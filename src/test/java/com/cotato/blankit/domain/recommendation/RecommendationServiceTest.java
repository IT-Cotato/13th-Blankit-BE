package com.cotato.blankit.domain.recommendation;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.recommendation.dto.response.AllRecommendationResponse;
import com.cotato.blankit.domain.recommendation.dto.response.RecommendedTaskItem;
import com.cotato.blankit.domain.recommendation.dto.response.TodayRecommendationResponse;
import com.cotato.blankit.domain.recommendation.service.RecommendationService;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskPriority;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:recommendation-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class RecommendationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired private RecommendationService recommendationService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TaskRepository taskRepository;
    @PersistenceContext private EntityManager entityManager;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create(SocialProvider.KAKAO, "rec-user", "rec@example.com", "추천유저", null, 120));
        category = categoryRepository.save(Category.create(user, "학업", "#FF5C5C", "book", 0, true));
    }

    private Task task(String title, LocalDate deadline, int estimatedTime, Integer progressRate, boolean starred) {
        Task t = Task.create(user, category, title, deadline, null, estimatedTime);
        if (progressRate != null) t.updateProgressRate(progressRate);
        if (starred) t.updateStarred(true);
        return taskRepository.save(t);
    }

    private Task taskNoEst(String title, LocalDate deadline, Integer progressRate) {
        Task t = Task.create(user, category, title, deadline, null);
        if (progressRate != null) t.updateProgressRate(progressRate);
        return taskRepository.save(t);
    }

    private Task taskDone(String title, LocalDate deadline, int estimatedTime) {
        Task t = Task.create(user, category, title, deadline, null, estimatedTime);
        t.updateStatus(TaskStatus.DONE);
        return taskRepository.save(t);
    }

    @Test
    @DisplayName("30분 Pack은 분당 남은 진행률이 높은 과업 3개와 공백 동안 예상 상승률을 반환한다")
    void getThirtyMinutePackRecommendationRanksByProgressPerMinute() {
        Task fastest = task("빠름", TODAY.plusDays(1), 10, 50, false);   // 5%/분, 최대 50%
        Task second = task("중간", TODAY.plusDays(1), 30, 10, false);   // 3%/분
        Task third = task("느림", TODAY.plusDays(1), 100, 0, false);    // 1%/분
        task("제외", TODAY.plusDays(1), 200, 0, false);

        var response = recommendationService.getThirtyMinutePackRecommendation(user.getId(), 30);

        assertThat(response.availableMinutes()).isEqualTo(30);
        assertThat(response.tasks()).extracting(item -> item.taskId())
                .containsExactly(fastest.getId(), second.getId(), third.getId());
        assertThat(response.tasks().get(0).progressPerMinute()).isEqualByComparingTo("5.0000");
        assertThat(response.tasks().get(0).expectedProgressIncrease()).isEqualByComparingTo("50.00");
        assertThat(response.tasks().get(1).expectedProgressIncrease()).isEqualByComparingTo("90.00");
    }

    // ─── 빈 컬렉션 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("활성 과업이 없으면 권장 시간 0분, 추천 과업 빈 리스트를 반환한다")
    void getTodayRecommendation_noTasks_returnsEmpty() {
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then
        assertThat(result.recommendedDate()).isEqualTo(TODAY);
        assertThat(result.totalRecommendedMinutes()).isZero();
        assertThat(result.topTasks()).isEmpty();
    }

    // ─── 점수 계산 + 정렬 ────────────────────────────────────────────────────

    @Test
    @DisplayName("점수가 낮은 과업이 먼저 추천된다 (긴급도×0.8 + 진행부족×0.2)")
    void getTodayRecommendation_scoreOrdering_mostUrgentAndLeastProgressFirst() {
        // given
        // task1: urgency=1(+1일), progress rank=2(50%) → score=1×0.8+2×0.2=1.20
        // task2: urgency=2(+3일), progress rank=1(0%)  → score=2×0.8+1×0.2=1.80
        // task3: urgency=3(+5일), progress rank=3(80%) → score=3×0.8+3×0.2=3.00
        Task task1 = task("급함",  TODAY.plusDays(1), 60, 50, false);
        Task task2 = task("미진행", TODAY.plusDays(3), 60,  0, false);
        Task task3 = task("여유",  TODAY.plusDays(5), 60, 80, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then
        assertThat(result.topTasks())
                .extracting(RecommendedTaskItem::taskId)
                .containsExactly(task1.getId(), task2.getId(), task3.getId());
        assertThat(result.topTasks().get(0).score()).isEqualByComparingTo(new BigDecimal("1.20"));
        assertThat(result.topTasks().get(1).score()).isEqualByComparingTo(new BigDecimal("1.80"));
        assertThat(result.topTasks().get(2).score()).isEqualByComparingTo(new BigDecimal("3.00"));
    }

    // ─── rank map 동점 처리 (같은 값 → 같은 rank) ──────────────────────────

    @Test
    @DisplayName("긴급도(마감일+starred)가 같으면 두 과업이 동일한 긴급도 rank를 받아 최종 순서는 진행률로 결정된다")
    void rankTasks_urgencyTie_sameUrgencyRank_orderedByProgress() {
        // taskA: urgency rank=1(동점), progress=50% → progress rank=2 → score=1×0.8+2×0.2=1.20
        // taskB: urgency rank=1(동점), progress=0%  → progress rank=1 → score=1×0.8+1×0.2=1.00
        Task taskA = task("먼저 생성", TODAY.plusDays(3), 60, 50, false);
        Task taskB = task("나중 생성", TODAY.plusDays(3), 60,  0, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then: 긴급도 rank 동점 → 진행률이 낮은 taskB가 먼저
        assertThat(result.topTasks().get(0).taskId()).isEqualTo(taskB.getId());
        assertThat(result.topTasks().get(0).score()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(result.topTasks().get(1).taskId()).isEqualTo(taskA.getId());
        assertThat(result.topTasks().get(1).score()).isEqualByComparingTo(new BigDecimal("1.20"));
    }

    @Test
    @DisplayName("마감일·starred·진행률이 모두 같으면 두 rank map에서 동일한 rank를 받아 최종 점수가 같고 먼저 생성된 과업이 먼저 추천된다")
    void rankTasks_allFieldsTie_sameScoreAndCreatedAtBreaksTie() {
        // taskA(먼저 생성): urgency rank=1, progress rank=1 → score=1×0.8+1×0.2=1.00
        // taskB(나중 생성): urgency rank=1, progress rank=1 → score=1×0.8+1×0.2=1.00
        // 최종 동점 → createdAt으로 타이브레이킹
        Task taskA = task("먼저 생성", TODAY.plusDays(3), 60, 50, false);
        Task taskB = task("나중 생성", TODAY.plusDays(3), 60, 50, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then: 두 과업 모두 score=1.00, 먼저 생성된 taskA가 먼저
        assertThat(result.topTasks().get(0).taskId()).isEqualTo(taskA.getId());
        assertThat(result.topTasks().get(0).score()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(result.topTasks().get(1).taskId()).isEqualTo(taskB.getId());
        assertThat(result.topTasks().get(1).score()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("같은 긴급도를 가진 과업이 3개이면 모두 동일한 긴급도 rank를 받아 점수 차이는 진행률 차이(×0.2)만 반영된다")
    void rankTasks_threeTasksWithSameUrgency_scoreDiffOnlyByProgress() {
        // 세 과업 모두 deadline=+3일, starred=false → urgency rank 모두 1
        // progress: 0%→rank=1, 50%→rank=2, 80%→rank=3
        // scores: 1×0.8+1×0.2=1.00 / 1×0.8+2×0.2=1.20 / 1×0.8+3×0.2=1.40
        Task task0  = task("0%",  TODAY.plusDays(3), 60,  0, false);
        Task task50 = task("50%", TODAY.plusDays(3), 60, 50, false);
        Task task80 = task("80%", TODAY.plusDays(3), 60, 80, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then: score 차이가 0.20씩 (진행률 rank 차이만 반영됨을 증명)
        assertThat(result.topTasks())
                .extracting(RecommendedTaskItem::taskId)
                .containsExactly(task0.getId(), task50.getId(), task80.getId());
        assertThat(result.topTasks().get(0).score()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(result.topTasks().get(1).score()).isEqualByComparingTo(new BigDecimal("1.20"));
        assertThat(result.topTasks().get(2).score()).isEqualByComparingTo(new BigDecimal("1.40"));
    }

    @Test
    @DisplayName("같은 진행률을 가진 과업이 3개이면 모두 동일한 진행률 rank를 받아 점수 차이는 긴급도 차이(×0.8)만 반영된다")
    void rankTasks_threeTasksWithSameProgress_scoreDiffOnlyByUrgency() {
        // 세 과업 모두 progress=50%, starred=false → progress rank 모두 1
        // deadline: +1일→urgency rank=1, +3일→urgency rank=2, +5일→urgency rank=3
        // scores: 1×0.8+1×0.2=1.00 / 2×0.8+1×0.2=1.80 / 3×0.8+1×0.2=2.60
        Task task1 = task("+1일", TODAY.plusDays(1), 60, 50, false);
        Task task3 = task("+3일", TODAY.plusDays(3), 60, 50, false);
        Task task5 = task("+5일", TODAY.plusDays(5), 60, 50, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then: score 차이가 0.80씩 (긴급도 rank 차이만 반영됨을 증명)
        assertThat(result.topTasks())
                .extracting(RecommendedTaskItem::taskId)
                .containsExactly(task1.getId(), task3.getId(), task5.getId());
        assertThat(result.topTasks().get(0).score()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(result.topTasks().get(1).score()).isEqualByComparingTo(new BigDecimal("1.80"));
        assertThat(result.topTasks().get(2).score()).isEqualByComparingTo(new BigDecimal("2.60"));
    }

    // ─── starred 타이브레이킹 ────────────────────────────────────────────────

    @Test
    @DisplayName("마감일이 같으면 starred 과업이 긴급도 순위에서 앞선다")
    void getTodayRecommendation_sameDeadline_starredRanksFirst() {
        // given: 두 과업 모두 deadline=+3일
        // taskA: starred=true → urgency=1, progress rank=2(30%) → score=1×0.8+2×0.2=1.20
        // taskB: starred=false → urgency=2, progress rank=1(0%) → score=2×0.8+1×0.2=1.80
        Task taskA = task("별표", TODAY.plusDays(3), 60, 30, true);
        Task taskB = task("일반", TODAY.plusDays(3), 60,  0, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then
        assertThat(result.topTasks().get(0).taskId()).isEqualTo(taskA.getId());
    }

    // ─── null progressRate = 0% ───────────────────────────────────────────────

    @Test
    @DisplayName("progressRate가 null이면 0%로 처리되어 진행 부족 순위 1위를 얻는다")
    void getTodayRecommendation_nullProgressRate_scoredAsZeroPercent() {
        // given
        // taskNull: urgency=1(+1일), progress null→0%(progress rank=1) → score=1×0.8+1×0.2=1.00
        // task50:   urgency=2(+3일), progress=50%(progress rank=2)      → score=2×0.8+2×0.2=2.00
        // null이 100%로 잘못 처리됐다면 taskNull의 progress rank=2 → score=1.20 ≠ 1.00
        task("null진행률", TODAY.plusDays(1), 60, null, false);
        task("50%완료",   TODAY.plusDays(3), 60, 50,   false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then
        assertThat(result.topTasks().get(0).score()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    // ─── 우선순위 배분 (과업 수별) ───────────────────────────────────────────

    @Test
    @DisplayName("과업 1개이면 HIGH 하나만 반환된다")
    void getTodayRecommendation_oneTask_highOnly() {
        // given
        task("단독", TODAY.plusDays(5), 60, null, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then
        assertThat(result.topTasks()).hasSize(1);
        assertThat(result.topTasks().get(0).priority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    @DisplayName("과업 2개이면 1위=HIGH, 2위=MEDIUM으로 배분되고 DB에도 반영된다")
    void getTodayRecommendation_twoTasks_highAndMedium() {
        // given: task1이 urgency·progress 모두 1위가 되도록 구성
        // task1: urgency=1(+1일), progress rank=1(0%) → score=1.00 → HIGH
        // task2: urgency=2(+5일), progress rank=2(80%) → score=2.00 → MEDIUM
        Task task1 = task("1위", TODAY.plusDays(1), 60,  0, false);
        Task task2 = task("2위", TODAY.plusDays(5), 60, 80, false);
        // when
        recommendationService.getTodayRecommendation(user.getId());
        // then: DB 반영 확인
        entityManager.flush();
        entityManager.clear();
        assertThat(taskRepository.findById(task1.getId()).orElseThrow().getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(taskRepository.findById(task2.getId()).orElseThrow().getPriority()).isEqualTo(TaskPriority.MEDIUM);
    }

    @Test
    @DisplayName("과업 3개이면 1위=HIGH, 2위=MEDIUM, 3위=LOW로 배분된다 (round: highEnd=1, mediumEnd=2)")
    void getTodayRecommendation_threeTasks_highMediumLow() {
        // given: n=3 → highEnd=round(3×0.3)=1, mediumEnd=round(3×0.7)=2
        task("1위", TODAY.plusDays(1), 60,  0, false);
        task("2위", TODAY.plusDays(3), 60, 50, false);
        task("3위", TODAY.plusDays(5), 60, 80, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then
        assertThat(result.topTasks().get(0).priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(result.topTasks().get(1).priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(result.topTasks().get(2).priority()).isEqualTo(TaskPriority.LOW);
    }

    // ─── getAllRecommendation ─────────────────────────────────────────────────

    @Test
    @DisplayName("활성 과업이 없으면 전체 보기도 빈 리스트를 반환한다")
    void getAllRecommendation_noTasks_returnsEmpty() {
        // when
        AllRecommendationResponse result = recommendationService.getAllRecommendation(user.getId());
        // then
        assertThat(result.recommendedDate()).isEqualTo(TODAY);
        assertThat(result.tasks()).isEmpty();
    }

    @Test
    @DisplayName("활성 과업이 4개 이상이어도 전체 보기는 모든 과업을 반환하고 rankOrder가 1부터 연속으로 부여된다")
    void getAllRecommendation_moreThanThreeTasks_returnsAll() {
        // given
        for (int i = 1; i <= 5; i++) {
            task("과업" + i, TODAY.plusDays(i), 60, i * 10, false);
        }
        // when
        AllRecommendationResponse result = recommendationService.getAllRecommendation(user.getId());
        // then
        assertThat(result.tasks()).hasSize(5);
        assertThat(result.tasks()).extracting(RecommendedTaskItem::rankOrder)
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("과업 4개일 때 4번째 과업도 LOW 우선순위를 받는다 (n=4: highEnd=1, mediumEnd=3)")
    void getAllRecommendation_fourTasks_priorityAssignedToAll() {
        // given: n=4 → highEnd=round(4×0.3)=1, mediumEnd=round(4×0.7)=3
        // rank1→HIGH, rank2→MEDIUM, rank3→MEDIUM, rank4→LOW
        task("1위", TODAY.plusDays(1), 60,  0, false);
        task("2위", TODAY.plusDays(2), 60, 20, false);
        task("3위", TODAY.plusDays(3), 60, 40, false);
        task("4위", TODAY.plusDays(5), 60, 80, false);
        // when
        AllRecommendationResponse result = recommendationService.getAllRecommendation(user.getId());
        // then
        assertThat(result.tasks()).hasSize(4);
        assertThat(result.tasks().get(0).priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(result.tasks().get(1).priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(result.tasks().get(2).priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(result.tasks().get(3).priority()).isEqualTo(TaskPriority.LOW);
    }

    // ─── 마감이 지난 과업 / 완료된 과업 제외 ────────────────────────────────────

    @Test
    @DisplayName("마감이 지난 과업은 우선순위 산정과 권장 시간 계산 모두에서 제외된다")
    void getTodayRecommendation_expiredTask_excludedCompletely() {
        // given
        task("마감지남", TODAY.minusDays(1), 120, null, false);
        Task active = task("활성", TODAY.plusDays(3), 90, null, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then
        assertThat(result.topTasks())
                .hasSize(1)
                .extracting(RecommendedTaskItem::taskId)
                .containsExactly(active.getId());
        assertThat(result.totalRecommendedMinutes()).isEqualTo(30L);
    }

    @Test
    @DisplayName("완료된(DONE) 과업은 우선순위 산정과 권장 시간 계산 모두에서 제외된다")
    void getTodayRecommendation_doneTask_excludedCompletely() {
        // given
        taskDone("완료됨", TODAY.plusDays(2), 60);
        Task active = task("활성", TODAY.plusDays(3), 90, null, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then
        assertThat(result.topTasks())
                .hasSize(1)
                .extracting(RecommendedTaskItem::taskId)
                .containsExactly(active.getId());
        assertThat(result.totalRecommendedMinutes()).isEqualTo(30L);
    }

    // ─── 오늘 마감 과업 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("오늘 마감 과업은 우선순위 산정에는 포함되고 권장 시간 계산에서는 제외되며 recommendedMinutes가 null이다")
    void getTodayRecommendation_todayDeadline_priorityAssignedButExcludedFromTimeCalc() {
        // given
        // todayTask: urgency rank=1(0일), progress rank=1(null→0%, 동점) → score=1.00
        // futureTask: urgency rank=2(+3일), progress rank=1(null→0%, 동점) → score=1.80
        Task todayTask  = task("오늘마감", TODAY,             120, null, false);
        Task futureTask = task("미래마감", TODAY.plusDays(3),  90, null, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then: todayTask도 topTasks에 포함되지만 recommendedMinutes=null
        assertThat(result.topTasks()).hasSize(2);
        assertThat(result.topTasks().get(0).taskId()).isEqualTo(todayTask.getId());
        assertThat(result.topTasks().get(0).recommendedMinutes()).isNull();
        assertThat(result.topTasks().get(1).taskId()).isEqualTo(futureTask.getId());
        assertThat(result.topTasks().get(1).recommendedMinutes()).isEqualTo(30);
        // totalMinutes: todayTask 제외, futureTask만 포함 → round(90/3)=30
        assertThat(result.totalRecommendedMinutes()).isEqualTo(30L);
    }

    // ─── estimatedTime 없는 과업 ──────────────────────────────────────────────

    @Test
    @DisplayName("예상 시간이 없는 과업은 우선순위 산정에는 포함되고 권장 시간 계산에서는 제외되며 recommendedMinutes가 null이다")
    void getTodayRecommendation_noEstimatedTime_priorityAssignedButExcludedFromTimeCalc() {
        // given
        // noEstTask: urgency rank=1(+2일), progress rank=1(null→0%, 동점) → score=1.00
        // normalTask: urgency rank=2(+4일), progress rank=1(null→0%, 동점) → score=1.80
        Task noEstTask  = taskNoEst("예상시간없음", TODAY.plusDays(2), null);
        Task normalTask = task("일반",           TODAY.plusDays(4), 120, null, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then: noEstTask도 topTasks에 포함되지만 recommendedMinutes=null
        assertThat(result.topTasks()).hasSize(2);
        assertThat(result.topTasks().get(0).taskId()).isEqualTo(noEstTask.getId());
        assertThat(result.topTasks().get(0).recommendedMinutes()).isNull();
        assertThat(result.topTasks().get(1).taskId()).isEqualTo(normalTask.getId());
        assertThat(result.topTasks().get(1).recommendedMinutes()).isEqualTo(30);
        // totalMinutes: noEstTask 제외, normalTask만 포함 → round(120/4)=30
        assertThat(result.totalRecommendedMinutes()).isEqualTo(30L);
    }

    // ─── 조합 케이스 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("마감된·완료·오늘마감·예상시간없음·일반 과업이 섞였을 때 우선순위와 권장 시간 대상이 올바르게 분리된다")
    void getTodayRecommendation_mixedCases_correctSeparation() {
        // given
        task("마감지남",    TODAY.minusDays(1), 60, null, false); // ranked 제외
        taskDone("완료됨", TODAY.plusDays(1),  60);               // ranked 제외
        // ranked 포함: todayTask(rank1) → noEstTask(rank2) → normalTask(rank3)
        Task todayTask  = task("오늘마감",    TODAY,             60, null, false);
        Task noEstTask  = taskNoEst("예상없음", TODAY.plusDays(2), null);
        Task normalTask = task("일반",       TODAY.plusDays(3), 90, null, false);
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then
        assertThat(result.topTasks())
                .hasSize(3)
                .extracting(RecommendedTaskItem::taskId)
                .containsExactly(todayTask.getId(), noEstTask.getId(), normalTask.getId());
        assertThat(result.topTasks().get(0).priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(result.topTasks().get(1).priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(result.topTasks().get(2).priority()).isEqualTo(TaskPriority.LOW);
        assertThat(result.topTasks().get(0).recommendedMinutes()).isNull();
        assertThat(result.topTasks().get(1).recommendedMinutes()).isNull();
        assertThat(result.topTasks().get(2).recommendedMinutes()).isEqualTo(30);
        // totalMinutes: normalTask만 포함 → round(90/3)=30
        assertThat(result.totalRecommendedMinutes()).isEqualTo(30L);
    }

    @Test
    @DisplayName("전체 조회에서도 마감된·완료 과업은 제외되고 오늘마감·예상시간없음 과업은 포함된다")
    void getAllRecommendation_mixedCases_correctSeparation() {
        // given
        task("마감지남",    TODAY.minusDays(1), 60, null, false);
        taskDone("완료됨", TODAY.plusDays(1),  60);
        Task todayTask  = task("오늘마감",    TODAY,             60, null, false);
        Task noEstTask  = taskNoEst("예상없음", TODAY.plusDays(2), null);
        Task normalTask = task("일반",       TODAY.plusDays(3), 90, null, false);
        // when
        AllRecommendationResponse result = recommendationService.getAllRecommendation(user.getId());
        // then
        assertThat(result.tasks())
                .hasSize(3)
                .extracting(RecommendedTaskItem::taskId)
                .containsExactly(todayTask.getId(), noEstTask.getId(), normalTask.getId());
        assertThat(result.tasks().get(0).recommendedMinutes()).isNull();
        assertThat(result.tasks().get(1).recommendedMinutes()).isNull();
        assertThat(result.tasks().get(2).recommendedMinutes()).isEqualTo(30);
    }

    // ─── topTasks 3개 컷 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("활성 과업이 4개 이상이어도 topTasks는 rankOrder 1·2·3만 반환된다")
    void getTodayRecommendation_moreThanThreeTasks_capsAtThree() {
        // given
        for (int i = 1; i <= 5; i++) {
            task("과업" + i, TODAY.plusDays(i), 60, i * 10, false);
        }
        // when
        TodayRecommendationResponse result = recommendationService.getTodayRecommendation(user.getId());
        // then
        assertThat(result.topTasks()).hasSize(3);
        assertThat(result.topTasks().get(0).rankOrder()).isEqualTo(1);
        assertThat(result.topTasks().get(1).rankOrder()).isEqualTo(2);
        assertThat(result.topTasks().get(2).rankOrder()).isEqualTo(3);
    }
}

package com.cotato.blankit.domain.recommendation;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.recommendation.dto.response.AllRecommendationResponse;
import com.cotato.blankit.domain.recommendation.dto.response.RecommendationModesResponse;
import com.cotato.blankit.domain.recommendation.dto.response.RecommendedTaskItem;
import com.cotato.blankit.domain.recommendation.dto.response.TodayRecommendationResponse;
import com.cotato.blankit.domain.recommendation.entity.DailyRecommendation;
import com.cotato.blankit.domain.recommendation.entity.DailyRecommendationItem;
import com.cotato.blankit.domain.recommendation.repository.DailyRecommendationItemRepository;
import com.cotato.blankit.domain.recommendation.repository.DailyRecommendationRepository;
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
import java.util.List;
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
    @Autowired private DailyRecommendationRepository dailyRecommendationRepository;
    @Autowired private DailyRecommendationItemRepository dailyRecommendationItemRepository;
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
        assertThat(response.tasks()).allSatisfy(item -> {
            assertThat(item.categoryName()).isEqualTo("학업");
            assertThat(item.memo()).isNull();
        });
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

    // ─── getRecommendationModes ───────────────────────────────────────────────

    @Test
    @DisplayName("활성 과업이 없으면 FIRE·BALANCE·TASTE·CLEAR 4개 모드가 반환되고 각 tasks는 비어있다")
    void getRecommendationModes_noTasks_allModesEmpty() {
        RecommendationModesResponse result = recommendationService.getRecommendationModes(user.getId());
        assertThat(result.modes()).extracting(RecommendationModesResponse.RecommendationModeItem::mode)
                .containsExactly("FIRE", "BALANCE", "TASTE", "CLEAR");
        result.modes().forEach(m -> assertThat(m.tasks()).isEmpty());
    }

    // ─ FIRE ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FIRE — HIGH 과업 1개의 estimatedTime이 totalMinutes 이상이면 해당 과업 1개만 recommendedMinutes=totalMinutes로 반환된다")
    void getRecommendationModes_fire_singleHighFillsBudget() {
        // n=1(HIGH), deadline=+2일, est=120 → totalMinutes=round(120/2)=60
        // min(120, 60)=60 → recommendedMinutes=60
        Task highTask = task("HIGH", TODAY.plusDays(2), 120, 0, false);

        RecommendationModesResponse.RecommendationModeItem fire =
                recommendationService.getRecommendationModes(user.getId()).modes().get(0);

        assertThat(fire.mode()).isEqualTo("FIRE");
        assertThat(fire.tasks()).hasSize(1);
        assertThat(fire.tasks().get(0).taskId()).isEqualTo(highTask.getId());
        assertThat(fire.tasks().get(0).recommendedMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("FIRE — 1등 HIGH의 estimatedTime이 totalMinutes 미만이면 2등 HIGH도 greedy fill로 포함된다")
    void getRecommendationModes_fire_twoHighGreedyFill() {
        // n=7: highEnd=round(7×0.3)=2 → rank1·2=HIGH
        // 점수가 모두 다르도록 deadline·progress를 1씩 증가
        // totalMinutes=round(30/1+60/2+60/3+60/4+60/5+60/6+60/7)=round(125.57)=126
        // high1.est=30 < 126 → add(30), remaining=96
        // high2.est=60 < 96  → add(min(60,96)=60), remaining=36; HIGH 소진 → stop
        Task high1 = task("HIGH1", TODAY.plusDays(1), 30,  0, false);
        Task high2 = task("HIGH2", TODAY.plusDays(2), 60, 10, false);
        task("MED1", TODAY.plusDays(3), 60, 20, false);
        task("MED2", TODAY.plusDays(4), 60, 30, false);
        task("MED3", TODAY.plusDays(5), 60, 40, false);
        task("LOW1", TODAY.plusDays(6), 60, 50, false);
        task("LOW2", TODAY.plusDays(7), 60, 60, false);

        RecommendationModesResponse.RecommendationModeItem fire =
                recommendationService.getRecommendationModes(user.getId()).modes().get(0);

        assertThat(fire.tasks()).hasSize(2);
        assertThat(fire.tasks().get(0).taskId()).isEqualTo(high1.getId());
        assertThat(fire.tasks().get(0).recommendedMinutes()).isEqualTo(30);
        assertThat(fire.tasks().get(1).taskId()).isEqualTo(high2.getId());
        assertThat(fire.tasks().get(1).recommendedMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("FIRE — estimatedTime=0인 HIGH 과업은 제외된다")
    void getRecommendationModes_fire_highWithZeroEst_excluded() {
        // n=3: HIGH(est=0, rank1), MEDIUM(est=60, rank2), LOW(est=60, rank3)
        // FIRE: est>0 필터 → HIGH 제외 → 결과 비어있음
        task("HIGH(zero)", TODAY.plusDays(1),  0, 0,  false);
        task("MED",        TODAY.plusDays(2), 60, 20, false);
        task("LOW",        TODAY.plusDays(3), 60, 40, false);

        RecommendationModesResponse.RecommendationModeItem fire =
                recommendationService.getRecommendationModes(user.getId()).modes().get(0);

        assertThat(fire.tasks()).isEmpty();
    }

    @Test
    @DisplayName("FIRE — estimatedTime이 null인 HIGH 과업은 제외된다")
    void getRecommendationModes_fire_highWithNullEst_excluded() {
        // n=3: rank1=HIGH(null est), rank2=MEDIUM, rank3=LOW
        taskNoEst("HIGH(null)", TODAY.plusDays(1), 0);
        task("MED", TODAY.plusDays(2), 60, 20, false);
        task("LOW", TODAY.plusDays(3), 60, 40, false);

        RecommendationModesResponse.RecommendationModeItem fire =
                recommendationService.getRecommendationModes(user.getId()).modes().get(0);

        assertThat(fire.tasks()).isEmpty();
    }

    // ─ BALANCE ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BALANCE — estimatedTime이 가장 작은 MEDIUM/LOW 과업 + HIGH 1등 과업 조합")
    void getRecommendationModes_balance_quickNonHighPlusTopHigh() {
        // n=3: rank1=HIGH, rank2=MEDIUM(est=90), rank3=LOW(est=30)
        // BALANCE: LOW가 est 최소 → [LOW(30), HIGH(120)]
        Task highTask = task("HIGH", TODAY.plusDays(1), 120,  0, false);
        Task medTask  = task("MED",  TODAY.plusDays(2),  90, 20, false);
        Task lowTask  = task("LOW",  TODAY.plusDays(3),  30, 40, false);

        RecommendationModesResponse.RecommendationModeItem balance =
                recommendationService.getRecommendationModes(user.getId()).modes().get(1);

        assertThat(balance.mode()).isEqualTo("BALANCE");
        assertThat(balance.tasks()).hasSize(2);
        assertThat(balance.tasks().get(0).taskId()).isEqualTo(lowTask.getId());
        assertThat(balance.tasks().get(0).recommendedMinutes()).isEqualTo(30);
        assertThat(balance.tasks().get(1).taskId()).isEqualTo(highTask.getId());
        assertThat(balance.tasks().get(1).recommendedMinutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("BALANCE — MEDIUM/LOW 과업 모두 estimatedTime이 없으면 빈 조합을 반환한다")
    void getRecommendationModes_balance_noNonHighWithEst_returnsEmpty() {
        // n=3: HIGH(est 있음), MEDIUM(null), LOW(null)
        // quickTask: est>0 필터 → MEDIUM·LOW 제외 → null → 빈 조합 반환
        task("HIGH", TODAY.plusDays(1), 60,  0, false);
        taskNoEst("MED", TODAY.plusDays(2), 20);
        taskNoEst("LOW", TODAY.plusDays(3), 40);

        RecommendationModesResponse.RecommendationModeItem balance =
                recommendationService.getRecommendationModes(user.getId()).modes().get(1);

        assertThat(balance.tasks()).isEmpty();
    }

    @Test
    @DisplayName("BALANCE — estimatedTime=0인 MEDIUM/LOW 과업은 quickTask 후보에서 제외된다")
    void getRecommendationModes_balance_zeroEstNonHigh_notPickedAsQuick() {
        // n=3: HIGH(est=60, rank1), MEDIUM(est=0, rank2), LOW(est=30, rank3)
        // quickTask: est>0 필터 → MEDIUM(0) 제외 → LOW(30)이 선택됨
        Task highTask = task("HIGH", TODAY.plusDays(1), 60,  0, false);
        task("MED",          TODAY.plusDays(2),  0,  20, false);
        Task lowTask  = task("LOW",  TODAY.plusDays(3), 30, 40, false);

        RecommendationModesResponse.RecommendationModeItem balance =
                recommendationService.getRecommendationModes(user.getId()).modes().get(1);

        assertThat(balance.tasks()).hasSize(2);
        assertThat(balance.tasks().get(0).taskId()).isEqualTo(lowTask.getId());
        assertThat(balance.tasks().get(0).recommendedMinutes()).isEqualTo(30);
        assertThat(balance.tasks().get(1).taskId()).isEqualTo(highTask.getId());
        assertThat(balance.tasks().get(1).recommendedMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("BALANCE — HIGH 과업의 estimatedTime=0이면 빈 조합을 반환한다")
    void getRecommendationModes_balance_highWithZeroEst_returnsEmpty() {
        // n=3: HIGH(est=0, rank1), MEDIUM(est=60, rank2), LOW(est=30, rank3)
        // highTask: est>0 필터 → HIGH(0) 제외 → null → 빈 조합 반환
        task("HIGH(zero)", TODAY.plusDays(1),  0,  0, false);
        task("MED",        TODAY.plusDays(2), 60, 20, false);
        task("LOW",        TODAY.plusDays(3), 30, 40, false);

        RecommendationModesResponse.RecommendationModeItem balance =
                recommendationService.getRecommendationModes(user.getId()).modes().get(1);

        assertThat(balance.tasks()).isEmpty();
    }

    // ─ TASTE ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TASTE — HIGH·MEDIUM·LOW 각 1등 과업이 순서대로 반환된다")
    void getRecommendationModes_taste_oneFromEachPriority() {
        // n=3: rank1=HIGH, rank2=MEDIUM, rank3=LOW
        Task highTask = task("HIGH", TODAY.plusDays(1), 60,  0, false);
        Task medTask  = task("MED",  TODAY.plusDays(2), 60, 20, false);
        Task lowTask  = task("LOW",  TODAY.plusDays(3), 60, 40, false);

        RecommendationModesResponse.RecommendationModeItem taste =
                recommendationService.getRecommendationModes(user.getId()).modes().get(2);

        assertThat(taste.mode()).isEqualTo("TASTE");
        assertThat(taste.tasks()).hasSize(3);
        assertThat(taste.tasks().get(0).taskId()).isEqualTo(highTask.getId());
        assertThat(taste.tasks().get(1).taskId()).isEqualTo(medTask.getId());
        assertThat(taste.tasks().get(2).taskId()).isEqualTo(lowTask.getId());
    }

    @Test
    @DisplayName("TASTE — 우선순위 2개가 존재하면(1개 없음) 있는 과업 2개를 반환한다")
    void getRecommendationModes_taste_twoPrioritiesPresent_returnsBoth() {
        // n=2: rank1=HIGH, rank2=MEDIUM (LOW 없음) → present=2 → HIGH·MEDIUM 반환
        Task highTask = task("HIGH", TODAY.plusDays(1), 60,  0, false);
        Task medTask  = task("MED",  TODAY.plusDays(2), 60, 50, false);

        RecommendationModesResponse.RecommendationModeItem taste =
                recommendationService.getRecommendationModes(user.getId()).modes().get(2);

        assertThat(taste.tasks()).hasSize(2);
        assertThat(taste.tasks().get(0).taskId()).isEqualTo(highTask.getId());
        assertThat(taste.tasks().get(1).taskId()).isEqualTo(medTask.getId());
    }

    @Test
    @DisplayName("TASTE — 우선순위 1개만 존재하면 빈 조합을 반환한다")
    void getRecommendationModes_taste_onlyOnePriority_returnsEmpty() {
        // n=1: HIGH만 존재 → present=1 → 빈 조합 반환
        task("HIGH", TODAY.plusDays(1), 60, 0, false);

        RecommendationModesResponse.RecommendationModeItem taste =
                recommendationService.getRecommendationModes(user.getId()).modes().get(2);

        assertThat(taste.tasks()).isEmpty();
    }

    @Test
    @DisplayName("TASTE — estimatedTime=0인 과업은 우선순위 대표 과업 선택에서 제외된다")
    void getRecommendationModes_taste_zeroEstTask_excluded() {
        // n=3: HIGH(est=0, rank1), MEDIUM(est=60, rank2), LOW(est=90, rank3)
        // firstByPriority: est>0 필터 → HIGH(0) 제외 → HIGH 슬롯 없음
        task("HIGH(zero)", TODAY.plusDays(1),  0,  0, false);
        Task medTask = task("MED", TODAY.plusDays(2), 60, 20, false);
        Task lowTask = task("LOW", TODAY.plusDays(3), 90, 40, false);

        RecommendationModesResponse.RecommendationModeItem taste =
                recommendationService.getRecommendationModes(user.getId()).modes().get(2);

        assertThat(taste.tasks()).hasSize(2);
        assertThat(taste.tasks().get(0).taskId()).isEqualTo(medTask.getId());
        assertThat(taste.tasks().get(1).taskId()).isEqualTo(lowTask.getId());
    }

    // ─ CLEAR ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CLEAR — estimatedTime 오름차순으로 정렬하여 누적 합계가 totalMinutes를 초과하는 시점까지 과업을 추가한다")
    void getRecommendationModes_clear_sortedByEstimatedTimeAsc() {
        // urgency: taskSmall(+1일)=1, taskMid(+2일)=2, taskLarge(+3일)=3
        // progress: taskLarge(0%)=1, taskSmall(20%)=2, taskMid(40%)=3
        // scores: taskSmall=1.20(HIGH), taskMid=2.20(MEDIUM), taskLarge=2.60(LOW)
        // totalMinutes=round(30/1+60/2+180/3)=round(120)=120
        // CLEAR est 오름차순: [taskSmall(30), taskMid(60), taskLarge(180)]
        //   accumulated=30 < 120 → continue
        //   accumulated=90 < 120 → continue
        //   accumulated=270 >= 120 → add taskLarge and break
        Task taskLarge = task("큰예상",  TODAY.plusDays(3), 180,  0, false);
        Task taskSmall = task("작은예상", TODAY.plusDays(1),  30, 20, false);
        Task taskMid   = task("중간예상", TODAY.plusDays(2),  60, 40, false);

        RecommendationModesResponse.RecommendationModeItem clear =
                recommendationService.getRecommendationModes(user.getId()).modes().get(3);

        assertThat(clear.mode()).isEqualTo("CLEAR");
        assertThat(clear.tasks()).hasSize(3);
        assertThat(clear.tasks().get(0).taskId()).isEqualTo(taskSmall.getId());
        assertThat(clear.tasks().get(0).recommendedMinutes()).isEqualTo(30);
        assertThat(clear.tasks().get(1).taskId()).isEqualTo(taskMid.getId());
        assertThat(clear.tasks().get(1).recommendedMinutes()).isEqualTo(60);
        assertThat(clear.tasks().get(2).taskId()).isEqualTo(taskLarge.getId());
        assertThat(clear.tasks().get(2).recommendedMinutes()).isEqualTo(180);
    }

    @Test
    @DisplayName("CLEAR — 1등 과업의 estimatedTime이 totalMinutes 초과이면 해당 과업 1개를 estimatedTime 그대로 반환한다")
    void getRecommendationModes_clear_firstTaskExceedsBudget_returnsOnlyFirst() {
        // n=1(HIGH), est=300, deadline=+2일 → totalMinutes=round(300/2)=150
        // accumulated=300 >= 150 → 1개 추가 후 break, recommendedMinutes=300(est 그대로)
        Task bigTask = task("큰과업", TODAY.plusDays(2), 300, 0, false);

        RecommendationModesResponse.RecommendationModeItem clear =
                recommendationService.getRecommendationModes(user.getId()).modes().get(3);

        assertThat(clear.tasks()).hasSize(1);
        assertThat(clear.tasks().get(0).taskId()).isEqualTo(bigTask.getId());
        assertThat(clear.tasks().get(0).recommendedMinutes()).isEqualTo(300);
    }

    @Test
    @DisplayName("CLEAR — 오늘 마감 과업만 있으면 totalMinutes=0이므로 빈 목록을 반환한다")
    void getRecommendationModes_clear_onlyTodayDeadlineTasks_returnsEmpty() {
        // 오늘 마감 과업은 calculateTotalMinutes에서 days=0으로 제외 → totalMinutes=0
        // totalMinutes<=0 조기 반환 없으면 first.est>0 조건이 항상 참 → recommendedMinutes=0 항목 반환하는 버그
        task("오늘마감A", TODAY, 60, 0,  false);
        task("오늘마감B", TODAY, 30, 20, false);

        RecommendationModesResponse.RecommendationModeItem clear =
                recommendationService.getRecommendationModes(user.getId()).modes().get(3);

        assertThat(clear.tasks()).isEmpty();
    }

    @Test
    @DisplayName("CLEAR — estimatedTime=0인 과업은 시간 채우기 목록에서 제외된다")
    void getRecommendationModes_clear_zeroEstTask_excluded() {
        // n=3: HIGH(est=0, rank1), MEDIUM(est=30, rank2), LOW(est=60, rank3)
        // totalMinutes=round(0/1+30/2+60/3)=round(35)=35
        // byEstimated: est>0 필터 → [MEDIUM(30), LOW(60)]
        //   accumulated=30 < 35 → continue
        //   accumulated=90 >= 35 → add LOW and break
        task("HIGH(zero)", TODAY.plusDays(1),  0,  0, false);
        Task medTask = task("MED", TODAY.plusDays(2), 30, 20, false);
        Task lowTask = task("LOW", TODAY.plusDays(3), 60, 40, false);

        RecommendationModesResponse.RecommendationModeItem clear =
                recommendationService.getRecommendationModes(user.getId()).modes().get(3);

        assertThat(clear.tasks()).hasSize(2);
        assertThat(clear.tasks().get(0).taskId()).isEqualTo(medTask.getId());
        assertThat(clear.tasks().get(0).recommendedMinutes()).isEqualTo(30);
        assertThat(clear.tasks().get(1).taskId()).isEqualTo(lowTask.getId());
        assertThat(clear.tasks().get(1).recommendedMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("CLEAR — estimatedTime이 null인 과업은 제외된다")
    void getRecommendationModes_clear_nullEst_excluded() {
        // n=2: rank1=HIGH(null est), rank2=MEDIUM(est=60)
        // totalMinutes: HIGH(null) 제외 → round(60/2)=30
        // CLEAR byEstimated: [medTask(60)] (null 제외)
        // accumulated=60 >= 30 → 1개 추가 후 break, recommendedMinutes=60(est 그대로)
        taskNoEst("HIGH(null)", TODAY.plusDays(1), 0);
        Task medTask = task("MED", TODAY.plusDays(2), 60, 20, false);

        RecommendationModesResponse.RecommendationModeItem clear =
                recommendationService.getRecommendationModes(user.getId()).modes().get(3);

        assertThat(clear.tasks()).hasSize(1);
        assertThat(clear.tasks().get(0).taskId()).isEqualTo(medTask.getId());
        assertThat(clear.tasks().get(0).recommendedMinutes()).isEqualTo(60);
    }

    // ─── 캐싱 동작 — getTodayRecommendation ──────────────────────────────────

    @Test
    @DisplayName("getTodayRecommendation — 첫 호출 시 DailyRecommendation 헤더만 저장되고 DailyRecommendationItem은 저장되지 않는다")
    void getTodayRecommendation_firstCall_savesHeaderOnly() {
        // given
        task("1순위", TODAY.plusDays(1), 60,  0, false);
        task("2순위", TODAY.plusDays(2), 60, 20, false);
        task("3순위", TODAY.plusDays(3), 60, 40, false);

        // when
        recommendationService.getTodayRecommendation(user.getId());
        entityManager.flush();

        // then — 헤더 레코드 1개 존재
        assertThat(dailyRecommendationRepository
                .existsByUser_IdAndRecommendedDateAndMode(user.getId(), TODAY, "TODAY"))
                .isTrue();
        // then — 아이템 레코드는 저장하지 않음
        DailyRecommendation dr = dailyRecommendationRepository
                .findByUser_IdAndRecommendedDateAndMode(user.getId(), TODAY, "TODAY")
                .orElseThrow();
        assertThat(dailyRecommendationItemRepository
                .findAllByDailyRecommendationOrderByRankOrder(dr)).isEmpty();
    }

    @Test
    @DisplayName("getTodayRecommendation — 두 번째 호출 시 새 과업이 추가되면 실시간으로 반영된다")
    void getTodayRecommendation_secondCall_newTaskReflected() {
        // given
        task("1순위", TODAY.plusDays(2), 60,  0, false);
        task("2순위", TODAY.plusDays(3), 60, 20, false);
        task("3순위", TODAY.plusDays(4), 60, 40, false);

        recommendationService.getTodayRecommendation(user.getId());
        entityManager.flush();
        entityManager.clear();

        // 재계산 시 1순위가 될 과업 추가 (더 긴급한 마감)
        Task newTop = task("신규최우선", TODAY.plusDays(1), 60, 0, false);
        entityManager.flush();
        entityManager.clear();

        // when
        TodayRecommendationResponse second = recommendationService.getTodayRecommendation(user.getId());

        // then — 실시간 재계산이므로 새 과업이 1순위로 반영됨
        assertThat(second.topTasks().get(0).taskId()).isEqualTo(newTop.getId());
    }

    @Test
    @DisplayName("getTodayRecommendation — 두 번째 호출 시 과업이 추가되어도 totalRecommendedMinutes가 고정된다")
    void getTodayRecommendation_secondCall_totalMinutesFixed() {
        // given — 60분짜리 과업 1개, 2일 후 마감 → totalMinutes = round(60/2) = 30
        task("과업A", TODAY.plusDays(2), 60, 0, false);

        TodayRecommendationResponse first = recommendationService.getTodayRecommendation(user.getId());
        entityManager.flush();
        entityManager.clear();

        // 권장 시간에 영향을 줄 과업 추가 (재계산 시 총합 증가)
        task("과업B", TODAY.plusDays(1), 120, 0, false);
        entityManager.flush();
        entityManager.clear();

        // when
        TodayRecommendationResponse second = recommendationService.getTodayRecommendation(user.getId());

        // then — 첫 호출 시 계산된 권장 시간 그대로 유지
        assertThat(second.totalRecommendedMinutes()).isEqualTo(first.totalRecommendedMinutes());
    }

    @Test
    @DisplayName("getTodayRecommendation — 활성 과업이 없으면 빈 결과로 DailyRecommendation 헤더가 저장된다")
    void getTodayRecommendation_noTasks_savesEmptyCache() {
        // when
        recommendationService.getTodayRecommendation(user.getId());
        entityManager.flush();

        // then — 과업이 없어도 헤더는 캐시됨
        assertThat(dailyRecommendationRepository
                .existsByUser_IdAndRecommendedDateAndMode(user.getId(), TODAY, "TODAY"))
                .isTrue();
    }

    @Test
    @DisplayName("getTodayRecommendation — 첫 호출이 빈 결과여도 이후 과업이 추가되면 다음 호출에 실시간 반영된다")
    void getTodayRecommendation_noTasks_newTaskReflectedOnNextCall() {
        // given — 과업 없는 상태로 첫 호출 → totalRecommendedMinutes=0 캐시 저장
        recommendationService.getTodayRecommendation(user.getId());
        entityManager.flush();
        entityManager.clear();

        // 이후 과업 추가
        Task newTask = task("HIGH", TODAY.plusDays(1), 60, 0, false);
        entityManager.flush();
        entityManager.clear();

        // when — 당일 두 번째 호출
        TodayRecommendationResponse second = recommendationService.getTodayRecommendation(user.getId());

        // then — task 목록은 실시간 재계산되어 새 과업이 포함됨
        assertThat(second.topTasks()).hasSize(1);
        assertThat(second.topTasks().get(0).taskId()).isEqualTo(newTask.getId());
    }

    @Test
    @DisplayName("getTodayRecommendation — 추천 중이던 과업이 완료되면 다음 호출 시 제외되고 다음 순위 과업으로 채워진다")
    void getTodayRecommendation_completedTask_replacedByNextRanked() {
        // given — 과업 4개, top3: task1(+1d), task2(+2d), task3(+3d) / task4(+4d)는 4순위
        Task task1 = task("1순위", TODAY.plusDays(1), 60,  0, false);
        Task task2 = task("2순위", TODAY.plusDays(2), 60, 20, false);
        Task task3 = task("3순위", TODAY.plusDays(3), 60, 40, false);
        Task task4 = task("4순위", TODAY.plusDays(4), 60, 60, false);

        TodayRecommendationResponse first = recommendationService.getTodayRecommendation(user.getId());
        assertThat(first.topTasks()).extracting(RecommendedTaskItem::taskId)
                .containsExactly(task1.getId(), task2.getId(), task3.getId());

        entityManager.flush();
        entityManager.clear();

        // task1 완료 처리
        Task t1 = taskRepository.findById(task1.getId()).orElseThrow();
        t1.updateStatus(TaskStatus.DONE);
        entityManager.flush();
        entityManager.clear();

        // when — 두 번째 호출
        TodayRecommendationResponse second = recommendationService.getTodayRecommendation(user.getId());

        // then — task1 제외, task4가 빈자리를 채워 3개 유지
        assertThat(second.topTasks()).extracting(RecommendedTaskItem::taskId)
                .containsExactly(task2.getId(), task3.getId(), task4.getId());
    }

    @Test
    @DisplayName("getTodayRecommendation — 다음 순위 과업이 없으면 완료 과업 제외 후 남은 과업만 반환된다")
    void getTodayRecommendation_completedTask_noReplacement_returnsFewer() {
        // given — 과업 2개만 존재
        Task task1 = task("1순위", TODAY.plusDays(1), 60,  0, false);
        Task task2 = task("2순위", TODAY.plusDays(2), 60, 20, false);

        recommendationService.getTodayRecommendation(user.getId());
        entityManager.flush();
        entityManager.clear();

        // task1 완료 처리
        Task t1 = taskRepository.findById(task1.getId()).orElseThrow();
        t1.updateStatus(TaskStatus.DONE);
        entityManager.flush();
        entityManager.clear();

        // when
        TodayRecommendationResponse second = recommendationService.getTodayRecommendation(user.getId());

        // then — task1 제외, 대체할 과업 없으므로 1개만 반환
        assertThat(second.topTasks()).hasSize(1);
        assertThat(second.topTasks().get(0).taskId()).isEqualTo(task2.getId());
    }

    // ─── 캐싱 동작 — getRecommendationModes ──────────────────────────────────

    @Test
    @DisplayName("getRecommendationModes — 첫 호출 시 FIRE·BALANCE·TASTE·CLEAR 4개의 DailyRecommendation이 모두 저장된다")
    void getRecommendationModes_firstCall_savesAllFourModes() {
        // given
        task("HIGH", TODAY.plusDays(1), 60,  0, false);
        task("MED",  TODAY.plusDays(2), 90, 20, false);
        task("LOW",  TODAY.plusDays(3), 30, 40, false);

        // when
        recommendationService.getRecommendationModes(user.getId());
        entityManager.flush();

        // then — 4개 모드 레코드 전부 존재
        for (String mode : List.of("FIRE", "BALANCE", "TASTE", "CLEAR")) {
            assertThat(dailyRecommendationRepository
                    .existsByUser_IdAndRecommendedDateAndMode(user.getId(), TODAY, mode))
                    .as("mode=%s 캐시가 존재해야 한다", mode)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("getRecommendationModes — 결과가 빈 모드도 DailyRecommendation 레코드가 저장되어 캐시로 동작한다")
    void getRecommendationModes_emptyMode_savedAsEmptyRecord() {
        // given — 오늘 마감 과업만 존재 → modeRanked 필터에 걸려 모든 모드가 빈 결과
        task("오늘마감A", TODAY, 60, 0, false);
        task("오늘마감B", TODAY, 30, 0, false);

        // when
        recommendationService.getRecommendationModes(user.getId());
        entityManager.flush();

        // then — FIRE 레코드는 존재하지만 연결된 아이템은 0개
        DailyRecommendation fireRecord = dailyRecommendationRepository
                .findByUser_IdAndRecommendedDateAndMode(user.getId(), TODAY, "FIRE")
                .orElseThrow();
        assertThat(dailyRecommendationItemRepository
                .findAllByDailyRecommendationOrderByRankOrder(fireRecord)).isEmpty();
    }

    @Test
    @DisplayName("getRecommendationModes — 두 번째 호출 시 새 HIGH 과업이 추가되어도 FIRE 캐시 결과가 반환된다")
    void getRecommendationModes_secondCall_newTaskNotReflected() {
        // given — 오늘 마감 과업만 있어 FIRE가 빈 채로 캐시됨
        task("오늘마감", TODAY, 60, 0, false);

        RecommendationModesResponse first = recommendationService.getRecommendationModes(user.getId());
        entityManager.flush();
        entityManager.clear();

        // 재계산 시 FIRE에 포함될 HIGH 우선순위 과업 추가
        task("새HIGH", TODAY.plusDays(1), 60, 0, false);
        entityManager.flush();
        entityManager.clear();

        // when
        RecommendationModesResponse second = recommendationService.getRecommendationModes(user.getId());

        // then — FIRE 캐시 히트 → 새 과업 미반영, 첫 호출과 동일한 결과
        List<Long> firstFireIds = first.modes().get(0).tasks().stream()
                .map(RecommendationModesResponse.ModeTaskItem::taskId).toList();
        List<Long> secondFireIds = second.modes().get(0).tasks().stream()
                .map(RecommendationModesResponse.ModeTaskItem::taskId).toList();
        assertThat(secondFireIds).containsExactlyElementsOf(firstFireIds);
    }

    // ─── 모드 조합 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("모드 조합 — 당일 마감 과업은 모든 모드의 조합에서 제외된다")
    void getRecommendationModes_todayDeadlineTask_excludedFromAllModes() {
        // n=2: todayTask(HIGH, deadline=TODAY), futureTask(MEDIUM, deadline=+2일)
        // modeRanked: todayTask 제외 → futureTask만 포함
        Task todayTask  = task("오늘마감", TODAY,             60, 0,  false);
        task("미래",       TODAY.plusDays(2), 60, 20, false);

        RecommendationModesResponse result = recommendationService.getRecommendationModes(user.getId());

        result.modes().forEach(m ->
            assertThat(m.tasks())
                .noneMatch(t -> t.taskId().equals(todayTask.getId()))
        );
    }
}

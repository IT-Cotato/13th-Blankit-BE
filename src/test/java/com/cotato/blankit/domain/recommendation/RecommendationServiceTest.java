package com.cotato.blankit.domain.recommendation;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.recommendation.dto.response.TodayRecommendationResponse;
import com.cotato.blankit.domain.recommendation.service.RecommendationService;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskPriority;
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
                .extracting(TodayRecommendationResponse.RecommendedTaskItem::taskId)
                .containsExactly(task1.getId(), task2.getId(), task3.getId());
        assertThat(result.topTasks().get(0).score()).isEqualByComparingTo(new BigDecimal("1.20"));
        assertThat(result.topTasks().get(1).score()).isEqualByComparingTo(new BigDecimal("1.80"));
        assertThat(result.topTasks().get(2).score()).isEqualByComparingTo(new BigDecimal("3.00"));
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

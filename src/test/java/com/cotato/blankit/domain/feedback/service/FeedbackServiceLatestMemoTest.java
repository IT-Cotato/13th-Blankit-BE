package com.cotato.blankit.domain.feedback.service;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.feedback.repository.FeedbackRepository;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:feedback-service-memo-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class FeedbackServiceLatestMemoTest {

    @Autowired private FeedbackService feedbackService;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private TaskSessionRepository taskSessionRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    private User user;
    private Task task;
    private Category category;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create(SocialProvider.KAKAO, "memo-test-user", "memo@example.com", "메모유저", null, 120));
        category = categoryRepository.save(Category.create(user, "학업", "#FC5F5F", "book", 0, true));
        task = taskRepository.save(Task.create(user, category, "메모 테스트 과업", LocalDate.now().plusDays(3), null));
    }

    @Nested
    @DisplayName("빈 taskIds 입력")
    class EmptyInput {

        @Test
        @DisplayName("빈 리스트를 전달하면 빈 맵을 반환한다")
        void getLatestMemoMap_emptyTaskIds() {
            // when
            Map<Long, String> result = feedbackService.getLatestMemoMap(List.of());

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("단일 task — memo 반환 규칙")
    class SingleTask {

        @Test
        @DisplayName("최종 제출 피드백이 두 개일 때 submittedAt이 더 늦은 피드백의 memo를 반환한다")
        void getLatestMemoMap_latestSubmittedMemoWins() throws InterruptedException {
            // given — 먼저 저장한 피드백
            TaskSession session1 = taskSessionRepository.saveAndFlush(
                    TaskSession.create(task, user, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusMinutes(5), 300, TaskSessionStatus.DONE));
            feedbackRepository.saveAndFlush(Feedback.create(session1, task, user, 30, "먼저 저장한 메모", false));

            // submittedAt은 Feedback.create() 내부에서 LocalDateTime.now()로 설정되므로
            // 두 피드백의 시각 차이를 보장하기 위해 sleep 사용
            Thread.sleep(5);

            // 나중에 저장한 피드백
            TaskSession session2 = taskSessionRepository.saveAndFlush(
                    TaskSession.create(task, user, LocalDateTime.now().minusMinutes(4), LocalDateTime.now().minusMinutes(1), 180, TaskSessionStatus.DONE));
            feedbackRepository.saveAndFlush(Feedback.create(session2, task, user, 60, "나중에 저장한 메모", false));

            // when
            Map<Long, String> result = feedbackService.getLatestMemoMap(List.of(task.getId()));

            // then
            assertThat(result.get(task.getId())).isEqualTo("나중에 저장한 메모");
        }

        @Test
        @DisplayName("isDraft=true 피드백만 있으면 null을 반환한다")
        void getLatestMemoMap_draftOnly() {
            // given
            TaskSession session = taskSessionRepository.save(
                    TaskSession.create(task, user, LocalDateTime.now().minusMinutes(5), null, 0, TaskSessionStatus.PLAYING));
            feedbackRepository.save(Feedback.create(session, task, user, 20, "임시 메모", true));

            // when
            Map<Long, String> result = feedbackService.getLatestMemoMap(List.of(task.getId()));

            // then
            assertThat(result.get(task.getId())).isNull();
        }

        @Test
        @DisplayName("최종 제출이지만 memo가 blank이면 null을 반환한다")
        void getLatestMemoMap_blankMemo() {
            // given
            TaskSession session = taskSessionRepository.save(
                    TaskSession.create(task, user, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusMinutes(1), 300, TaskSessionStatus.DONE));
            feedbackRepository.save(Feedback.create(session, task, user, 50, "   ", false));

            // when
            Map<Long, String> result = feedbackService.getLatestMemoMap(List.of(task.getId()));

            // then
            assertThat(result.get(task.getId())).isNull();
        }

        @Test
        @DisplayName("최종 제출이지만 memo가 null이면 null을 반환한다")
        void getLatestMemoMap_nullMemo() {
            // given
            TaskSession session = taskSessionRepository.save(
                    TaskSession.create(task, user, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusMinutes(1), 300, TaskSessionStatus.DONE));
            feedbackRepository.save(Feedback.create(session, task, user, 50, null, false));

            // when
            Map<Long, String> result = feedbackService.getLatestMemoMap(List.of(task.getId()));

            // then
            assertThat(result.get(task.getId())).isNull();
        }

        @Test
        @DisplayName("피드백이 전혀 없으면 null을 반환한다")
        void getLatestMemoMap_noFeedback() {
            // when
            Map<Long, String> result = feedbackService.getLatestMemoMap(List.of(task.getId()));

            // then
            assertThat(result.get(task.getId())).isNull();
        }
    }

    @Nested
    @DisplayName("복수 task 배치 처리")
    class MultipleTasks {

        @Test
        @DisplayName("여러 task를 한 번에 조회하면 task별로 각각 올바른 memo를 반환한다")
        void getLatestMemoMap_multipleTaskIds() {
            // given
            Task taskB = taskRepository.save(Task.create(user, category, "두 번째 과업", LocalDate.now().plusDays(5), null));

            TaskSession sessionA = taskSessionRepository.save(
                    TaskSession.create(task, user, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusMinutes(5), 300, TaskSessionStatus.DONE));
            feedbackRepository.save(Feedback.create(sessionA, task, user, 50, "과업A 메모", false));
            // taskB는 피드백 없음

            // when
            Map<Long, String> result = feedbackService.getLatestMemoMap(List.of(task.getId(), taskB.getId()));

            // then
            assertThat(result.get(task.getId())).isEqualTo("과업A 메모");
            assertThat(result.get(taskB.getId())).isNull();
        }
    }
}

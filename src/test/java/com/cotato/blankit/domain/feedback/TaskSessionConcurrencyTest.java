package com.cotato.blankit.domain.feedback;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.feedback.dto.request.SessionStatusUpdateRequest;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.feedback.repository.DailyElapsedTimeRepository;
import com.cotato.blankit.domain.feedback.repository.PlayIntervalRepository;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.domain.feedback.service.TaskSessionService;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:session-concurrency-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class TaskSessionConcurrencyTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @MockitoBean Clock clock;

    @Autowired private TaskSessionService taskSessionService;
    @Autowired private TaskSessionRepository taskSessionRepository;
    @Autowired private PlayIntervalRepository playIntervalRepository;
    @Autowired private DailyElapsedTimeRepository dailyElapsedTimeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long userId;
    private Long session1Id;
    private Long session2Id;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(SEOUL);
        when(clock.instant()).thenReturn(
                LocalDate.of(2026, 8, 1).atTime(10, 0).toInstant(ZoneOffset.ofHours(9)));

        transactionTemplate.execute(status -> {
            User user = userRepository.save(
                    User.create(SocialProvider.KAKAO, "concurrency-user", "concurrency@example.com", "동시성유저", null, 120));
            userId = user.getId();
            Category category = categoryRepository.save(
                    Category.create(user, "학업", "#FF5C5C", "book", 0, true));
            Task task1 = taskRepository.save(
                    Task.create(user, category, "과업1", LocalDate.of(2026, 8, 31), null, 60));
            Task task2 = taskRepository.save(
                    Task.create(user, category, "과업2", LocalDate.of(2026, 8, 31), null, 60));
            session1Id = taskSessionService.startSession(user.getId(), task1.getId()).taskSessionId();
            session2Id = taskSessionService.startSession(user.getId(), task2.getId()).taskSessionId();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.execute(status -> {
            dailyElapsedTimeRepository.deleteAllInBatch();
            playIntervalRepository.deleteAllInBatch();
            taskSessionRepository.deleteAllInBatch();
            taskRepository.deleteAllInBatch();
            categoryRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
            return null;
        });
    }

    @Test
    @DisplayName("동시에 두 세션을 PLAYING으로 전환해도 최종적으로 하나만 PLAYING 상태여야 한다")
    void parallelPlay_onlyOneSessionRemainsPlaying() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> f1 = executor.submit((Callable<Void>) () -> {
            ready.countDown();
            start.await();
            transactionTemplate.execute(s -> {
                taskSessionService.updateSessionStatus(userId, session1Id,
                        new SessionStatusUpdateRequest(TaskSessionStatus.PLAYING, 0));
                return null;
            });
            return null;
        });

        Future<?> f2 = executor.submit((Callable<Void>) () -> {
            ready.countDown();
            start.await();
            transactionTemplate.execute(s -> {
                taskSessionService.updateSessionStatus(userId, session2Id,
                        new SessionStatusUpdateRequest(TaskSessionStatus.PLAYING, 0));
                return null;
            });
            return null;
        });

        ready.await();
        start.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        long playingCount = transactionTemplate.execute(status ->
                taskSessionRepository.findAll().stream()
                        .filter(s -> s.getUser().getId().equals(userId))
                        .filter(s -> s.getStatus() == TaskSessionStatus.PLAYING)
                        .count()
        );
        long pausedCount = transactionTemplate.execute(status ->
                taskSessionRepository.findAll().stream()
                        .filter(s -> s.getUser().getId().equals(userId))
                        .filter(s -> s.getStatus() == TaskSessionStatus.PAUSED)
                        .count()
        );
        assertThat(playingCount).isOne();
        assertThat(pausedCount).isOne();
    }
}

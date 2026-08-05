package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.task.dto.request.TaskStepCreateRequest;
import com.cotato.blankit.domain.task.dto.request.TaskStepUpdateRequest;
import com.cotato.blankit.domain.task.dto.response.TaskStepResponse;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskStep;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.task.repository.TaskStepRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TaskStepServiceTest {

    @Mock TaskStepRepository taskStepRepository;
    @Mock TaskRepository taskRepository;
    @InjectMocks TaskStepService taskStepService;

    @Mock Task task;

    private static final Long TASK_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long STEP_ID = 100L;

    // ─── getSteps ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSteps")
    class GetSteps {

        @Test
        @DisplayName("과업 소유자가 조회하면 단계 목록이 반환된다")
        void getSteps_success() {
            // given
            TaskStep step1 = TaskStep.create(task, "개념 정리");
            TaskStep step2 = TaskStep.create(task, "문제 풀이");
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(TASK_ID)).willReturn(List.of(step1, step2));

            // when
            List<TaskStepResponse> result = taskStepService.getSteps(TASK_ID, USER_ID);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).title()).isEqualTo("개념 정리");
            assertThat(result.get(1).title()).isEqualTo("문제 풀이");
        }

        @Test
        @DisplayName("단계가 없으면 빈 리스트를 반환한다")
        void getSteps_empty() {
            // given
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(TASK_ID)).willReturn(List.of());

            // when
            List<TaskStepResponse> result = taskStepService.getSteps(TASK_ID, USER_ID);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("과업이 없거나 소유자가 아니면 TASK_NOT_FOUND 예외가 발생하고 단계 조회를 하지 않는다")
        void getSteps_taskNotFound() {
            // given
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> taskStepService.getSteps(TASK_ID, USER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TASK_NOT_FOUND);
            then(taskStepRepository).shouldHaveNoInteractions();
        }
    }

    // ─── createStep ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createStep")
    class CreateStep {

        @Test
        @DisplayName("첫 번째 단계 추가 시 과업 진행률이 0으로 초기화된다")
        void createStep_firstStep_resetsProgressRate() {
            // given
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(TASK_ID)).willReturn(List.of());
            given(taskStepRepository.save(any(TaskStep.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            taskStepService.createStep(TASK_ID, USER_ID, new TaskStepCreateRequest("개념 정리"));

            // then
            then(task).should().updateProgressRate(0);
        }

        @Test
        @DisplayName("기존 단계가 있을 때 단계를 추가하면 0으로 초기화하지 않고 재계산된 값으로 갱신한다")
        void createStep_additionalStep_doesNotResetProgressRate() {
            // given: existing step progressRate=60, new step=0 → (60+0)/2 = 30
            TaskStep existing = TaskStep.create(task, "기존 단계");
            existing.update(null, 60);
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(TASK_ID)).willReturn(List.of(existing));
            given(taskStepRepository.save(any(TaskStep.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            taskStepService.createStep(TASK_ID, USER_ID, new TaskStepCreateRequest("새 단계"));

            // then: 0 초기화가 아닌 재계산 값(30)으로 갱신
            then(task).should().updateProgressRate(30);
        }

        @Test
        @DisplayName("진행률이 반영된 단계가 있을 때 새 단계를 추가하면 전체 진행률이 재계산된다")
        void createStep_withExistingProgress_recalculates() {
            // given: step1(60), step2(80) 있는 상태에서 step3(0) 추가
            // → (60+80+0)/3 = 46.67 → 47
            TaskStep step1 = TaskStep.create(task, "step1");
            step1.update(null, 60);
            TaskStep step2 = TaskStep.create(task, "step2");
            step2.update(null, 80);
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(TASK_ID)).willReturn(List.of(step1, step2));
            given(taskStepRepository.save(any(TaskStep.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            taskStepService.createStep(TASK_ID, USER_ID, new TaskStepCreateRequest("새 단계"));

            // then: (60+80+0)/3 = 46.67 → 47
            then(task).should().updateProgressRate(47);
        }

        @Test
        @DisplayName("과업이 없으면 TASK_NOT_FOUND 예외가 발생한다")
        void createStep_taskNotFound() {
            // given
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> taskStepService.createStep(TASK_ID, USER_ID, new TaskStepCreateRequest("단계")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TASK_NOT_FOUND);
        }
    }

    // ─── updateStep ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStep")
    class UpdateStep {

        @Test
        @DisplayName("진척도를 변경하면 전체 단계 평균으로 과업 진행률이 재계산된다")
        void updateStep_progressRate_recalculates() {
            // given: step(0→80), other(60) → avg(80,60)=70
            TaskStep step = TaskStep.create(task, "개념 정리");
            TaskStep other = TaskStep.create(task, "문제 풀이");
            other.update(null, 60);
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskStepIdAndTaskId(STEP_ID, TASK_ID)).willReturn(Optional.of(step));
            given(taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(TASK_ID)).willReturn(List.of(step, other));

            // when
            taskStepService.updateStep(TASK_ID, STEP_ID, USER_ID, new TaskStepUpdateRequest(null, 80));

            // then
            then(task).should().updateProgressRate(70);
        }

        @Test
        @DisplayName("제목만 변경하면 과업 진행률을 재계산하지 않는다")
        void updateStep_titleOnly_doesNotRecalculate() {
            // given
            TaskStep step = TaskStep.create(task, "개념 정리");
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskStepIdAndTaskId(STEP_ID, TASK_ID)).willReturn(Optional.of(step));

            // when
            taskStepService.updateStep(TASK_ID, STEP_ID, USER_ID, new TaskStepUpdateRequest("수정된 제목", null));

            // then
            assertThat(step.getTitle()).isEqualTo("수정된 제목");
            then(task).should(never()).updateProgressRate(anyInt());
        }

        @Test
        @DisplayName("제목이 공백 문자열이면 INVALID_TASK_TITLE 예외가 발생하고 소유권 확인을 하지 않는다")
        void updateStep_blankTitle_throwsInvalidTaskTitle() {
            // given & when & then
            assertThatThrownBy(() -> taskStepService.updateStep(TASK_ID, STEP_ID, USER_ID, new TaskStepUpdateRequest("   ", null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TASK_TITLE);
            then(taskRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("단계가 없으면 TASK_STEP_NOT_FOUND 예외가 발생한다")
        void updateStep_stepNotFound() {
            // given
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskStepIdAndTaskId(STEP_ID, TASK_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> taskStepService.updateStep(TASK_ID, STEP_ID, USER_ID, new TaskStepUpdateRequest(null, 50)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TASK_STEP_NOT_FOUND);
        }
    }

    // ─── deleteStep ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteStep")
    class DeleteStep {

        @Test
        @DisplayName("삭제 후 단계가 남아있으면 평균으로 과업 진행률을 재계산한다")
        void deleteStep_remainingSteps_recalculates() {
            // given: remaining step(60) → avg=60
            TaskStep stepToDelete = TaskStep.create(task, "삭제할 단계");
            TaskStep remaining = TaskStep.create(task, "남은 단계");
            remaining.update(null, 60);
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskStepIdAndTaskId(STEP_ID, TASK_ID)).willReturn(Optional.of(stepToDelete));
            given(taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(TASK_ID)).willReturn(List.of(remaining));

            // when
            taskStepService.deleteStep(TASK_ID, STEP_ID, USER_ID);

            // then
            then(taskStepRepository).should().delete(stepToDelete);
            then(task).should().updateProgressRate(60);
        }

        @Test
        @DisplayName("마지막 단계를 삭제하면 단계 없는 모드로 돌아가므로 과업 진행률이 0으로 초기화된다")
        void deleteStep_lastStep_resetsProgressRateToZero() {
            // given
            TaskStep step = TaskStep.create(task, "마지막 단계");
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskStepIdAndTaskId(STEP_ID, TASK_ID)).willReturn(Optional.of(step));
            given(taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(TASK_ID)).willReturn(List.of());

            // when
            taskStepService.deleteStep(TASK_ID, STEP_ID, USER_ID);

            // then
            then(task).should().updateProgressRate(0);
        }

        @Test
        @DisplayName("진행률이 기록된 단계가 여러 개일 때 하나를 삭제하면 나머지 단계 평균으로 재계산된다")
        void deleteStep_multipleStepsWithProgress_recalculatesCorrectly() {
            // given: step1(60), step2(80), step3(40) 중 step2(80) 삭제 → (60+40)/2 = 50
            TaskStep stepToDelete = TaskStep.create(task, "step2");
            stepToDelete.update(null, 80);
            TaskStep step1 = TaskStep.create(task, "step1");
            step1.update(null, 60);
            TaskStep step3 = TaskStep.create(task, "step3");
            step3.update(null, 40);
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskStepIdAndTaskId(STEP_ID, TASK_ID)).willReturn(Optional.of(stepToDelete));
            given(taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(TASK_ID)).willReturn(List.of(step1, step3));

            // when
            taskStepService.deleteStep(TASK_ID, STEP_ID, USER_ID);

            // then
            then(task).should().updateProgressRate(50);
        }

        @Test
        @DisplayName("단계가 없으면 TASK_STEP_NOT_FOUND 예외가 발생하고 삭제가 실행되지 않는다")
        void deleteStep_stepNotFound() {
            // given
            given(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).willReturn(Optional.of(task));
            given(taskStepRepository.findByTaskStepIdAndTaskId(STEP_ID, TASK_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> taskStepService.deleteStep(TASK_ID, STEP_ID, USER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TASK_STEP_NOT_FOUND);
            then(taskStepRepository).should(never()).delete(any());
        }
    }

    // ─── applyAndCalculateProgress ────────────────────────────────────────────

    @Nested
    @DisplayName("applyAndCalculateProgress")
    class ApplyAndCalculateProgress {

        @Test
        @DisplayName("단계별 진행률의 균등 가중 평균을 반환한다 — [90, 60, 0] → 50")
        void applyAndCalculateProgress_average() {
            // given
            Task mockTask = mock(Task.class);
            TaskStep step1 = TaskStep.create(mockTask, "step1");
            TaskStep step2 = TaskStep.create(mockTask, "step2");
            TaskStep step3 = TaskStep.create(mockTask, "step3");
            given(taskStepRepository.findByTaskStepIdAndTaskId(1L, TASK_ID)).willReturn(Optional.of(step1));
            given(taskStepRepository.findByTaskStepIdAndTaskId(2L, TASK_ID)).willReturn(Optional.of(step2));
            given(taskStepRepository.findByTaskStepIdAndTaskId(3L, TASK_ID)).willReturn(Optional.of(step3));
            given(taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(TASK_ID)).willReturn(List.of(step1, step2, step3));

            // when
            int result = taskStepService.applyAndCalculateProgress(TASK_ID, Map.of(1L, 90, 2L, 60, 3L, 0));

            // then
            assertThat(result).isEqualTo(50);
            assertThat(step1.getProgressRate()).isEqualTo(90);
            assertThat(step2.getProgressRate()).isEqualTo(60);
            assertThat(step3.getProgressRate()).isEqualTo(0);
        }

        @Test
        @DisplayName("소수점은 반올림한다 — [100, 60, 0] → 53")
        void applyAndCalculateProgress_rounding() {
            // given: (100+60+0)/3 = 53.33 → Math.round → 53
            Task mockTask = mock(Task.class);
            TaskStep step1 = TaskStep.create(mockTask, "step1");
            TaskStep step2 = TaskStep.create(mockTask, "step2");
            TaskStep step3 = TaskStep.create(mockTask, "step3");
            given(taskStepRepository.findByTaskStepIdAndTaskId(1L, TASK_ID)).willReturn(Optional.of(step1));
            given(taskStepRepository.findByTaskStepIdAndTaskId(2L, TASK_ID)).willReturn(Optional.of(step2));
            given(taskStepRepository.findByTaskStepIdAndTaskId(3L, TASK_ID)).willReturn(Optional.of(step3));
            given(taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(TASK_ID)).willReturn(List.of(step1, step2, step3));

            // when
            int result = taskStepService.applyAndCalculateProgress(TASK_ID, Map.of(1L, 100, 2L, 60, 3L, 0));

            // then
            assertThat(result).isEqualTo(53);
        }

        @Test
        @DisplayName("존재하지 않는 stepId이면 TASK_STEP_NOT_FOUND 예외가 발생한다")
        void applyAndCalculateProgress_stepNotFound() {
            // given
            given(taskStepRepository.findByTaskStepIdAndTaskId(999L, TASK_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> taskStepService.applyAndCalculateProgress(TASK_ID, Map.of(999L, 50)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TASK_STEP_NOT_FOUND);
        }
    }
}

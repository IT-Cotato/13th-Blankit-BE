package com.cotato.blankit.domain.task.entity;

import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "task_step")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskStep extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskStepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private int progressRate;

    @Column(nullable = false)
    private int sortOrder;

    public static TaskStep create(Task task, String title) {
        TaskStep step = new TaskStep();
        step.task = task;
        step.title = title;
        step.progressRate = 0;
        step.sortOrder = 0;
        return step;
    }

    public void update(String title, Integer progressRate) {
        if (title != null) this.title = title;
        if (progressRate != null) this.progressRate = progressRate;
    }
}

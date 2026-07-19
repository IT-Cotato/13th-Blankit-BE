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
}

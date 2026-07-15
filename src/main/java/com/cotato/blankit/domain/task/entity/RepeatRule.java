package com.cotato.blankit.domain.task.entity;

import com.cotato.blankit.domain.task.entity.enums.RepeatFrequency;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "repeat_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepeatRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long repeatRuleId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('WEEKLY','MONTHLY','YEARLY')")
    private RepeatFrequency frequency;

    @Column(length = 50)
    private String daysOfWeek;

    @Column(length = 100)
    private String daysOfMonth;

    private Integer monthOfYear;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;
}

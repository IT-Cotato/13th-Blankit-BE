package com.cotato.blankit.domain.task.entity;

import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Entity
@Table(
        name = "repeat_rule",
        uniqueConstraints = @UniqueConstraint(name = "uk_repeat_rule_task", columnNames = "task_id"),
        indexes = @Index(name = "idx_repeat_rule_task", columnList = "task_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepeatRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repeat_rule_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurrenceType frequency;

    @Convert(converter = IntegerListStringConverter.class)
    @Column(name = "days_of_week", length = 50)
    private List<Integer> daysOfWeek = List.of();

    @Convert(converter = RepeatMonthDaysConverter.class)
    @Column(name = "days_of_month", length = 100)
    private RepeatMonthDays daysOfMonth = RepeatMonthDays.none();

    @Column(name = "month_of_year")
    private Integer monthOfYear;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    public static RepeatRule create(
            Task task,
            RecurrenceType frequency,
            List<Integer> daysOfWeek,
            RepeatMonthDays daysOfMonth,
            Integer monthOfYear,
            LocalDate startDate,
            LocalDate endDate
    ) {
        RepeatRule repeatRule = new RepeatRule();
        repeatRule.task = task;
        repeatRule.update(frequency, daysOfWeek, daysOfMonth, monthOfYear, startDate, endDate);
        return repeatRule;
    }

    public void update(
            RecurrenceType frequency,
            List<Integer> daysOfWeek,
            RepeatMonthDays daysOfMonth,
            Integer monthOfYear,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this.frequency = frequency;
        this.daysOfWeek = daysOfWeek == null ? List.of() : daysOfWeek;
        this.daysOfMonth = daysOfMonth == null ? RepeatMonthDays.none() : daysOfMonth;
        this.monthOfYear = monthOfYear;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}

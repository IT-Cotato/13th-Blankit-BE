package com.cotato.blankit.domain.recommendation.entity;

import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "daily_recommendation_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRecommendationItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dailyRecommendationItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_recommendation_id", nullable = false)
    private DailyRecommendation dailyRecommendation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false)
    private int rankOrder;

    @Column(precision = 8, scale = 2)
    private BigDecimal score;

    private Integer recommendedMinutes;
}

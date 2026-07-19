package com.cotato.blankit.domain.recommendation.entity;

import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "daily_recommendation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRecommendation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dailyRecommendationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate recommendedDate;

    @Column(nullable = false, length = 30)
    private String mode;

    private Integer availableMinutes;

    @Column(nullable = false)
    private int totalRecommendedMinutes;
}

package com.cotato.blankit.domain.recommendation.entity;

import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
        name = "daily_recommendation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_rec_user_date_mode",
                columnNames = {"user_id", "recommended_date", "mode"}
        )
)
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

    public static DailyRecommendation ofToday(User user, LocalDate date, int totalRecommendedMinutes) {
        DailyRecommendation dr = new DailyRecommendation();
        dr.user = user;
        dr.recommendedDate = date;
        dr.mode = "TODAY";
        dr.totalRecommendedMinutes = totalRecommendedMinutes;
        return dr;
    }

    public static DailyRecommendation ofMode(User user, LocalDate date, String mode) {
        DailyRecommendation dr = new DailyRecommendation();
        dr.user = user;
        dr.recommendedDate = date;
        dr.mode = mode;
        dr.totalRecommendedMinutes = 0;
        return dr;
    }
}
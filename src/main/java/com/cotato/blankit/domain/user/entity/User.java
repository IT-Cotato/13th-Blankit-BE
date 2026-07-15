package com.cotato.blankit.domain.user.entity;

import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "`user`",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_social_provider_social_id",
                columnNames = {"social_provider", "social_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", length = 20, nullable = false)
    private SocialProvider socialProvider;

    @Column(name = "social_id", nullable = false)
    private String socialId;

    @Column(length = 100, nullable = false)
    private String nickname;

    @Column(length = 255)
    private String email;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "recommended_daily_time")
    private Integer recommendedDailyTime;

    public static User create(
            SocialProvider socialProvider,
            String socialId,
            String email,
            String nickname,
            String profileImageUrl,
            Integer recommendedDailyTime
    ) {
        User user = new User();
        user.socialProvider = socialProvider;
        user.socialId = socialId;
        user.email = email;
        user.nickname = nickname;
        user.profileImageUrl = profileImageUrl;
        user.recommendedDailyTime = recommendedDailyTime;
        return user;
    }
}

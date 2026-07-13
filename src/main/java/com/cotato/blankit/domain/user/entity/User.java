package com.cotato.blankit.domain.user.entity;

import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(
        name = "`user`",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_social", columnNames = {"social_provider", "social_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String socialProvider;

    @Column(nullable = false, length = 255)
    private String socialId;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(length = 255)
    private String email;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(nullable = false)
    private LocalTime timetableStartTime;

    @Column(nullable = false)
    private LocalTime timetableEndTime;
}

package com.cotato.blankit.domain.auth.repository;

import com.cotato.blankit.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.user.id = :userId")
    Optional<RefreshToken> findByUserIdForUpdate(@Param("userId") Long userId);

    boolean existsByUserIdAndSessionIdAndExpiresAtAfter(
            Long userId,
            String sessionId,
            LocalDateTime now
    );

    void deleteByUserId(Long userId);
}

package com.cotato.blankit.domain.recommendation.repository;

import com.cotato.blankit.domain.recommendation.entity.DailyRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyRecommendationRepository extends JpaRepository<DailyRecommendation, Long> {

    Optional<DailyRecommendation> findByUser_IdAndRecommendedDateAndMode(Long userId, LocalDate date, String mode);

    boolean existsByUser_IdAndRecommendedDateAndMode(Long userId, LocalDate date, String mode);
}

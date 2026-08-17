package com.cotato.blankit.domain.recommendation.repository;

import com.cotato.blankit.domain.recommendation.entity.DailyRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyRecommendationRepository extends JpaRepository<DailyRecommendation, Long> {

    Optional<DailyRecommendation> findByUser_IdAndRecommendedDateAndMode(Long userId, LocalDate date, String mode);

    boolean existsByUser_IdAndRecommendedDateAndMode(Long userId, LocalDate date, String mode);

    long countByUser_IdAndRecommendedDateAndModeIn(Long userId, LocalDate date, List<String> modes);

    List<DailyRecommendation> findAllByUser_IdAndRecommendedDateAndModeIn(Long userId, LocalDate date, List<String> modes);
}

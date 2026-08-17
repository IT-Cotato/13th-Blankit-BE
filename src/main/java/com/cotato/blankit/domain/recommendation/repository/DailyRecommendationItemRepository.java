package com.cotato.blankit.domain.recommendation.repository;

import com.cotato.blankit.domain.recommendation.entity.DailyRecommendation;
import com.cotato.blankit.domain.recommendation.entity.DailyRecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyRecommendationItemRepository extends JpaRepository<DailyRecommendationItem, Long> {

    List<DailyRecommendationItem> findAllByDailyRecommendationOrderByRankOrder(DailyRecommendation dailyRecommendation);

    void deleteAllByDailyRecommendationIn(List<DailyRecommendation> dailyRecommendations);
}

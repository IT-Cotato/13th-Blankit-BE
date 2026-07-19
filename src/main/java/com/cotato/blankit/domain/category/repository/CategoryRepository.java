package com.cotato.blankit.domain.category.repository;

import com.cotato.blankit.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    List<Category> findByUserIdAndDeletedFalseOrderBySortOrderAscCreatedAtAscIdAsc(Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndColorAndDeletedFalse(Long userId, String color);

    boolean existsByUserIdAndColorAndDeletedFalseAndIdNot(Long userId, String color, Long id);
}

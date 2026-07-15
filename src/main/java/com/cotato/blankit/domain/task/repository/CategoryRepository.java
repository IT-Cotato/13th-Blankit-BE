package com.cotato.blankit.domain.task.repository;

import com.cotato.blankit.domain.task.entity.Category;
import com.cotato.blankit.domain.task.entity.CategoryColor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    List<Category> findByUserIdAndDeletedFalseOrderBySortOrderAscCreatedAtAscIdAsc(Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndColorAndDeletedFalse(Long userId, CategoryColor color);

    boolean existsByUserIdAndColorAndDeletedFalseAndIdNot(Long userId, CategoryColor color, Long id);
}

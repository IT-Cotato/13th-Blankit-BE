package com.cotato.blankit.domain.category.repository;

import com.cotato.blankit.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}

package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.task.dto.request.CategoryCreateRequest;
import com.cotato.blankit.domain.task.dto.request.CategoryUpdateRequest;
import com.cotato.blankit.domain.task.dto.response.CategoryResponse;
import com.cotato.blankit.domain.task.entity.Category;
import com.cotato.blankit.domain.task.entity.CategoryColor;
import com.cotato.blankit.domain.task.repository.CategoryRepository;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("학업", CategoryColor.BLUE, 0),
            new DefaultCategory("일상", CategoryColor.GREEN, 1),
            new DefaultCategory("기념일", CategoryColor.YELLOW, 2)
    );

    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createDefaultCategoriesIfNeverInitialized(User user) {
        if (categoryRepository.countByUserId(user.getId()) > 0) {
            return;
        }
        DEFAULT_CATEGORIES.forEach(defaultCategory ->
                categoryRepository.save(Category.create(
                        user,
                        defaultCategory.name(),
                        defaultCategory.color(),
                        defaultCategory.sortOrder(),
                        true
                ))
        );
    }

    @Transactional
    public List<CategoryResponse> getCategories(Long userId) {
        User user = getUser(userId);
        createDefaultCategoriesIfNeverInitialized(user);
        return getActiveCategories(userId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(Long userId, CategoryCreateRequest request) {
        validateName(request.name());
        validateColorAvailable(userId, request.color(), null);
        Category category = Category.create(getUser(userId), request.name().trim(), request.color());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long userId, Long categoryId, CategoryUpdateRequest request) {
        Category category = getActiveCategory(userId, categoryId);
        String name = request.name() == null ? category.getName() : request.name();
        CategoryColor color = request.color() == null ? category.getColor() : request.color();
        validateName(name);
        validateColorAvailable(userId, color, categoryId);
        category.update(name.trim(), color);
        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        Category category = getActiveCategory(userId, categoryId);
        if (taskRepository.existsByCategoryIdAndUserId(categoryId, userId)) {
            throw new CustomException(ErrorCode.CATEGORY_IN_USE);
        }
        category.delete();
    }

    @Transactional(readOnly = true)
    public List<CategoryColor> getAvailableColors(Long userId, Long editingCategoryId) {
        Category editingCategory = editingCategoryId == null ? null : getActiveCategory(userId, editingCategoryId);
        return Arrays.stream(CategoryColor.values())
                .filter(color -> editingCategory != null && editingCategory.getColor() == color
                        || !categoryRepository.existsByUserIdAndColorAndDeletedFalse(userId, color))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Category> getActiveCategories(Long userId) {
        return categoryRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscCreatedAtAscIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public Category getActiveCategory(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUserIdAndDeletedFalse(categoryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private void validateColorAvailable(Long userId, CategoryColor color, Long editingCategoryId) {
        boolean used = editingCategoryId == null
                ? categoryRepository.existsByUserIdAndColorAndDeletedFalse(userId, color)
                : categoryRepository.existsByUserIdAndColorAndDeletedFalseAndIdNot(userId, color, editingCategoryId);
        if (used) {
            throw new CustomException(ErrorCode.CATEGORY_COLOR_ALREADY_USED);
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private record DefaultCategory(String name, CategoryColor color, int sortOrder) {
    }
}

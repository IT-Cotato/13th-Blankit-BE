package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.task.dto.request.CategoryCreateRequest;
import com.cotato.blankit.domain.task.dto.request.CategoryUpdateRequest;
import com.cotato.blankit.domain.task.dto.response.CategoryResponse;
import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.task.repository.CategoryRepository;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("학업", "#5C9EFF", 0),
            new DefaultCategory("일상", "#5CFF8A", 1),
            new DefaultCategory("기념일", "#FFB85C", 2)
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
        String color = normalizeColor(request.color());
        validateColorAvailable(userId, color, null);
        Category category = Category.create(getUser(userId), request.name().trim(), color);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long userId, Long categoryId, CategoryUpdateRequest request) {
        Category category = getActiveCategory(userId, categoryId);
        String name = request.name() == null ? category.getName() : request.name();
        String color = request.color() == null ? category.getColor() : normalizeColor(request.color());
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
    public List<String> getAvailableColors(Long userId, Long editingCategoryId) {
        Category editingCategory = editingCategoryId == null ? null : getActiveCategory(userId, editingCategoryId);
        List<String> suggestedColors = new ArrayList<>(DEFAULT_CATEGORIES.stream()
                .map(DefaultCategory::color)
                .distinct()
                .toList());
        if (editingCategory != null && !suggestedColors.contains(editingCategory.getColor())) {
            suggestedColors.add(0, editingCategory.getColor());
        }
        return suggestedColors.stream()
                .filter(color -> editingCategory != null && color.equals(editingCategory.getColor())
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

    private void validateColorAvailable(Long userId, String color, Long editingCategoryId) {
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

    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalizedColor = color.trim();
        if (!HEX_COLOR_PATTERN.matcher(normalizedColor).matches()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalizedColor.toUpperCase(Locale.ROOT);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private record DefaultCategory(String name, String color, int sortOrder) {
    }
}

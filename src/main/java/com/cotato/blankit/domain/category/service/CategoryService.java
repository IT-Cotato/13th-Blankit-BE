package com.cotato.blankit.domain.category.service;

import com.cotato.blankit.domain.category.dto.request.CategoryCreateRequest;
import com.cotato.blankit.domain.category.dto.request.CategoryUpdateRequest;
import com.cotato.blankit.domain.category.dto.response.CategoryResponse;
import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final List<String> CATEGORY_COLORS = List.of(
            "#FC5F5F",
            "#FF9A33",
            "#FBF965",
            "#D3FB65",
            "#5BE478",
            "#5BE4CB",
            "#6FD4FF",
            "#B3BBFA",
            "#F2B3FA",
            "#C5C9CD"
    );

    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("학업", "#FC5F5F", "pen", 0),
            new DefaultCategory("일상", "#FF9A33", "msg", 1),
            new DefaultCategory("기념일", "#FBF965", "pin", 2)
    );

    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createDefaultCategoriesIfNeverInitialized(User user) {
        User lockedUser = getUserForUpdate(user.getId());
        if (categoryRepository.countByUserId(lockedUser.getId()) > 0) {
            return;
        }
        DEFAULT_CATEGORIES.forEach(defaultCategory ->
                categoryRepository.save(Category.create(
                        lockedUser,
                        defaultCategory.name(),
                        defaultCategory.color(),
                        defaultCategory.iconKey(),
                        defaultCategory.sortOrder(),
                        true
                ))
        );
    }

    @Transactional
    public List<CategoryResponse> getCategories(Long userId) {
        User user = getUserForUpdate(userId);
        createDefaultCategoriesIfNeverInitialized(user);
        return getActiveCategories(userId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(Long userId, CategoryCreateRequest request) {
        User user = getUserForUpdate(userId);
        validateName(request.name());
        validateIconKey(request.iconKey());
        String color = normalizeColor(request.color());
        validateColorAvailable(userId, color, null);
        Category category = Category.create(user, request.name().trim(), color, request.iconKey().trim());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long userId, Long categoryId, CategoryUpdateRequest request) {
        getUserForUpdate(userId);
        Category category = getActiveCategory(userId, categoryId);
        String name = request.name() == null ? category.getName() : request.name();
        String color = request.color() == null ? category.getColor() : normalizeColor(request.color());
        String iconKey = request.iconKey() == null ? category.getIconKey() : request.iconKey();
        validateName(name);
        validateIconKey(iconKey);
        validateColorAvailable(userId, color, categoryId);
        category.update(name.trim(), color, iconKey.trim());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        getUserForUpdate(userId);
        Category category = getActiveCategory(userId, categoryId);
        if (taskRepository.existsByCategoryIdAndUserId(categoryId, userId)) {
            throw new CustomException(ErrorCode.CATEGORY_IN_USE);
        }
        category.delete();
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableColors(Long userId) {
        return getRecommendedColors(userId);
    }

    private List<String> getRecommendedColors(Long userId) {
        Set<String> usedColors = getActiveCategories(userId).stream()
                .map(Category::getColor)
                .collect(Collectors.toSet());

        return CATEGORY_COLORS.stream()
                .filter(color -> !usedColors.contains(color))
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

    private void validateIconKey(String iconKey) {
        if (iconKey == null || iconKey.isBlank() || iconKey.length() > 100) {
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
        String uppercaseColor = normalizedColor.toUpperCase(Locale.ROOT);
        if (!CATEGORY_COLORS.contains(uppercaseColor)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return uppercaseColor;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private User getUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private record DefaultCategory(String name, String color, String iconKey, int sortOrder) {
    }
}

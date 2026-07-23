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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final int GENERATED_COLOR_CANDIDATE_LIMIT = 360;

    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("학업", "#5C9EFF", "book", 0),
            new DefaultCategory("일상", "#5CFF8A", "daily", 1),
            new DefaultCategory("기념일", "#FFB85C", "calendar", 2)
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
    public List<String> getAvailableColors(Long userId, Long editingCategoryId) {
        Category editingCategory = editingCategoryId == null ? null : getActiveCategory(userId, editingCategoryId);
        return getRecommendedColors(userId, editingCategory);
    }

    private List<String> getRecommendedColors(Long userId, Category editingCategory) {
        List<String> suggestedColors = new ArrayList<>(DEFAULT_CATEGORIES.stream()
                .map(DefaultCategory::color)
                .distinct()
                .toList());
        if (editingCategory != null && !suggestedColors.contains(editingCategory.getColor())) {
            suggestedColors.add(0, editingCategory.getColor());
        }
        return toAvailableColors(userId, editingCategory, suggestedColors);
    }

    private List<String> toAvailableColors(Long userId, Category editingCategory, List<String> suggestedColors) {
        List<String> availableColors = suggestedColors.stream()
                .filter(color -> editingCategory != null && color.equals(editingCategory.getColor())
                        || !categoryRepository.existsByUserIdAndColorAndDeletedFalse(userId, color))
                .toList();
        if (availableColors.isEmpty()) {
            return List.of(generateUnusedColor(userId));
        }
        return availableColors;
    }

    private String generateUnusedColor(Long userId) {
        for (int index = 0; index < GENERATED_COLOR_CANDIDATE_LIMIT; index++) {
            String color = generateColorCandidate(index);
            if (!categoryRepository.existsByUserIdAndColorAndDeletedFalse(userId, color)) {
                return color;
            }
        }
        throw new CustomException(ErrorCode.INVALID_INPUT);
    }

    private String generateColorCandidate(int index) {
        double hue = (210 + index * 137.508) % 360;
        return hslToHex(hue, 0.62, 0.62);
    }

    private String hslToHex(double hue, double saturation, double lightness) {
        double c = (1 - Math.abs(2 * lightness - 1)) * saturation;
        double h = hue / 60;
        double x = c * (1 - Math.abs(h % 2 - 1));
        double r = 0;
        double g = 0;
        double b = 0;
        if (h < 1) {
            r = c;
            g = x;
        } else if (h < 2) {
            r = x;
            g = c;
        } else if (h < 3) {
            g = c;
            b = x;
        } else if (h < 4) {
            g = x;
            b = c;
        } else if (h < 5) {
            r = x;
            b = c;
        } else {
            r = c;
            b = x;
        }
        double m = lightness - c / 2;
        return String.format(
                Locale.ROOT,
                "#%02X%02X%02X",
                Math.round((r + m) * 255),
                Math.round((g + m) * 255),
                Math.round((b + m) * 255)
        );
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
        return normalizedColor.toUpperCase(Locale.ROOT);
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

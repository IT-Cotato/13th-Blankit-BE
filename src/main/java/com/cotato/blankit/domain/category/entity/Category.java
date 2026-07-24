package com.cotato.blankit.domain.category.entity;

import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "category",
        indexes = {
                @Index(name = "idx_category_user_active_sort", columnList = "user_id,is_deleted,sort_order"),
                @Index(name = "idx_category_user_color_active", columnList = "user_id,color,is_deleted")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String color;

    @Column(name = "icon_key", nullable = false, length = 100)
    private String iconKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_default", nullable = false)
    private boolean defaultCategory;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    public static Category create(User user, String name, String color, String iconKey) {
        return create(user, name, color, iconKey, 0, false);
    }

    public static Category create(
            User user,
            String name,
            String color,
            String iconKey,
            int sortOrder,
            boolean defaultCategory
    ) {
        Category category = new Category();
        category.user = user;
        category.name = name;
        category.color = color;
        category.iconKey = iconKey;
        category.sortOrder = sortOrder;
        category.defaultCategory = defaultCategory;
        category.deleted = false;
        return category;
    }

    public void update(String name, String color, String iconKey) {
        this.name = name;
        this.color = color;
        this.iconKey = iconKey;
    }

    public void delete() {
        this.deleted = true;
    }

    public boolean isDeleted() {
        return deleted;
    }
}

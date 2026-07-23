package com.cotato.blankit.domain.search.entity;

import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "search_history",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_search_history_user_keyword", columnNames = {"user_id", "keyword"})
        },
        indexes = {
                @Index(name = "idx_search_history_user_updated", columnList = "user_id, updated_at")
        }
)
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)"))
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long searchHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String keyword;

    private SearchHistory(User user, String keyword) {
        this.user = user;
        this.keyword = keyword;
    }

    public static SearchHistory create(User user, String keyword) {
        return new SearchHistory(user, keyword);
    }
}

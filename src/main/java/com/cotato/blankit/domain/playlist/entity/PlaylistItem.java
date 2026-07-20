package com.cotato.blankit.domain.playlist.entity;

import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "playlist_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_playlist_item", columnNames = {"playlist_id", "task_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long playlistItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false)
    private int sortOrder;

    @Column(length = 30)
    private String sourceMode;

    public static PlaylistItem create(Playlist playlist, Task task, int sortOrder, String sourceMode) {
        PlaylistItem item = new PlaylistItem();
        item.playlist = playlist;
        item.task = task;
        item.sortOrder = sortOrder;
        item.sourceMode = sourceMode;
        return item;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}

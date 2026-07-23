package com.cotato.blankit.domain.playlist.repository;

import com.cotato.blankit.domain.playlist.entity.Playlist;
import com.cotato.blankit.domain.playlist.entity.PlaylistItem;
import com.cotato.blankit.domain.task.entity.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, Long> {

    @EntityGraph(attributePaths = {"task", "task.category"})
    List<PlaylistItem> findByPlaylistOrderBySortOrder(Playlist playlist);

    @EntityGraph(attributePaths = {"task", "task.category"})
    List<PlaylistItem> findByPlaylistAndSourceModeOrderBySortOrder(Playlist playlist, String sourceMode);

    boolean existsByPlaylistAndTask(Playlist playlist, Task task);

    Optional<PlaylistItem> findByPlaylistAndTask(Playlist playlist, Task task);

    void deleteByPlaylistAndSourceMode(Playlist playlist, String sourceMode);

    void deleteByPlaylist(Playlist playlist);

    void deleteByTask(Task task);

    int countByPlaylist(Playlist playlist);
}

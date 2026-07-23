package com.cotato.blankit.domain.playlist.service;

import com.cotato.blankit.domain.playlist.dto.request.PlaylistItemAddRequest;
import com.cotato.blankit.domain.playlist.dto.request.PlaylistItemOrderUpdateRequest;
import com.cotato.blankit.domain.playlist.dto.response.PlaylistResponse;
import com.cotato.blankit.domain.playlist.entity.Playlist;
import com.cotato.blankit.domain.playlist.entity.PlaylistItem;
import com.cotato.blankit.domain.playlist.repository.PlaylistItemRepository;
import com.cotato.blankit.domain.playlist.repository.PlaylistRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cotato.blankit.domain.task.entity.TaskStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional
    public PlaylistResponse getPlaylist(Long userId, String sourceMode) {
        Playlist playlist = findOrCreatePlaylist(getUser(userId));
        int totalCount = playlistItemRepository.countByPlaylist(playlist);
        List<PlaylistItem> items = sourceMode != null
                ? playlistItemRepository.findByPlaylistAndSourceModeOrderBySortOrder(playlist, sourceMode)
                : playlistItemRepository.findByPlaylistOrderBySortOrder(playlist);
        return toResponse(playlist, items, totalCount);
    }

    @Transactional
    public PlaylistResponse addItems(Long userId, PlaylistItemAddRequest request) {
        Playlist playlist = findOrCreatePlaylist(getUser(userId));

        Map<Long, Task> taskMap = taskRepository.findAllByIdInAndUserId(request.taskIds(), userId)
                .stream()
                .collect(Collectors.toMap(Task::getId, t -> t));

        List<PlaylistItem> existingItems = playlistItemRepository.findByPlaylistOrderBySortOrder(playlist);
        Set<Long> existingTaskIds = existingItems.stream()
                .map(item -> item.getTask().getId())
                .collect(Collectors.toSet());

        int nextSortOrder = existingItems.size();

        for (Long taskId : request.taskIds()) {
            Task task = taskMap.get(taskId);
            if (task == null) {
                throw new CustomException(ErrorCode.TASK_NOT_FOUND);
            }
            if (task.getStatus() == TaskStatus.DONE) {
                continue;
            }
            if (!existingTaskIds.contains(taskId)) {
                playlistItemRepository.save(PlaylistItem.create(playlist, task, nextSortOrder, request.sourceMode()));
                existingTaskIds.add(taskId);
                nextSortOrder++;
            }
        }

        List<PlaylistItem> items = playlistItemRepository.findByPlaylistOrderBySortOrder(playlist);
        return toResponse(playlist, items, items.size());
    }

    @Transactional
    public void updateOrder(Long userId, PlaylistItemOrderUpdateRequest request) {
        Playlist playlist = findOrCreatePlaylist(getUser(userId));

        for (PlaylistItemOrderUpdateRequest.PlaylistItemOrder order : request.items()) {
            PlaylistItem item = playlistItemRepository.findById(order.playlistItemId())
                    .orElseThrow(() -> new CustomException(ErrorCode.PLAYLIST_ITEM_NOT_FOUND));
            if (!item.getPlaylist().getPlaylistId().equals(playlist.getPlaylistId())) {
                throw new CustomException(ErrorCode.PLAYLIST_ITEM_NOT_FOUND);
            }
            item.updateSortOrder(order.sortOrder());
        }
    }

    @Transactional
    public void deleteItem(Long userId, Long playlistItemId) {
        Playlist playlist = findOrCreatePlaylist(getUser(userId));
        PlaylistItem item = playlistItemRepository.findById(playlistItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYLIST_ITEM_NOT_FOUND));
        if (!item.getPlaylist().getPlaylistId().equals(playlist.getPlaylistId())) {
            throw new CustomException(ErrorCode.PLAYLIST_ITEM_NOT_FOUND);
        }
        playlistItemRepository.delete(item);
    }

    @Transactional
    public void deleteItems(Long userId, String sourceMode) {
        Playlist playlist = findOrCreatePlaylist(getUser(userId));
        if (sourceMode != null) {
            playlistItemRepository.deleteByPlaylistAndSourceMode(playlist, sourceMode);
        } else {
            playlistItemRepository.deleteByPlaylist(playlist);
        }
    }

    private Playlist findOrCreatePlaylist(User user) {
        return playlistRepository.findByUser(user)
                .orElseGet(() -> playlistRepository.save(Playlist.create(user)));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private PlaylistResponse toResponse(Playlist playlist, List<PlaylistItem> items, int totalCount) {
        List<PlaylistResponse.PlaylistItemResponse> itemResponses = items.stream()
                .map(item -> new PlaylistResponse.PlaylistItemResponse(
                        item.getPlaylistItemId(),
                        item.getTask().getId(),
                        item.getTask().getTitle(),
                        item.getTask().getCategory().getName(),
                        item.getTask().getCategory().getColor(),
                        item.getSortOrder(),
                        item.getSourceMode()
                ))
                .toList();
        return new PlaylistResponse(playlist.getPlaylistId(), totalCount, itemResponses);
    }
}

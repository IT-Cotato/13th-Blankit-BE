package com.cotato.blankit.domain.playlist;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.playlist.entity.Playlist;
import com.cotato.blankit.domain.playlist.entity.PlaylistItem;
import com.cotato.blankit.domain.playlist.repository.PlaylistItemRepository;
import com.cotato.blankit.domain.playlist.repository.PlaylistRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:playlist-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class PlaylistControllerTest {

    private MockMvc mockMvc;
    private User user;
    private String token;
    private Task taskA;
    private Task taskB;
    private Task taskC;

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private PlaylistRepository playlistRepository;
    @Autowired private PlaylistItemRepository playlistItemRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        user = userRepository.save(User.create(SocialProvider.KAKAO, "playlist-user", "playlist@example.com", "플레이리스트유저", null, 120));
        token = jwtTokenProvider.createAccessToken(user.getId());
        Category category = categoryRepository.save(Category.create(user, "학업", "#5C9EFF", 0, true));
        taskA = taskRepository.save(Task.create(user, category, "과업A", LocalDate.of(2026, 7, 31), null));
        taskB = taskRepository.save(Task.create(user, category, "과업B", LocalDate.of(2026, 7, 31), null));
        taskC = taskRepository.save(Task.create(user, category, "과업C", LocalDate.of(2026, 7, 31), null));
    }

    // ─── 플레이리스트 조회 ────────────────────────────────────────────────────────

    @Test
    void getPlaylist_emptyPlaylist_returnsEmptyList() throws Exception {
        // 플레이리스트에 항목이 없을 때 totalCount=0, items=[] 반환
        mockMvc.perform(get("/api/playlist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void getPlaylist_sourceModeFilter_returnsOnlyMatchingItems() throws Exception {
        // sourceMode=FIRE 필터 시 FIRE 항목만 반환, totalCount는 전체 항목 수(필터 전)
        Playlist playlist = playlistRepository.save(Playlist.create(user));
        playlistItemRepository.save(PlaylistItem.create(playlist, taskA, 0, "FIRE"));
        playlistItemRepository.save(PlaylistItem.create(playlist, taskB, 1, "BALANCE"));

        mockMvc.perform(get("/api/playlist")
                        .header("Authorization", "Bearer " + token)
                        .param("sourceMode", "FIRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("과업A"));
    }

    // ─── 과업 추가 ────────────────────────────────────────────────────────────────

    @Test
    void addItems_singleTask_addsToPlaylist() throws Exception {
        // 단건 추가 시 sortOrder=0으로 저장되고 totalCount=1
        mockMvc.perform(post("/api/playlist/items")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "taskIds": [%d], "sourceMode": null }
                                """.formatted(taskA.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].taskId").value(taskA.getId()))
                .andExpect(jsonPath("$.data.items[0].sortOrder").value(0));
    }

    @Test
    void addItems_duplicateTask_notAddedAgain() throws Exception {
        // 이미 추가된 과업을 다시 추가해도 totalCount가 증가하지 않음
        mockMvc.perform(post("/api/playlist/items")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "taskIds": [%d], "sourceMode": null }
                                """.formatted(taskA.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/playlist/items")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "taskIds": [%d], "sourceMode": null }
                                """.formatted(taskA.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    void addItems_multipleTasks_sortOrderAssignedInOrder() throws Exception {
        // 여러 과업 일괄 추가 시 요청 순서대로 sortOrder 0, 1, 2 배정
        mockMvc.perform(post("/api/playlist/items")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "taskIds": [%d, %d, %d], "sourceMode": "FIRE" }
                                """.formatted(taskA.getId(), taskB.getId(), taskC.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.items[1].sortOrder").value(1))
                .andExpect(jsonPath("$.data.items[2].sortOrder").value(2));
    }

    // ─── 순서 변경 ─────────────────────────────────────────────────────────────

    @Test
    void updateOrder_updatesItemSortOrders() throws Exception {
        // 드래그 핸들로 순서 변경 시 각 항목의 sortOrder가 요청값으로 업데이트됨
        Playlist playlist = playlistRepository.save(Playlist.create(user));
        PlaylistItem itemA = playlistItemRepository.save(PlaylistItem.create(playlist, taskA, 0, null));
        PlaylistItem itemB = playlistItemRepository.save(PlaylistItem.create(playlist, taskB, 1, null));

        mockMvc.perform(patch("/api/playlist/items/order")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [
                                    { "playlistItemId": %d, "sortOrder": 1 },
                                    { "playlistItemId": %d, "sortOrder": 0 }
                                ] }
                                """.formatted(itemA.getPlaylistItemId(), itemB.getPlaylistItemId())))
                .andExpect(status().isOk());

        // DB에서 직접 조회하여 sortOrder 변경 확인
        assertThat(playlistItemRepository.findById(itemA.getPlaylistItemId()).orElseThrow().getSortOrder()).isEqualTo(1);
        assertThat(playlistItemRepository.findById(itemB.getPlaylistItemId()).orElseThrow().getSortOrder()).isEqualTo(0);
    }

    // ─── 단건 삭제 ─────────────────────────────────────────────────────────────

    @Test
    void deleteItem_removesOnlyTargetItem() throws Exception {
        // 단건 삭제 시 대상 항목만 제거되고 나머지 항목은 유지됨
        Playlist playlist = playlistRepository.save(Playlist.create(user));
        PlaylistItem itemA = playlistItemRepository.save(PlaylistItem.create(playlist, taskA, 0, null));
        playlistItemRepository.save(PlaylistItem.create(playlist, taskB, 1, null));

        mockMvc.perform(delete("/api/playlist/items/{playlistItemId}", itemA.getPlaylistItemId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(playlistItemRepository.findById(itemA.getPlaylistItemId())).isEmpty();
        assertThat(playlistItemRepository.countByPlaylist(playlist)).isEqualTo(1);
    }

    @Test
    void deleteItem_otherUserItem_returns404() throws Exception {
        // 다른 사용자의 플레이리스트 항목 삭제 시 PLAYLIST_ITEM_NOT_FOUND(404)
        User other = userRepository.save(User.create(SocialProvider.KAKAO, "other-pl", "other-pl@example.com", "타인", null, 120));
        Category otherCategory = categoryRepository.save(Category.create(other, "학업", "#5C9EFF", 0, true));
        Task otherTask = taskRepository.save(Task.create(other, otherCategory, "타인과업", LocalDate.of(2026, 7, 31), null));
        Playlist otherPlaylist = playlistRepository.save(Playlist.create(other));
        PlaylistItem otherItem = playlistItemRepository.save(PlaylistItem.create(otherPlaylist, otherTask, 0, null));

        mockMvc.perform(delete("/api/playlist/items/{playlistItemId}", otherItem.getPlaylistItemId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYLIST_ITEM_NOT_FOUND"));
    }

    // ─── 일괄 삭제 ─────────────────────────────────────────────────────────────

    @Test
    void deleteItems_withSourceMode_deletesOnlyMatchingMode() throws Exception {
        // sourceMode=FIRE 일괄 삭제 시 FIRE 항목만 제거되고 BALANCE 항목은 유지됨
        Playlist playlist = playlistRepository.save(Playlist.create(user));
        playlistItemRepository.save(PlaylistItem.create(playlist, taskA, 0, "FIRE"));
        playlistItemRepository.save(PlaylistItem.create(playlist, taskB, 1, "BALANCE"));
        playlistItemRepository.save(PlaylistItem.create(playlist, taskC, 2, "FIRE"));

        mockMvc.perform(delete("/api/playlist/items")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .param("sourceMode", "FIRE"))
                .andExpect(status().isOk());

        assertThat(playlistItemRepository.countByPlaylist(playlist)).isEqualTo(1);
    }

    @Test
    void deleteItems_noSourceMode_deletesAll() throws Exception {
        // sourceMode 파라미터 없이 일괄 삭제 시 전체 항목 삭제
        Playlist playlist = playlistRepository.save(Playlist.create(user));
        playlistItemRepository.save(PlaylistItem.create(playlist, taskA, 0, "FIRE"));
        playlistItemRepository.save(PlaylistItem.create(playlist, taskB, 1, null));

        mockMvc.perform(delete("/api/playlist/items")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(playlistItemRepository.countByPlaylist(playlist)).isEqualTo(0);
    }
}

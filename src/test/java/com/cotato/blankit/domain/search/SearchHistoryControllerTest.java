package com.cotato.blankit.domain.search;

import com.cotato.blankit.domain.search.repository.SearchHistoryRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:search-history-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class SearchHistoryControllerTest {

    private MockMvc mockMvc;
    private User user;
    private User otherUser;
    private String token;
    private String otherToken;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SearchHistoryRepository searchHistoryRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        searchHistoryRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(User.create(SocialProvider.KAKAO, "history-user", "history@example.com", "히스토리", null, 120));
        otherUser = userRepository.save(User.create(SocialProvider.KAKAO, "other-history-user", "other-history@example.com", "다른사용자", null, 120));
        token = jwtTokenProvider.createAccessToken(user.getId());
        otherToken = jwtTokenProvider.createAccessToken(otherUser.getId());
    }

    @Test
    void searchApiSavesAndRefreshesSearchHistory() throws Exception {
        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", " 수학 "))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(searchHistoryRepository.findAll()).hasSize(1);
        var firstHistory = searchHistoryRepository.findByUserIdAndKeyword(user.getId(), "수학").orElseThrow();
        var firstSearchedAt = firstHistory.getUpdatedAt();

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "없는검색어"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0));

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "수학"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(searchHistoryRepository.findAll()).hasSize(2);
        var refreshedHistory = searchHistoryRepository.findByUserIdAndKeyword(user.getId(), "수학").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(refreshedHistory.getSearchHistoryId()).isEqualTo(firstHistory.getSearchHistoryId());
        org.assertj.core.api.Assertions.assertThat(refreshedHistory.getUpdatedAt()).isAfterOrEqualTo(firstSearchedAt);

        mockMvc.perform(get("/api/search-histories")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].keyword").value("수학"))
                .andExpect(jsonPath("$.data[1].keyword").value("없는검색어"));
    }

    @Test
    void searchHistoryIsUserScopedAndSupportsPagination() throws Exception {
        saveHistory(token, "수학");
        saveHistory(token, "영어");
        saveHistory(otherToken, "수학");

        mockMvc.perform(get("/api/search-histories")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].keyword").value("영어"));

        mockMvc.perform(get("/api/search-histories")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].keyword").value("수학"));
    }

    @Test
    void deleteSearchHistoryDeletesOnlyOwnHistory() throws Exception {
        saveHistory(token, "수학");
        saveHistory(otherToken, "영어");

        var ownHistory = searchHistoryRepository.findByUserIdAndKeyword(user.getId(), "수학").orElseThrow();
        var otherHistory = searchHistoryRepository.findByUserIdAndKeyword(otherUser.getId(), "영어").orElseThrow();

        mockMvc.perform(delete("/api/search-histories/{searchHistoryId}", otherHistory.getSearchHistoryId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEARCH_HISTORY_NOT_FOUND"));

        org.assertj.core.api.Assertions.assertThat(searchHistoryRepository.findById(otherHistory.getSearchHistoryId())).isPresent();

        mockMvc.perform(delete("/api/search-histories/{searchHistoryId}", ownHistory.getSearchHistoryId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        org.assertj.core.api.Assertions.assertThat(searchHistoryRepository.findById(ownHistory.getSearchHistoryId())).isEmpty();
        org.assertj.core.api.Assertions.assertThat(searchHistoryRepository.findById(otherHistory.getSearchHistoryId())).isPresent();

        mockMvc.perform(delete("/api/search-histories/{searchHistoryId}", ownHistory.getSearchHistoryId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEARCH_HISTORY_NOT_FOUND"));
    }

    @Test
    void deleteAllSearchHistoriesDeletesOnlyOwnHistories() throws Exception {
        saveHistory(token, "수학");
        saveHistory(token, "영어");
        saveHistory(otherToken, "수학");

        mockMvc.perform(delete("/api/search-histories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/search-histories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/search-histories")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].keyword").value("수학"));
    }

    @Test
    void searchHistoryApisAreIncludedInSwaggerDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs/1. 구현 완료"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/search-histories']").exists())
                .andExpect(jsonPath("$.paths['/api/search-histories'].get").exists())
                .andExpect(jsonPath("$.paths['/api/search-histories'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/search-histories/{searchHistoryId}'].delete").exists());
    }

    private void saveHistory(String accessToken, String keyword) throws Exception {
        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("keyword", keyword))
                .andExpect(status().isOk());
    }
}

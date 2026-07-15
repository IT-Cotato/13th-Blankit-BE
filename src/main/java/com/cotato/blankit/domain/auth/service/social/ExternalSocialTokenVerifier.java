package com.cotato.blankit.domain.auth.service.social;

import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("!test & !local")
@RequiredArgsConstructor
public class ExternalSocialTokenVerifier implements SocialTokenVerifier {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    @Override
    public void verify(SocialProvider socialProvider, String socialToken, String expectedSocialId) {
        String verifiedSocialId = switch (socialProvider) {
            case KAKAO -> verifyKakao(socialToken);
            case GOOGLE -> verifyGoogle(socialToken);
        };

        if (!expectedSocialId.equals(verifiedSocialId)) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    private String verifyKakao(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://kapi.kakao.com/v2/user/me"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        JsonNode response = send(request);
        JsonNode id = response.get("id");
        if (id == null || id.isNull()) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        return id.asText();
    }

    private String verifyGoogle(String idToken) {
        String encodedToken = URLEncoder.encode(idToken, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + encodedToken))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        JsonNode response = send(request);
        JsonNode subject = response.get("sub");
        if (subject == null || subject.isNull()) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        return subject.asText();
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
            }
            return objectMapper.readTree(response.body());
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, e);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, e);
        }
    }
}

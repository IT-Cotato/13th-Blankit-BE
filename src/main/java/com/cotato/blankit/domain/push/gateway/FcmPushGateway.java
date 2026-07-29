package com.cotato.blankit.domain.push.gateway;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "blankit.firebase", name = "enabled", havingValue = "true")
public class FcmPushGateway implements PushGateway {
    private static final int BATCH_SIZE = 500;
    private final FirebaseMessaging firebaseMessaging;

    @Override
    public PushDeliveryResult send(List<String> installationIds, PushPayload payload) {
        List<PushDeliveryResult.Item> results = new ArrayList<>(installationIds.size());
        for (int start = 0; start < installationIds.size(); start += BATCH_SIZE) {
            List<String> batch = installationIds.subList(start, Math.min(start + BATCH_SIZE, installationIds.size()));
            MulticastMessage message = MulticastMessage.builder()
                    .addAllFids(batch)
                    .setNotification(Notification.builder().setTitle(payload.title()).setBody(payload.body()).build())
                    .putAllData(payload.data())
                    .build();
            try {
                BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
                for (int i = 0; i < batch.size(); i++) {
                    SendResponse item = response.getResponses().get(i);
                    results.add(item.isSuccessful()
                            ? PushDeliveryResult.Item.success(batch.get(i))
                            : failure(batch.get(i), item.getException()));
                }
            } catch (FirebaseMessagingException exception) {
                for (String fid : batch) results.add(failure(fid, exception));
            }
        }
        return new PushDeliveryResult(results);
    }

    private PushDeliveryResult.Item failure(String fid, FirebaseMessagingException exception) {
        MessagingErrorCode errorCode = exception.getMessagingErrorCode();
        String code = errorCode != null ? errorCode.name()
                : exception.getErrorCode() != null ? exception.getErrorCode().name() : "UNKNOWN";
        PushErrorType type = switch (code) {
            case "UNREGISTERED", "INVALID_ARGUMENT" -> PushErrorType.PERMANENT_TARGET;
            case "UNAVAILABLE", "INTERNAL", "QUOTA_EXCEEDED" -> PushErrorType.RETRYABLE;
            case "SENDER_ID_MISMATCH", "THIRD_PARTY_AUTH_ERROR", "UNAUTHENTICATED",
                    "PERMISSION_DENIED" -> PushErrorType.CONFIGURATION;
            default -> PushErrorType.UNKNOWN;
        };
        return PushDeliveryResult.Item.failure(fid, code, type);
    }
}

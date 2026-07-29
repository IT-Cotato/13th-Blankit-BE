package com.cotato.blankit.domain.push.gateway;

import java.util.List;

public record PushDeliveryResult(List<Item> items) {
    public record Item(String installationId, boolean success, String errorCode, PushErrorType errorType) {
        public static Item success(String fid) { return new Item(fid, true, null, null); }
        public static Item failure(String fid, String code, PushErrorType type) {
            return new Item(fid, false, code, type);
        }
    }
}

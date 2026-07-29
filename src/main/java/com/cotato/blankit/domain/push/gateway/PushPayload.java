package com.cotato.blankit.domain.push.gateway;

import java.util.LinkedHashMap;
import java.util.Map;

public record PushPayload(String type, String title, String body, String referenceId,
                          String clickUrl, Map<String, String> additionalData) {
    public Map<String, String> data() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("type", type);
        if (referenceId != null) values.put("referenceId", referenceId);
        if (clickUrl != null) values.put("clickUrl", clickUrl);
        if (additionalData != null) values.putAll(additionalData);
        return values;
    }
}

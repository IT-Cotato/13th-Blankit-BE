package com.cotato.blankit.domain.notification.push.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

final class FidListCodec {

    private FidListCodec() {
    }

    static String encode(List<String> fids) {
        if (fids == null || fids.isEmpty()) {
            return null;
        }
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return fids.stream()
                .map(fid -> encoder.encodeToString(fid.getBytes(StandardCharsets.UTF_8)))
                .collect(java.util.stream.Collectors.joining(","));
    }

    static List<String> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        Base64.Decoder decoder = Base64.getUrlDecoder();
        return java.util.Arrays.stream(encoded.split(","))
                .map(value -> new String(decoder.decode(value), StandardCharsets.UTF_8))
                .toList();
    }
}

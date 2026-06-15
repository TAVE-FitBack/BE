package com.fitback.core.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fitback.core.application.port.TenantDataRepository;
import com.fitback.global.security.WebhookSignatureException;

@Service
public class DeliveryCallbackService {
    private final TenantDataRepository store;
    private final String secret;

    public DeliveryCallbackService(TenantDataRepository store, @Value("${webhook.secret:${WEBHOOK_SECRET:}}") String secret) {
        this.store = store;
        this.secret = secret;
    }

    public Map<String, Object> apply(String signature, Map<String, Object> body) {
        if (secret.isBlank() || signature == null || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookSignatureException();
        }
        return store.updateAcrossTenants("messages", String.valueOf(body.get("messageId")), body);
    }
}

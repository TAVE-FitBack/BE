package com.fitback.global.security;

public class WebhookSignatureException extends RuntimeException {
    public WebhookSignatureException() {
        super("invalid webhook signature");
    }
}

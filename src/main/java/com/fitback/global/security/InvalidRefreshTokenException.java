package com.fitback.global.security;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("invalid refresh token");
    }
}

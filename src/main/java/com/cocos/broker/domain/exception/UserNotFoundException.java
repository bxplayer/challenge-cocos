package com.cocos.broker.domain.exception;

/**
 * El usuario indicado no existe.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long userId) {
        super("User not found: " + userId);
    }
}

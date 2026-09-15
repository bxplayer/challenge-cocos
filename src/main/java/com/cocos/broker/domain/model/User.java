package com.cocos.broker.domain.model;

public record User(
        Long id,
        String email,
        String accountNumber
) {
}

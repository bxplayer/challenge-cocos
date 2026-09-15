package com.cocos.broker.domain.exception;

/**
 * La orden indicada no existe.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Order not found: " + orderId);
    }
}

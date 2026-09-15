package com.cocos.broker.domain.exception;

import com.cocos.broker.domain.OrderStatus;

/**
 * Solo pueden cancelarse órdenes en estado NEW.
 */
public class OrderNotCancellableException extends RuntimeException {

    public OrderNotCancellableException(Long orderId, OrderStatus status) {
        super("Order " + orderId + " cannot be cancelled (status " + status + "); only NEW orders can be cancelled");
    }
}

package com.cocos.broker.domain.port;

import com.cocos.broker.domain.model.Order;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para persistencia de órdenes.
 */
public interface OrderRepository {

    Optional<Order> findById(Long id);

    /** Todas las órdenes del usuario (base para calcular tenencia y cash). */
    List<Order> findByUserId(Long userId);

    Order save(Order order);
}

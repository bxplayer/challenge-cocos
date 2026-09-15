package com.cocos.broker.application;

import com.cocos.broker.domain.OrderStatus;
import com.cocos.broker.domain.exception.OrderNotCancellableException;
import com.cocos.broker.domain.exception.OrderNotFoundException;
import com.cocos.broker.domain.model.Order;
import com.cocos.broker.domain.port.OrderRepository;
import com.cocos.broker.domain.port.PortfolioCache;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CancelOrderService {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderService.class);

    private final OrderRepository orderRepository;
    private final PortfolioCache portfolioCache;

    /**
     * Cancela una orden NEW del usuario (NEW → CANCELLED). Al pasar a CANCELLED
     * deja de contar como reserva, liberando automáticamente el cash/acciones
     * comprometidos. Una orden de otro usuario se trata como inexistente (404)
     * para no revelar órdenes ajenas.
     */
    @Transactional
    public Order cancel(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.status() != OrderStatus.NEW) {
            throw new OrderNotCancellableException(orderId, order.status());
        }

        Order saved = orderRepository.save(order.withStatus(OrderStatus.CANCELLED));

        // La cancelación libera la reserva → invalidamos el cache del portfolio.
        portfolioCache.evict(userId);

        log.info("Orden #{} cancelada (NEW -> CANCELLED)", orderId);
        return saved;
    }
}

package com.cocos.broker.domain.port;

import com.cocos.broker.domain.model.Portfolio;

import java.util.Optional;

/**
 * Puerto de salida para el cache-aside del portfolio.
 */
public interface PortfolioCache {

    Optional<Portfolio> get(Long userId);

    void put(Long userId, Portfolio portfolio);

    void evict(Long userId);
}

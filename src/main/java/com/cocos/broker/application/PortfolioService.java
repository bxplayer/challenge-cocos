package com.cocos.broker.application;

import com.cocos.broker.domain.PortfolioCalculator;
import com.cocos.broker.domain.exception.UserNotFoundException;
import com.cocos.broker.domain.model.Instrument;
import com.cocos.broker.domain.model.MarketData;
import com.cocos.broker.domain.model.Order;
import com.cocos.broker.domain.model.Portfolio;
import com.cocos.broker.domain.port.InstrumentRepository;
import com.cocos.broker.domain.port.MarketDataRepository;
import com.cocos.broker.domain.port.OrderRepository;
import com.cocos.broker.domain.port.PortfolioCache;
import com.cocos.broker.domain.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final OrderRepository orderRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataRepository marketDataRepository;
    private final UserRepository userRepository;
    private final PortfolioCache portfolioCache;

    private final PortfolioCalculator calculator = new PortfolioCalculator();

    /**
     * Sin {@code @Transactional} a propósito: son lecturas independientes (las
     * órdenes en una sola query; instrumentos y precios son datos de referencia)
     * y así no se retiene una conexión del pool mientras se consulta el cache.
     */
    public Portfolio getPortfolio(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        Optional<Portfolio> cached = portfolioCache.get(userId);
        if (cached.isPresent()) {
            return cached.get();
        }

        Portfolio portfolio = computePortfolio(userId);
        portfolioCache.put(userId, portfolio);
        return portfolio;
    }

    private Portfolio computePortfolio(Long userId) {
        List<Order> orders = orderRepository.findByUserId(userId);

        Set<Long> instrumentIds = orders.stream()
                .map(Order::instrumentId)
                .collect(Collectors.toSet());

        Map<Long, Instrument> instruments = instrumentRepository.findAllById(instrumentIds).stream()
                .collect(Collectors.toMap(Instrument::id, Function.identity()));

        Map<Long, MarketData> latestMarketData = marketDataRepository.findLatestByInstrumentIds(instrumentIds).stream()
                .collect(Collectors.toMap(MarketData::instrumentId, Function.identity()));

        return calculator.calculate(userId, orders, instruments, latestMarketData);
    }
}

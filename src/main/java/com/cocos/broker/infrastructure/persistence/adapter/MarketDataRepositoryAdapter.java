package com.cocos.broker.infrastructure.persistence.adapter;

import com.cocos.broker.domain.model.MarketData;
import com.cocos.broker.infrastructure.persistence.entity.MarketDataEntity;
import com.cocos.broker.domain.port.MarketDataRepository;
import com.cocos.broker.infrastructure.persistence.repository.MarketDataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MarketDataRepositoryAdapter implements MarketDataRepository {

    private final MarketDataJpaRepository repository;

    @Override
    public Optional<MarketData> findLatestByInstrumentId(Long instrumentId) {
        return repository.findFirstByInstrumentIdOrderByDateDesc(instrumentId).map(MarketDataRepositoryAdapter::toDomain);
    }

    @Override
    public List<MarketData> findLatestByInstrumentIds(Collection<Long> instrumentIds) {
        if (instrumentIds == null || instrumentIds.isEmpty()) {
            return List.of();
        }
        return repository.findLatestByInstrumentIds(instrumentIds).stream().map(MarketDataRepositoryAdapter::toDomain).toList();
    }

    private static MarketData toDomain(MarketDataEntity e) {
        return new MarketData(
                e.getId(), e.getInstrumentId(), e.getHigh(), e.getLow(), e.getOpen(),
                e.getClose(), e.getPreviousClose(), e.getDate());
    }
}

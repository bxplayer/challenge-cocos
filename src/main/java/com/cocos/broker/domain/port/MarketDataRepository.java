package com.cocos.broker.domain.port;

import com.cocos.broker.domain.model.MarketData;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para datos de mercado (precios).
 */
public interface MarketDataRepository {

    /** Último marketdata (por fecha) del instrumento. */
    Optional<MarketData> findLatestByInstrumentId(Long instrumentId);

    /** Último marketdata de cada instrumento del conjunto (para el portfolio). */
    List<MarketData> findLatestByInstrumentIds(Collection<Long> instrumentIds);
}

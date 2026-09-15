package com.cocos.broker.infrastructure.persistence.repository;

import com.cocos.broker.infrastructure.persistence.entity.MarketDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MarketDataJpaRepository extends JpaRepository<MarketDataEntity, Long> {

    Optional<MarketDataEntity> findFirstByInstrumentIdOrderByDateDesc(Long instrumentId);

    /**
     * Último marketdata (por fecha) de cada instrumento del conjunto.
     */
    @Query("""
            SELECT m FROM MarketDataEntity m
            WHERE m.instrumentId IN :ids
              AND m.date = (
                  SELECT MAX(m2.date) FROM MarketDataEntity m2
                  WHERE m2.instrumentId = m.instrumentId
              )
            """)
    List<MarketDataEntity> findLatestByInstrumentIds(@Param("ids") Collection<Long> ids);
}

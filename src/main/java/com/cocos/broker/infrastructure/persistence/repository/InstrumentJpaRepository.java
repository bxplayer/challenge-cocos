package com.cocos.broker.infrastructure.persistence.repository;

import com.cocos.broker.infrastructure.persistence.entity.InstrumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentJpaRepository extends JpaRepository<InstrumentEntity, Long> {

    /**
     * Búsqueda por ticker o nombre (contains, case-insensitive). Se usa @Query en vez de una
     * derived query (findByTickerContainingIgnoreCaseOrNameContainingIgnoreCase) para tener un
     * nombre de método corto e intencional en lugar de codificar la condición en la firma.
     * {@code pattern} ya viene con los comodines y con '%', '_' y '!' escapados (ver adapter).
     */
    @Query("""
            SELECT i FROM InstrumentEntity i
            WHERE LOWER(i.ticker) LIKE LOWER(:pattern) ESCAPE '!'
               OR LOWER(i.name) LIKE LOWER(:pattern) ESCAPE '!'
            """)
    Page<InstrumentEntity> searchByTickerOrName(@Param("pattern") String pattern, Pageable pageable);
}

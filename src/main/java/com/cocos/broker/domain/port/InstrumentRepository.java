package com.cocos.broker.domain.port;

import com.cocos.broker.domain.model.Instrument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para consulta de instrumentos.
 *
 * Nota de diseño: la paginación usa {@link Page}/{@link Pageable} de Spring
 * Data (abstracción de datos, no del framework web). Es una concesión pragmática
 * para no reimplementar paginación/sort; el dominio de modelos y la lógica de
 * negocio permanecen libres de Spring.
 */
public interface InstrumentRepository {

    Optional<Instrument> findById(Long id);

    List<Instrument> findAllById(Collection<Long> ids);

    /**
     * Búsqueda paginada por ticker y/o nombre (contains, case-insensitive).
     * Si {@code query} es nulo o vacío, devuelve todos los instrumentos.
     */
    Page<Instrument> search(String query, Pageable pageable);
}

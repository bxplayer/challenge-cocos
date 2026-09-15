package com.cocos.broker.infrastructure.persistence.adapter;

import com.cocos.broker.domain.model.Instrument;
import com.cocos.broker.domain.port.InstrumentRepository;
import com.cocos.broker.infrastructure.persistence.entity.InstrumentEntity;
import com.cocos.broker.infrastructure.persistence.repository.InstrumentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InstrumentRepositoryAdapter implements InstrumentRepository {

    private final InstrumentJpaRepository repository;

    @Override
    public Optional<Instrument> findById(Long id) {
        return repository.findById(id).map(InstrumentRepositoryAdapter::toDomain);
    }

    @Override
    public List<Instrument> findAllById(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(ids).stream().map(InstrumentRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Page<Instrument> search(String query, Pageable pageable) {
        Page<InstrumentEntity> result = (query == null || query.isBlank())
                ? repository.findAll(pageable)
                : repository.searchByTickerOrName(containsPattern(query), pageable);
        return result.map(InstrumentRepositoryAdapter::toDomain);
    }

    /** Patrón LIKE "contiene", escapando los comodines del texto del usuario. */
    private static String containsPattern(String query) {
        String escaped = query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    static Instrument toDomain(InstrumentEntity e) {
        return new Instrument(e.getId(), e.getTicker(), e.getName(), e.getType());
    }
}

package com.cocos.broker.application;

import com.cocos.broker.domain.model.Instrument;
import com.cocos.broker.domain.port.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    /**
     * Busca instrumentos por ticker y/o nombre (paginado).
     */
    public Page<Instrument> search(String query, Pageable pageable) {
        String normalized = (query == null) ? null : query.trim();
        return instrumentRepository.search(normalized, pageable);
    }
}

package com.cocos.broker.infrastructure.web.dto;

import com.cocos.broker.domain.model.Instrument;

public record InstrumentResponse(
        Long id,
        String ticker,
        String name,
        String type
) {
    public static InstrumentResponse from(Instrument i) {
        return new InstrumentResponse(i.id(), i.ticker(), i.name(), i.type());
    }
}

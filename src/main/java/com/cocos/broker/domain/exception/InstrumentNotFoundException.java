package com.cocos.broker.domain.exception;

/**
 * El instrumento indicado no existe.
 */
public class InstrumentNotFoundException extends RuntimeException {

    public InstrumentNotFoundException(Long instrumentId) {
        super("Instrument not found: " + instrumentId);
    }
}

package com.cocos.broker.domain.port;

import java.time.Duration;
import java.util.Optional;

/**
 * Puerto de salida para idempotencia. Protocolo en dos pasos para que dos
 * reintentos concurrentes con la misma clave no ejecuten ambos el caso de uso:
 * primero se {@link #reserve reserva} la clave (operación atómica), luego se
 * {@link #put guarda} el resultado.
 */
public interface IdempotencyStore {

    /**
     * Reserva la clave de forma atómica. Devuelve {@code true} si este llamador
     * la reservó (debe ejecutar el caso de uso), {@code false} si ya existía.
     */
    boolean reserve(String key, Duration ttl);

    /** Resultado guardado para la clave; vacío si no existe o está reservada sin resultado aún. */
    Optional<String> get(String key);

    void put(String key, String value, Duration ttl);

    /** Libera una clave reservada (p. ej. si el caso de uso falló). */
    void release(String key);
}

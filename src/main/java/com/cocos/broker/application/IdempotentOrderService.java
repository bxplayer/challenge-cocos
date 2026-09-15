package com.cocos.broker.application;

import com.cocos.broker.domain.model.Order;
import com.cocos.broker.domain.port.IdempotencyStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Envoltorio de idempotencia sobre el envío de órdenes. Si se provee una clave
 * de idempotencia ya utilizada por el mismo usuario, devuelve la orden
 * persistida en el primer intento en lugar de volver a ejecutar el caso de
 * uso — evita duplicar órdenes ante reintentos del cliente (p. ej. por
 * timeout de red).
 *
 * <p>La clave se reserva de forma atómica antes de ejecutar el caso de uso, así
 * dos reintentos simultáneos no lo ejecutan ambos: el segundo recibe un 409
 * ({@link ConcurrentRequestException}) y, al reintentar, la orden ya guardada.
 * La clave se scopea por {@code userId} para que un usuario no pueda leer la
 * orden de otro reutilizando su Idempotency-Key.
 *
 * <p>Vive en la capa de aplicación, no en el controller: la idempotencia es una
 * garantía sobre la ejecución del caso de uso, no un detalle del transporte HTTP.
 *
 * <p>Configurable por variable de entorno: {@code IDEMPOTENCY_ENABLED} habilita
 * o deshabilita por completo el mecanismo (default {@code true}) y
 * {@code IDEMPOTENCY_KEY_TTL_SECONDS} controla cuánto tiempo se recuerda cada
 * clave (default 86400s = 24h).
 */
@Service
public class IdempotentOrderService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentOrderService.class);

    private final SendOrderService sendOrderService;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final boolean idempotencyEnabled;
    private final Duration idempotencyKeyTimeToLive;

    public IdempotentOrderService(
            SendOrderService sendOrderService,
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper,
            @Value("${app.idempotency.enabled:true}") boolean idempotencyEnabled,
            @Value("${app.idempotency.order-key-ttl-seconds:86400}") long idempotencyKeyTimeToLiveSeconds) {
        this.sendOrderService = sendOrderService;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.idempotencyEnabled = idempotencyEnabled;
        this.idempotencyKeyTimeToLive = Duration.ofSeconds(idempotencyKeyTimeToLiveSeconds);

        if (!idempotencyEnabled) {
            log.warn("Idempotencia de órdenes deshabilitada (IDEMPOTENCY_ENABLED=false): "
                    + "reintentos con el mismo Idempotency-Key crearán órdenes duplicadas.");
        }
    }

    /**
     * @param idempotencyKey clave enviada por el cliente; {@code null} o vacía procesa sin idempotencia.
     */
    public Order submit(SendOrderCommand cmd, String idempotencyKey) {
        if (!idempotencyEnabled || idempotencyKey == null || idempotencyKey.isBlank()) {
            return sendOrderService.send(cmd);
        }

        String storeKey = "orders:" + cmd.userId() + ":" + idempotencyKey;
        if (!idempotencyStore.reserve(storeKey, idempotencyKeyTimeToLive)) {
            // Clave ya usada: devolvemos la orden original o, si el primer intento
            // todavía está en curso, pedimos reintentar.
            return idempotencyStore.get(storeKey)
                    .map(this::deserializeOrder)
                    .orElseThrow(() -> new ConcurrentRequestException(idempotencyKey));
        }

        Order submittedOrder;
        try {
            submittedOrder = sendOrderService.send(cmd);
        } catch (RuntimeException e) {
            idempotencyStore.release(storeKey);
            throw e;
        }
        idempotencyStore.put(storeKey, serializeOrder(submittedOrder), idempotencyKeyTimeToLive);
        return submittedOrder;
    }

    private String serializeOrder(Order order) {
        try {
            return objectMapper.writeValueAsString(order);
        } catch (Exception serializationException) {
            throw new IllegalStateException(
                    "No se pudo serializar la orden para idempotencia", serializationException);
        }
    }

    private Order deserializeOrder(String serializedOrder) {
        try {
            return objectMapper.readValue(serializedOrder, Order.class);
        } catch (Exception deserializationException) {
            throw new IllegalStateException(
                    "No se pudo deserializar la orden cacheada por idempotencia", deserializationException);
        }
    }
}

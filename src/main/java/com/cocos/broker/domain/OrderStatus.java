package com.cocos.broker.domain;

/**
 * Estado de la orden.
 * <ul>
 *   <li>NEW: orden LIMIT enviada al mercado, aún no ejecutada.</li>
 *   <li>FILLED: orden ejecutada.</li>
 *   <li>REJECTED: rechazada (p. ej. fondos/acciones insuficientes).</li>
 *   <li>CANCELLED: cancelada por el usuario (solo aplica sobre NEW).</li>
 * </ul>
 */
public enum OrderStatus {
    NEW,
    FILLED,
    REJECTED,
    CANCELLED
}

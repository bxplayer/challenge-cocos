package com.cocos.broker.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

/** Solicitud de cancelación: el usuario dueño de la orden (sin auth, viaja en el body). */
public record CancelOrderRequest(@NotNull Long userId) {
}

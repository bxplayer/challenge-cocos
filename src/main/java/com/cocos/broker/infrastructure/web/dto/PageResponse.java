package com.cocos.broker.infrastructure.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltura de respuesta paginada estándar para la API. Usamos un DTO propio
 * (en vez de serializar {@link Page} directamente, cuyo formato está deprecado
 * en Spring Boot 3) para exponer un contrato REST estable.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        List<T> content = page.getContent().stream().map(mapper).toList();
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}

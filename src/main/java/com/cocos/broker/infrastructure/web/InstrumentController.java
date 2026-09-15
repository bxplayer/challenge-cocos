package com.cocos.broker.infrastructure.web;

import com.cocos.broker.application.InstrumentService;
import com.cocos.broker.domain.model.Instrument;
import com.cocos.broker.infrastructure.web.dto.InstrumentResponse;
import com.cocos.broker.infrastructure.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instruments")
@RequiredArgsConstructor
@Tag(name = "Instruments", description = "Búsqueda de instrumentos del mercado")
public class InstrumentController {

    private final InstrumentService instrumentService;

    @GetMapping
    @Operation(summary = "Buscar instrumentos por ticker y/o nombre (paginado)")
    public PageResponse<InstrumentResponse> search(
            @RequestParam(required = false) String query,
            @ParameterObject @PageableDefault(size = 20, sort = "ticker") Pageable pageable) {

        Page<Instrument> result = instrumentService.search(query, pageable);
        return PageResponse.from(result, InstrumentResponse::from);
    }
}

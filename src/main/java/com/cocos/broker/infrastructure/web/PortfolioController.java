package com.cocos.broker.infrastructure.web;

import com.cocos.broker.application.PortfolioService;
import com.cocos.broker.domain.model.Portfolio;
import com.cocos.broker.infrastructure.web.dto.PortfolioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}/portfolio")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "Portfolio del usuario")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    @Operation(summary = "Obtener el portfolio del usuario (valor total, cash disponible y posiciones)")
    public PortfolioResponse getPortfolio(@PathVariable Long userId) {
        Portfolio portfolio = portfolioService.getPortfolio(userId);
        return PortfolioResponse.from(portfolio);
    }
}

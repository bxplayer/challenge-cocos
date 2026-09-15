package com.cocos.broker.domain;

import com.cocos.broker.domain.model.Instrument;
import com.cocos.broker.domain.model.MarketData;
import com.cocos.broker.domain.model.Order;
import com.cocos.broker.domain.model.Portfolio;
import com.cocos.broker.domain.model.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioCalculatorTest {

    private final PortfolioCalculator calculator = new PortfolioCalculator();

    private static final LocalDateTime T = LocalDateTime.parse("2023-07-13T12:00:00");

    private static Order order(long id, long instrumentId, int size, String price,
                               OrderType type, OrderSide side, OrderStatus status) {
        return new Order(id, instrumentId, 1L, size, new BigDecimal(price), type, side, status, T);
    }

    /** Las 11 órdenes semilla del usuario 1. */
    private static List<Order> seedOrders() {
        return List.of(
                order(1, 66, 1_000_000, "1.00", OrderType.MARKET, OrderSide.CASH_IN, OrderStatus.FILLED),
                order(2, 47, 50, "930.00", OrderType.MARKET, OrderSide.BUY, OrderStatus.FILLED),
                order(3, 47, 50, "920.00", OrderType.LIMIT, OrderSide.BUY, OrderStatus.CANCELLED),
                order(4, 47, 10, "940.00", OrderType.MARKET, OrderSide.SELL, OrderStatus.FILLED),
                order(5, 45, 50, "710.00", OrderType.LIMIT, OrderSide.BUY, OrderStatus.NEW),
                order(6, 47, 100, "950.00", OrderType.MARKET, OrderSide.SELL, OrderStatus.REJECTED),
                order(7, 31, 60, "1500.00", OrderType.LIMIT, OrderSide.BUY, OrderStatus.NEW),
                order(8, 66, 100_000, "1.00", OrderType.MARKET, OrderSide.CASH_OUT, OrderStatus.FILLED),
                order(9, 31, 20, "1540.00", OrderType.LIMIT, OrderSide.BUY, OrderStatus.FILLED),
                order(10, 54, 500, "250.00", OrderType.MARKET, OrderSide.BUY, OrderStatus.FILLED),
                order(11, 31, 30, "1530.00", OrderType.MARKET, OrderSide.SELL, OrderStatus.FILLED)
        );
    }

    private static Map<Long, Instrument> seedInstruments() {
        return Map.of(
                47L, new Instrument(47L, "PAMP", "Pampa Holding S.A.", "ACCIONES"),
                31L, new Instrument(31L, "BMA", "Banco Macro S.A.", "ACCIONES"),
                54L, new Instrument(54L, "METR", "MetroGAS S.A.", "ACCIONES"),
                45L, new Instrument(45L, "LOMA", "Loma Negra S.A.", "ACCIONES"),
                66L, new Instrument(66L, "ARS", "PESOS", "MONEDA")
        );
    }

    private static MarketData md(long instrumentId, String close, String prevClose) {
        return new MarketData(1L, instrumentId, null, null, null,
                new BigDecimal(close), new BigDecimal(prevClose), LocalDate.parse("2023-07-14"));
    }

    private static Map<Long, MarketData> seedMarketData() {
        return Map.of(
                47L, md(47, "925.85", "921.80"),
                54L, md(54, "229.50", "232.00"),
                31L, md(31, "1502.80", "1520.25"),
                45L, md(45, "734.35", "696.60")
        );
    }

    @Test
    @DisplayName("Calcula cash disponible descontando reservas de órdenes BUY NEW")
    void availableCash() {
        Portfolio p = calculator.calculate(1L, seedOrders(), seedInstruments(), seedMarketData());

        // Cash total = 1.000.000 - 100.000 + (10*940 + 30*1530) - (50*930 + 20*1540 + 500*250) = 753.000
        // Reservas BUY NEW = 50*710 + 60*1500 = 125.500  →  disponible = 627.500
        assertThat(p.availableCash()).isEqualByComparingTo("627500.00");
    }

    @Test
    @DisplayName("Valor total de la cuenta = cash total + valor de mercado de posiciones")
    void totalAccountValue() {
        Portfolio p = calculator.calculate(1L, seedOrders(), seedInstruments(), seedMarketData());

        // 753.000 (cash total) + 37.034 (PAMP) + 114.750 (METR) = 904.784
        assertThat(p.totalAccountValue()).isEqualByComparingTo("904784.00");
    }

    @Test
    @DisplayName("Solo lista posiciones FILLED con cantidad > 0 (BMA queda excluida por vender más de lo comprado)")
    void positionsOnlyPositiveQuantities() {
        Portfolio p = calculator.calculate(1L, seedOrders(), seedInstruments(), seedMarketData());

        assertThat(p.positions()).extracting(Position::ticker).containsExactly("METR", "PAMP");
    }

    @Test
    @DisplayName("Posición PAMP: cantidad, valor de mercado y rendimientos")
    void pampPosition() {
        Portfolio p = calculator.calculate(1L, seedOrders(), seedInstruments(), seedMarketData());

        Position pamp = p.positions().stream().filter(x -> x.ticker().equals("PAMP")).findFirst().orElseThrow();
        assertThat(pamp.quantity()).isEqualTo(40);
        assertThat(pamp.marketValue()).isEqualByComparingTo("37034.00");   // 925.85 * 40
        assertThat(pamp.totalReturnPercent()).isEqualByComparingTo("-0.45"); // costo 930*40=37200
        assertThat(pamp.dailyReturnPercent()).isEqualByComparingTo("0.44");  // (925.85-921.80)/921.80
    }

    @Test
    @DisplayName("Posición METR: cantidad, valor de mercado y rendimientos")
    void metrPosition() {
        Portfolio p = calculator.calculate(1L, seedOrders(), seedInstruments(), seedMarketData());

        Position metr = p.positions().stream().filter(x -> x.ticker().equals("METR")).findFirst().orElseThrow();
        assertThat(metr.quantity()).isEqualTo(500);
        assertThat(metr.marketValue()).isEqualByComparingTo("114750.00");    // 229.50 * 500
        assertThat(metr.totalReturnPercent()).isEqualByComparingTo("-8.20");  // costo 250*500=125000
        assertThat(metr.dailyReturnPercent()).isEqualByComparingTo("-1.08");  // (229.50-232.00)/232.00
    }

    @Test
    @DisplayName("Órdenes REJECTED y CANCELLED no afectan el cálculo")
    void ignoresRejectedAndCancelled() {
        // Sin la orden 4 (SELL FILLED), PAMP tendría 50 acciones; con ella, 40.
        Portfolio p = calculator.calculate(1L, seedOrders(), seedInstruments(), seedMarketData());
        Position pamp = p.positions().stream().filter(x -> x.ticker().equals("PAMP")).findFirst().orElseThrow();
        assertThat(pamp.quantity()).isEqualTo(40); // la SELL REJECTED (100) fue ignorada
    }

    @Test
    @DisplayName("availableCash coincide con el cash disponible del portfolio")
    void availableCashMatchesPortfolio() {
        assertThat(calculator.availableCash(seedOrders())).isEqualByComparingTo("627500");
    }

    @Test
    @DisplayName("availableShares descuenta las reservas de SELL NEW sobre la tenencia FILLED")
    void availableSharesDiscountsPendingSells() {
        List<Order> orders = new java.util.ArrayList<>(seedOrders());
        orders.add(order(12, 47, 15, "1000.00", OrderType.LIMIT, OrderSide.SELL, OrderStatus.NEW));

        assertThat(calculator.availableShares(seedOrders(), 47L)).isEqualTo(40);
        assertThat(calculator.availableShares(orders, 47L)).isEqualTo(25);
        assertThat(calculator.availableShares(orders, 31L)).isEqualTo(-10); // BMA: vendió más de lo comprado
        assertThat(calculator.availableShares(orders, 999L)).isZero();
    }
}

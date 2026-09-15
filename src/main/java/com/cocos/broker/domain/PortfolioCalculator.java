package com.cocos.broker.domain;

import com.cocos.broker.domain.model.Instrument;
import com.cocos.broker.domain.model.MarketData;
import com.cocos.broker.domain.model.Order;
import com.cocos.broker.domain.model.Portfolio;
import com.cocos.broker.domain.model.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lógica financiera pura (sin dependencias de framework, testeable de forma
 * aislada). Es la única fuente de verdad para derivar cash y tenencia de la
 * tabla de órdenes: la usan tanto el portfolio como la validación de órdenes.
 *
 * <h2>Reglas</h2>
 * <ul>
 *   <li><b>Cash total</b> = Σ CASH_IN − Σ CASH_OUT + Σ(SELL FILLED) − Σ(BUY FILLED),
 *       usando {@code size * price} de cada orden.</li>
 *   <li><b>Cash reservado</b> = Σ(BUY NEW): las órdenes LIMIT de compra pendientes
 *       reservan pesos.</li>
 *   <li><b>Cash disponible</b> = cash total − cash reservado.</li>
 *   <li><b>Posición</b> por instrumento (solo FILLED) = Σ size(BUY) − Σ size(SELL);
 *       se listan las de cantidad &gt; 0.</li>
 *   <li><b>Acciones disponibles</b> para vender = posición − Σ size(SELL NEW):
 *       las órdenes LIMIT de venta pendientes reservan acciones.</li>
 *   <li><b>Valor de mercado</b> = último {@code close} × cantidad.</li>
 *   <li><b>Rendimiento total %</b> = (valorMercado − costo) / costo × 100, con
 *       costo = precio promedio ponderado de las compras × cantidad.</li>
 *   <li><b>Retorno diario %</b> = (close − previousClose) / previousClose × 100.</li>
 *   <li><b>Valor total de la cuenta</b> = cash total + Σ valor de mercado de posiciones
 *       (el cash reservado sigue siendo patrimonio del usuario).</li>
 * </ul>
 */
public class PortfolioCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int PCT_SCALE = 2;
    private static final int DIVISION_SCALE = 10;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Portfolio calculate(Long userId,
                               List<Order> orders,
                               Map<Long, Instrument> instrumentsById,
                               Map<Long, MarketData> latestMarketDataByInstrumentId) {

        Ledger ledger = Ledger.of(orders);

        List<Position> positions = new ArrayList<>();
        BigDecimal positionsMarketValue = BigDecimal.ZERO;

        for (Map.Entry<Long, Holding> entry : ledger.holdings.entrySet()) {
            Long instrumentId = entry.getKey();
            Holding h = entry.getValue();
            int quantity = h.quantity();
            if (quantity <= 0) {
                continue;
            }

            MarketData md = latestMarketDataByInstrumentId.get(instrumentId);
            BigDecimal close = (md != null && md.close() != null) ? md.close() : BigDecimal.ZERO;
            BigDecimal marketValue = close.multiply(BigDecimal.valueOf(quantity)).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            BigDecimal totalReturnPct = totalReturnPercent(h, quantity, marketValue);
            BigDecimal dailyReturnPct = dailyReturnPercent(md, close);

            Instrument instrument = instrumentsById.get(instrumentId);
            positions.add(new Position(
                    instrumentId,
                    instrument != null ? instrument.ticker() : null,
                    instrument != null ? instrument.name() : null,
                    quantity,
                    marketValue,
                    totalReturnPct,
                    dailyReturnPct));
            positionsMarketValue = positionsMarketValue.add(marketValue);
        }

        positions.sort(Comparator.comparing(p -> p.ticker() == null ? "" : p.ticker()));

        BigDecimal totalAccountValue = ledger.totalCash.add(positionsMarketValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal availableCash = ledger.availableCash().setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new Portfolio(userId, totalAccountValue, availableCash, positions);
    }

    /** Pesos disponibles para operar (descuenta las reservas de BUY NEW). */
    public BigDecimal availableCash(List<Order> orders) {
        return Ledger.of(orders).availableCash();
    }

    /** Acciones disponibles para vender de un instrumento (descuenta las reservas de SELL NEW). */
    public int availableShares(List<Order> orders, Long instrumentId) {
        Holding h = Ledger.of(orders).holdings.get(instrumentId);
        return h == null ? 0 : h.availableQuantity();
    }

    private static BigDecimal amountOf(Order order) {
        return order.price().multiply(BigDecimal.valueOf(order.size()));
    }

    private static BigDecimal totalReturnPercent(Holding h, int quantity, BigDecimal marketValue) {
        if (h.buyQuantity == 0) {
            return null;
        }
        BigDecimal avgBuyPrice = h.buyCost.divide(BigDecimal.valueOf(h.buyQuantity), DIVISION_SCALE, RoundingMode.HALF_UP);
        BigDecimal cost = avgBuyPrice.multiply(BigDecimal.valueOf(quantity));
        if (cost.signum() == 0) {
            return null;
        }
        return marketValue.subtract(cost)
                .divide(cost, DIVISION_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal dailyReturnPercent(MarketData md, BigDecimal close) {
        if (md == null || md.previousClose() == null || md.previousClose().signum() == 0) {
            return null;
        }
        return close.subtract(md.previousClose())
                .divide(md.previousClose(), DIVISION_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    /** Cash y tenencias derivados de un recorrido único sobre las órdenes. */
    private static final class Ledger {
        private BigDecimal totalCash = BigDecimal.ZERO;
        private BigDecimal reservedCash = BigDecimal.ZERO;
        private final Map<Long, Holding> holdings = new HashMap<>();

        static Ledger of(List<Order> orders) {
            Ledger ledger = new Ledger();
            for (Order order : orders) {
                ledger.apply(order);
            }
            return ledger;
        }

        private void apply(Order order) {
            BigDecimal amount = amountOf(order);
            if (order.status() == OrderStatus.FILLED) {
                switch (order.side()) {
                    case CASH_IN -> totalCash = totalCash.add(amount);
                    case CASH_OUT -> totalCash = totalCash.subtract(amount);
                    case SELL -> {
                        totalCash = totalCash.add(amount);
                        holding(order).addSell(order.size());
                    }
                    case BUY -> {
                        totalCash = totalCash.subtract(amount);
                        holding(order).addBuy(order.size(), amount);
                    }
                }
            } else if (order.status() == OrderStatus.NEW) {
                // Órdenes LIMIT pendientes: BUY reserva pesos, SELL reserva acciones.
                switch (order.side()) {
                    case BUY -> reservedCash = reservedCash.add(amount);
                    case SELL -> holding(order).reserveSell(order.size());
                    default -> { }
                }
            }
        }

        private Holding holding(Order order) {
            return holdings.computeIfAbsent(order.instrumentId(), k -> new Holding());
        }

        BigDecimal availableCash() {
            return totalCash.subtract(reservedCash);
        }
    }

    /** Acumulador de compras/ventas de un instrumento. */
    private static final class Holding {
        private int buyQuantity;
        private int sellQuantity;
        private int reservedSellQuantity;
        private BigDecimal buyCost = BigDecimal.ZERO;

        void addBuy(int size, BigDecimal amount) {
            buyQuantity += size;
            buyCost = buyCost.add(amount);
        }

        void addSell(int size) {
            sellQuantity += size;
        }

        void reserveSell(int size) {
            reservedSellQuantity += size;
        }

        /** Tenencia efectiva (solo FILLED). */
        int quantity() {
            return buyQuantity - sellQuantity;
        }

        /** Tenencia menos lo comprometido en ventas LIMIT pendientes. */
        int availableQuantity() {
            return quantity() - reservedSellQuantity;
        }
    }
}

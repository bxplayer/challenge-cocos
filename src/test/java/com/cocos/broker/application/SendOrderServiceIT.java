package com.cocos.broker.application;

import com.cocos.broker.domain.exception.InvalidOrderException;
import com.cocos.broker.domain.exception.OrderNotCancellableException;
import com.cocos.broker.domain.exception.OrderNotFoundException;
import com.cocos.broker.domain.model.Order;
import com.cocos.broker.domain.port.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static com.cocos.broker.domain.OrderSide.BUY;
import static com.cocos.broker.domain.OrderSide.SELL;
import static com.cocos.broker.domain.OrderStatus.CANCELLED;
import static com.cocos.broker.domain.OrderStatus.FILLED;
import static com.cocos.broker.domain.OrderStatus.NEW;
import static com.cocos.broker.domain.OrderStatus.REJECTED;
import static com.cocos.broker.domain.OrderType.LIMIT;
import static com.cocos.broker.domain.OrderType.MARKET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test funcional (pedido por el spec) sobre el envío de órdenes, con Postgres y
 * Redis reales vía Testcontainers. El schema semilla se carga con el mismo
 * mecanismo de init que en producción (docker-entrypoint-initdb.d).
 *
 * <p>{@code @Transactional} aísla cada test: las órdenes creadas se revierten al
 * finalizar, dejando el estado semilla intacto para el siguiente.
 */
@SpringBootTest
@Testcontainers
@Transactional
class SendOrderServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/init/01-schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final Instant NOW = Instant.parse("2024-01-15T10:30:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;
    private static final Long ARS = 66L;    // cash, tipo MONEDA
    private static final Long METR = 54L;   // último close 229.50
    private static final Long PAMP = 47L;   // último close 925.85, posición FILLED = 40
    private static final Long MIRG = 5L;     // último close 9163.00

    @Autowired
    private SendOrderService sendOrderService;
    @Autowired
    private CancelOrderService cancelOrderService;
    @Autowired
    private OrderRepository orderRepository;

    private SendOrderCommand cmd(Long instrumentId, com.cocos.broker.domain.OrderSide side,
                                 com.cocos.broker.domain.OrderType type,
                                 Integer size, BigDecimal amount, BigDecimal price) {
        return new SendOrderCommand(USER, instrumentId, side, type, size, amount, price);
    }

    @Test
    @DisplayName("MARKET BUY se ejecuta (FILLED) al último precio de mercado")
    void marketBuyIsFilledAtLastClose() {
        Order order = sendOrderService.send(cmd(METR, BUY, MARKET, 10, null, null));

        assertThat(order.status()).isEqualTo(FILLED);
        assertThat(order.size()).isEqualTo(10);
        assertThat(order.price()).isEqualByComparingTo("229.50");
        assertThat(order.datetime()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(orderRepository.findById(order.id())).isPresent();
    }

    @Test
    @DisplayName("MARKET SELL se ejecuta (FILLED) si hay acciones suficientes")
    void marketSellIsFilled() {
        Order order = sendOrderService.send(cmd(PAMP, SELL, MARKET, 10, null, null));

        assertThat(order.status()).isEqualTo(FILLED);
        assertThat(order.price()).isEqualByComparingTo("925.85");
    }

    @Test
    @DisplayName("LIMIT queda en estado NEW con el precio enviado")
    void limitOrderIsNew() {
        Order order = sendOrderService.send(cmd(METR, BUY, LIMIT, 10, null, new BigDecimal("200")));

        assertThat(order.status()).isEqualTo(NEW);
        assertThat(order.price()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("Orden por monto: calcula la cantidad máxima sin fracciones (floor)")
    void buyByAmountFloorsQuantity() {
        Order order = sendOrderService.send(cmd(METR, BUY, MARKET, null, new BigDecimal("100000"), null));

        assertThat(order.status()).isEqualTo(FILLED);
        assertThat(order.size()).isEqualTo(435); // floor(100000 / 229.50)
    }

    @Test
    @DisplayName("BUY con fondos insuficientes se rechaza (REJECTED) y se persiste")
    void buyWithInsufficientFundsIsRejected() {
        Order order = sendOrderService.send(cmd(MIRG, BUY, MARKET, 1000, null, null));

        assertThat(order.status()).isEqualTo(REJECTED);
        assertThat(orderRepository.findById(order.id())).isPresent();
    }

    @Test
    @DisplayName("SELL con acciones insuficientes se rechaza (REJECTED)")
    void sellWithInsufficientSharesIsRejected() {
        Order order = sendOrderService.send(cmd(PAMP, SELL, MARKET, 1000, null, null));

        assertThat(order.status()).isEqualTo(REJECTED);
    }

    @Test
    @DisplayName("Una LIMIT SELL pendiente reserva acciones: la siguiente venta solo cuenta con el resto")
    void pendingLimitSellReservesShares() {
        // PAMP: posición 40. Reservamos 30 con una LIMIT SELL (queda NEW).
        Order limitSell = sendOrderService.send(cmd(PAMP, SELL, LIMIT, 30, null, new BigDecimal("1000")));
        assertThat(limitSell.status()).isEqualTo(NEW);

        Order rejected = sendOrderService.send(cmd(PAMP, SELL, MARKET, 11, null, null));
        Order filled = sendOrderService.send(cmd(PAMP, SELL, MARKET, 10, null, null));

        assertThat(rejected.status()).isEqualTo(REJECTED);
        assertThat(filled.status()).isEqualTo(FILLED);
    }

    @Test
    @DisplayName("No se puede operar el instrumento ARS (tipo MONEDA)")
    void currencyInstrumentCannotBeTraded() {
        assertThatThrownBy(() -> sendOrderService.send(cmd(ARS, BUY, LIMIT, 1000, null, new BigDecimal("1"))))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("Cancelar una orden NEW la pasa a CANCELLED")
    void cancelNewOrderTransitionsToCancelled() {
        Order created = sendOrderService.send(cmd(METR, BUY, LIMIT, 10, null, new BigDecimal("200")));
        assertThat(created.status()).isEqualTo(NEW);

        Order cancelled = cancelOrderService.cancel(created.id(), USER);

        assertThat(cancelled.status()).isEqualTo(CANCELLED);
    }

    @Test
    @DisplayName("No se puede cancelar una orden FILLED")
    void cancelFilledOrderThrows() {
        // La orden semilla #2 está FILLED.
        assertThatThrownBy(() -> cancelOrderService.cancel(2L, USER))
                .isInstanceOf(OrderNotCancellableException.class);
    }

    @Test
    @DisplayName("Cancelar una orden de otro usuario se trata como inexistente")
    void cancelOrderOfAnotherUserIsNotFound() {
        // La orden semilla #5 es NEW del usuario 1.
        assertThatThrownBy(() -> cancelOrderService.cancel(5L, OTHER_USER))
                .isInstanceOf(OrderNotFoundException.class);
    }
}

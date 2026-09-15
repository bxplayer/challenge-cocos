package com.cocos.broker.application;

import com.cocos.broker.domain.OrderSide;
import com.cocos.broker.domain.OrderStatus;
import com.cocos.broker.domain.OrderType;
import com.cocos.broker.domain.PortfolioCalculator;
import com.cocos.broker.domain.exception.InstrumentNotFoundException;
import com.cocos.broker.domain.exception.InvalidOrderException;
import com.cocos.broker.domain.exception.UserNotFoundException;
import com.cocos.broker.domain.model.Instrument;
import com.cocos.broker.domain.model.Order;
import com.cocos.broker.domain.port.InstrumentRepository;
import com.cocos.broker.domain.port.MarketDataRepository;
import com.cocos.broker.domain.port.OrderRepository;
import com.cocos.broker.domain.port.PortfolioCache;
import com.cocos.broker.domain.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SendOrderService {

    private static final Logger log = LoggerFactory.getLogger(SendOrderService.class);

    private final OrderRepository orderRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataRepository marketDataRepository;
    private final UserRepository userRepository;
    private final PortfolioCache portfolioCache;
    private final Clock clock;

    private final PortfolioCalculator calculator = new PortfolioCalculator();

    @Transactional
    public Order send(SendOrderCommand cmd) {
        // Lock de fila sobre el usuario: dos órdenes concurrentes del mismo usuario
        // se procesan una después de la otra, así la segunda ve la primera al
        // validar fondos/acciones (evita sobregiros por check-then-act).
        userRepository.findByIdForUpdate(cmd.userId())
                .orElseThrow(() -> new UserNotFoundException(cmd.userId()));
        Instrument instrument = instrumentRepository.findById(cmd.instrumentId())
                .orElseThrow(() -> new InstrumentNotFoundException(cmd.instrumentId()));

        if (instrument.isCurrency()) {
            throw new InvalidOrderException("Solo se pueden operar instrumentos de tipo ACCIONES");
        }
        if (cmd.side() != OrderSide.BUY && cmd.side() != OrderSide.SELL) {
            throw new InvalidOrderException("El side debe ser BUY o SELL");
        }

        boolean hasSize = cmd.size() != null;
        boolean hasAmount = cmd.amount() != null;
        if (hasSize == hasAmount) {
            throw new InvalidOrderException("Enviar exactamente uno de 'size' o 'amount'");
        }

        BigDecimal price = resolvePrice(cmd);
        int size = resolveSize(cmd, price);

        BigDecimal required = price.multiply(BigDecimal.valueOf(size));
        List<Order> userOrders = orderRepository.findByUserId(cmd.userId());
        OrderStatus status = resolveStatus(cmd, size, required, userOrders);

        Order order = new Order(null, cmd.instrumentId(), cmd.userId(), size, price,
                cmd.type(), cmd.side(), status, LocalDateTime.now(clock));
        Order saved = orderRepository.save(order);

        log.info("Orden #{} {} {} {} size={} price={} -> {}",
                saved.id(), cmd.type(), cmd.side(), instrument.ticker(), size, price, status);

        // Cualquier orden aceptada (NEW/FILLED) cambia cash disponible o tenencia:
        // invalidamos el cache del portfolio del usuario (REJECTED no altera nada).
        if (saved.status() != OrderStatus.REJECTED) {
            portfolioCache.evict(cmd.userId());
        }

        // Punto de extensión (no aplica en este alcance): si el sistema evolucionara a
        // ejecutar órdenes LIMIT contra el mercado (matching engine), acá se publicaría
        // un evento a una cola (p. ej. SQS) para procesamiento asíncrono. En este scope
        // no hay procesamiento asíncrono (el spec indica no simular el mercado) y la
        // ejecución debe ser síncrona y transaccional, por lo que no se usa cola.
        return saved;
    }

    /** Precio de ejecución: el enviado (LIMIT) o el último close (MARKET). */
    private BigDecimal resolvePrice(SendOrderCommand cmd) {
        if (cmd.type() == OrderType.LIMIT) {
            if (cmd.price() == null || cmd.price().signum() <= 0) {
                throw new InvalidOrderException("Las órdenes LIMIT requieren 'price' > 0");
            }
            return cmd.price();
        }
        return marketDataRepository.findLatestByInstrumentId(cmd.instrumentId())
                .map(md -> md.close())
                .filter(close -> close != null && close.signum() > 0)
                .orElseThrow(() -> new InvalidOrderException(
                        "No hay precio de mercado para el instrumento " + cmd.instrumentId()));
    }

    /** Cantidad: la enviada, o la máxima entera que cubre el monto (floor). */
    private int resolveSize(SendOrderCommand cmd, BigDecimal price) {
        if (cmd.size() != null) {
            if (cmd.size() <= 0) {
                throw new InvalidOrderException("'size' debe ser > 0");
            }
            return cmd.size();
        }
        if (cmd.amount().signum() <= 0) {
            throw new InvalidOrderException("'amount' debe ser > 0");
        }
        int size = cmd.amount().divideToIntegralValue(price).intValueExact();
        if (size <= 0) {
            throw new InvalidOrderException("El monto no alcanza para comprar al menos una acción");
        }
        return size;
    }

    /**
     * Estado resultante: si hay fondos (BUY) o acciones (SELL) suficientes,
     * MARKET → FILLED y LIMIT → NEW; en caso contrario, REJECTED (persistido).
     */
    private OrderStatus resolveStatus(SendOrderCommand cmd, int size, BigDecimal required, List<Order> userOrders) {
        boolean acceptable;
        if (cmd.side() == OrderSide.BUY) {
            acceptable = calculator.availableCash(userOrders).compareTo(required) >= 0;
        } else {
            acceptable = calculator.availableShares(userOrders, cmd.instrumentId()) >= size;
        }
        if (!acceptable) {
            return OrderStatus.REJECTED;
        }
        return cmd.type() == OrderType.MARKET ? OrderStatus.FILLED : OrderStatus.NEW;
    }
}

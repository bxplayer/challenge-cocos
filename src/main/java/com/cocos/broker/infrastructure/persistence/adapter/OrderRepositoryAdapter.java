package com.cocos.broker.infrastructure.persistence.adapter;

import com.cocos.broker.domain.model.Order;
import com.cocos.broker.infrastructure.persistence.entity.OrderEntity;
import com.cocos.broker.domain.port.OrderRepository;
import com.cocos.broker.infrastructure.persistence.repository.OrderJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository repository;

    @Override
    public Optional<Order> findById(Long id) {
        return repository.findById(id).map(OrderRepositoryAdapter::toDomain);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return repository.findByUserId(userId).stream().map(OrderRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Order save(Order order) {
        return toDomain(repository.save(toEntity(order)));
    }

    private static Order toDomain(OrderEntity e) {
        return new Order(
                e.getId(), e.getInstrumentId(), e.getUserId(), e.getSize(), e.getPrice(),
                e.getType(), e.getSide(), e.getStatus(), e.getDatetime());
    }

    private static OrderEntity toEntity(Order o) {
        OrderEntity e = new OrderEntity();
        e.setId(o.id());
        e.setInstrumentId(o.instrumentId());
        e.setUserId(o.userId());
        e.setSize(o.size());
        e.setPrice(o.price());
        e.setType(o.type());
        e.setSide(o.side());
        e.setStatus(o.status());
        e.setDatetime(o.datetime());
        return e;
    }
}

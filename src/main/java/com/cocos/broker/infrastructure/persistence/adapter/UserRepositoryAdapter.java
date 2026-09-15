package com.cocos.broker.infrastructure.persistence.adapter;

import com.cocos.broker.domain.model.User;
import com.cocos.broker.domain.port.UserRepository;
import com.cocos.broker.infrastructure.persistence.entity.UserEntity;
import com.cocos.broker.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository repository;

    @Override
    public boolean existsById(Long id) {
        return repository.existsById(id);
    }

    @Override
    public Optional<User> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(UserRepositoryAdapter::toDomain);
    }

    private static User toDomain(UserEntity e) {
        return new User(e.getId(), e.getEmail(), e.getAccountNumber());
    }
}

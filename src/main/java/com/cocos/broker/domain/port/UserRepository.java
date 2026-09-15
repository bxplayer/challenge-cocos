package com.cocos.broker.domain.port;

import com.cocos.broker.domain.model.User;

import java.util.Optional;

/**
 * Puerto de salida para consulta de usuarios.
 */
public interface UserRepository {

    boolean existsById(Long id);

    /**
     * Busca el usuario y toma un lock de escritura sobre su fila hasta el fin de
     * la transacción en curso. Serializa el envío de órdenes de un mismo usuario:
     * la validación de fondos/acciones y la persistencia de la orden ocurren sin
     * que otra orden concurrente del mismo usuario pueda intercalarse.
     */
    Optional<User> findByIdForUpdate(Long id);
}

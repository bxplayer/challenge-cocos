package com.cocos.broker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class BrokerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrokerApplication.class, args);
    }

    /** Reloj inyectable (UTC): permite fijar el tiempo en tests. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

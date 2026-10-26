package com.preptrack.simulator;

import com.preptrack.simulator.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class DemoDataRunner implements CommandLineRunner {
    private final DemoDataService demo;
    private final ConfigurableApplicationContext context;
    @Value("${DEMO_EMAIL:}") private String email;
    @Value("${DEMO_PASSWORD:}") private String password;
    @Value("${preptrack.demo.exit:false}") private boolean exitAfterGeneration;
    public void run(String... args) {
        Long id = demo.generate(email, password);
        log.info("Created synthetic demo user {} ({}) with ten days of history", id, email);
        if (exitAfterGeneration) context.close();
    }
}

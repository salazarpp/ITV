package com.pokesync;

import com.pokesync.infrastructure.observability.SafeExceptionDiagnostics;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;

@SpringBootApplication
public class PokeSyncApplication {

    public static void main(String[] args) {
        var application = new SpringApplication(PokeSyncApplication.class);
        application.addListeners((ApplicationListener<ApplicationFailedEvent>) event ->
                LoggerFactory.getLogger(PokeSyncApplication.class).error(
                        "POKESYNC-APP-ERR-STARTUP | Application startup failed diagnostic={}",
                        SafeExceptionDiagnostics.describe(event.getException())));
        application.run(args);
    }
}

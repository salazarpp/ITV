package com.pokesync.infrastructure.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLifecycleLogger {
    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleLogger.class);

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        log.info("POKESYNC-APP-0001 | Application ready");
    }

    @EventListener(ContextClosedEvent.class)
    public void stopped() {
        log.info("POKESYNC-APP-0002 | Application context closing");
    }
}

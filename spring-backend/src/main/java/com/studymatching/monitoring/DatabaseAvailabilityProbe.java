package com.studymatching.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;

/** Cached connectivity probe: scraping metrics must never wait for the database. */
@Component
@Profile("monitoring")
public class DatabaseAvailabilityProbe {
    private static final Logger log = LoggerFactory.getLogger(DatabaseAvailabilityProbe.class);
    private final DataSource dataSource;
    private volatile double available = Double.NaN;
    private volatile double lastCheckedSeconds;

    public DatabaseAvailabilityProbe(DataSource dataSource, MeterRegistry registry) {
        this.dataSource = dataSource;
        Gauge.builder("study.database.available", this, probe -> probe.available)
                .description("Last completed database connectivity probe: 1 reachable, 0 unavailable")
                .register(registry);
        Gauge.builder("study.database.probe.timestamp.seconds", this, probe -> probe.lastCheckedSeconds)
                .description("Unix timestamp of the last completed database probe")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${monitoring.database-probe-interval-ms:5000}")
    public void check() {
        double next;
        try (var connection = dataSource.getConnection()) {
            next = connection.isValid(2) ? 1 : 0;
        } catch (SQLException exception) {
            next = 0;
        }
        if (next != available) {
            if (next == 0) log.warn("database_connectivity state=DOWN");
            else log.info("database_connectivity state=UP");
        }
        available = next;
        lastCheckedSeconds = Instant.now().getEpochSecond();
    }
}

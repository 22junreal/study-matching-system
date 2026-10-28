package com.studymatching.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DatabaseAvailabilityProbeTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @org.junit.jupiter.api.AfterEach
    void closeRegistry() { registry.close(); }

    @Test
    void scrapeReadsCachedStateWithoutAccessingDatabase() {
        var source = mock(DataSource.class);
        {
            new DatabaseAvailabilityProbe(source, registry);
            assertThat(registry.get("study.database.available").gauge().value()).isNaN();
            assertThat(registry.get("study.database.probe.timestamp.seconds").gauge().value()).isZero();
            verifyNoInteractions(source);
        }
    }

    @Test
    void reportsFailureAndRecoveryAndClosesConnections() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(source.getConnection()).thenThrow(new SQLException("database unavailable")).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        {
            var probe = new DatabaseAvailabilityProbe(source, registry);
            probe.check();
            assertThat(registry.get("study.database.available").gauge().value()).isZero();
            assertThat(registry.get("study.database.probe.timestamp.seconds").gauge().value()).isPositive();
            probe.check();
            assertThat(registry.get("study.database.available").gauge().value()).isEqualTo(1);
            verify(connection).close();
            clearInvocations(source);
            registry.get("study.database.available").gauge().value();
            verifyNoInteractions(source);
        }
    }

    @Test
    void invalidConnectionIsDownEvenWhenBorrowSucceeded() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);
        {
            var probe = new DatabaseAvailabilityProbe(source, registry);
            probe.check();
            assertThat(registry.get("study.database.available").gauge().value()).isZero();
            verify(connection).close();
        }
    }
}

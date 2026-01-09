// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author glebashnik
 */
public class SidecarProbeTest {
    @Test
    void valid_probe_creation() {
        var httpProbe = new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 10, 5, 2, 3);
        assertEquals(10, httpProbe.initialDelaySeconds());
        assertEquals(5, httpProbe.periodSeconds());
        assertEquals(2, httpProbe.timeoutSeconds());
        assertEquals(3, httpProbe.failureThreshold());

        var execProbe = new SidecarProbe(new SidecarProbe.ExecAction(List.of("cat", "/tmp/healthy")), 0, 10, 1, 2);
        assertEquals(0, execProbe.initialDelaySeconds());
        assertEquals(10, execProbe.periodSeconds());
        assertEquals(1, execProbe.timeoutSeconds());
        assertEquals(2, execProbe.failureThreshold());
    }

    @Test
    void null_action_throws() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(null, 10, 5, 2, 3));
        assertEquals("Probe action must not be null", exception.getMessage());
    }

    @Test
    void invalid_initial_delay_seconds() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), -1, 5, 2, 3));
        assertEquals("initialDelaySeconds must be between 0 and 1800 seconds, got: -1", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 1801, 5, 2, 3));
        assertEquals("initialDelaySeconds must be between 0 and 1800 seconds, got: 1801", exception.getMessage());
    }

    @Test
    void invalid_period_seconds() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 10, 0, 2, 3));
        assertEquals("periodSeconds must be between 1 and 300 seconds, got: 0", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 10, -5, 2, 3));
        assertEquals("periodSeconds must be between 1 and 300 seconds, got: -5", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 10, 301, 2, 3));
        assertEquals("periodSeconds must be between 1 and 300 seconds, got: 301", exception.getMessage());
    }

    @Test
    void invalid_timeout_seconds() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 10, 5, 0, 3));
        assertEquals("timeoutSeconds must be between 1 and 60 seconds, got: 0", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 10, 5, -2, 3));
        assertEquals("timeoutSeconds must be between 1 and 60 seconds, got: -2", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 10, 5, 61, 3));
        assertEquals("timeoutSeconds must be between 1 and 60 seconds, got: 61", exception.getMessage());
    }

    @Test
    void invalid_failure_threshold() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 10, 5, 2, 0));
        assertEquals("failureThreshold must be between 1 and 10, got: 0", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 10, 5, 2, -3));
        assertEquals("failureThreshold must be between 1 and 10, got: -3", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 10, 5, 2, 11));
        assertEquals("failureThreshold must be between 1 and 10, got: 11", exception.getMessage());
    }

    @Test
    void http_get_action_valid() {
        var action1 = new SidecarProbe.HttpGetAction("/", 80);
        assertEquals("/", action1.path());
        assertEquals(80, action1.port());

        var action2 = new SidecarProbe.HttpGetAction("/v2/health/live", 8000);
        assertEquals("/v2/health/live", action2.path());
        assertEquals(8000, action2.port());

        var action3 = new SidecarProbe.HttpGetAction("/health/ready", 65535);
        assertEquals("/health/ready", action3.path());
        assertEquals(65535, action3.port());
    }

    @Test
    void http_get_action_null_path() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe.HttpGetAction(null, 8080));
        assertEquals("Path must not be null or empty and must start with '/', got: null", exception.getMessage());
    }

    @Test
    void http_get_action_empty_path() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe.HttpGetAction("", 8080));
        assertEquals("Path must not be null or empty and must start with '/', got: ", exception.getMessage());
    }

    @Test
    void http_get_action_path_without_leading_slash() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe.HttpGetAction("health", 8080));
        assertEquals("Path must not be null or empty and must start with '/', got: health", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe.HttpGetAction("v2/health/live", 8000));
        assertEquals("Path must not be null or empty and must start with '/', got: v2/health/live", exception.getMessage());
    }

    @Test
    void http_get_action_invalid_port() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe.HttpGetAction("/health", 0));
        assertEquals("Port must be between 1 and 65535, got: 0", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe.HttpGetAction("/health", -1));
        assertEquals("Port must be between 1 and 65535, got: -1", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe.HttpGetAction("/health", 65536));
        assertEquals("Port must be between 1 and 65535, got: 65536", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe.HttpGetAction("/health", 100000));
        assertEquals("Port must be between 1 and 65535, got: 100000", exception.getMessage());
    }

    @Test
    void exec_action_valid() {
        var action1 = new SidecarProbe.ExecAction(List.of("cat", "/tmp/healthy"));
        assertEquals(List.of("cat", "/tmp/healthy"), action1.command());

        var action2 = new SidecarProbe.ExecAction(List.of("/bin/check-status"));
        assertEquals(List.of("/bin/check-status"), action2.command());

        var action3 = new SidecarProbe.ExecAction(List.of("sh", "-c", "curl localhost:8080/health"));
        assertEquals(List.of("sh", "-c", "curl localhost:8080/health"), action3.command());
    }

    @Test
    void exec_action_null_command() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe.ExecAction(null));
        assertEquals("Command must not be null or empty", exception.getMessage());
    }

    @Test
    void exec_action_empty_command() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SidecarProbe.ExecAction(List.of()));
        assertEquals("Command must not be null or empty", exception.getMessage());
    }

    @Test
    void probe_with_minimum_valid_values() {
        // Test boundary values - minimum valid values
        var probe = new SidecarProbe(new SidecarProbe.HttpGetAction("/", 1), 0, 1, 1, 1);
        assertEquals(0, probe.initialDelaySeconds());
        assertEquals(1, probe.periodSeconds());
        assertEquals(1, probe.timeoutSeconds());
        assertEquals(1, probe.failureThreshold());
    }

    @Test
    void probe_with_maximum_valid_values() {
        // Test boundary values - maximum valid values
        var probe = new SidecarProbe(new SidecarProbe.HttpGetAction("/health", 8080), 1800, 300, 60, 10);
        assertEquals(1800, probe.initialDelaySeconds());
        assertEquals(300, probe.periodSeconds());
        assertEquals(60, probe.timeoutSeconds());
        assertEquals(10, probe.failureThreshold());
    }

}
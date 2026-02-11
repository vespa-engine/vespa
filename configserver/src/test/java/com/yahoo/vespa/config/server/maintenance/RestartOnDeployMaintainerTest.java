// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.model.api.ServiceConfigState;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.PendingRestarts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.maintenance.RestartOnDeployMaintainer.triggerPendingRestarts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author glebashnik
 */
class RestartOnDeployMaintainerTest {
    private static final Logger log = Logger.getLogger(RestartOnDeployMaintainerTest.class.getName());

    @Test
    void test_triggerPendingRestarts_restart_with_no_service_states() {
        // Restart when no service states.
        assertEquals(
                Map.of(),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a", List.of(),
                                        "b", List.of()),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_restart_with_applyOnRestart_empty() {
        assertEquals(
                Map.of(),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a", List.of(new ServiceConfigState(1, Optional.empty())),
                                        "b", List.of(new ServiceConfigState(1, Optional.empty()))),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_restart_with_applyOnRestart_true() {
        assertEquals(
                Map.of(),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a", List.of(new ServiceConfigState(0, Optional.of(true))),
                                        "b", List.of(new ServiceConfigState(0, Optional.of(true)))),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_no_restart_with_applyOnRestart_false() {
        assertEquals(
                Map.of(1L, Set.of("a", "b")),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a", List.of(new ServiceConfigState(0, Optional.of(false))),
                                        "b", List.of(new ServiceConfigState(0, Optional.of(false)))),
                                (id, hosts) -> {
                                    fail("Should not be called");
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_restart_with_applyOnRestart_false_but_ready_generation() {
        assertEquals(
                Map.of(),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a", List.of(new ServiceConfigState(1, Optional.of(false))),
                                        "b", List.of(new ServiceConfigState(1, Optional.of(false)))),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_no_restart_without_ready_generation() {
        assertEquals(
                Map.of(1L, Set.of("a", "b")),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a",
                                        List.of(
                                                new ServiceConfigState(0, Optional.empty()),
                                                new ServiceConfigState(0, Optional.of(true))),
                                        "b",
                                        List.of(
                                                new ServiceConfigState(0, Optional.empty()),
                                                new ServiceConfigState(0, Optional.of(true)))),
                                (id, hosts) -> {
                                    fail("Should not be called");
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_restart_with_many_restart_generations() {
        assertEquals(
                Map.of(2L, Set.of("b", "c"), 3L, Set.of("c")),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a",
                                                List.of(
                                                        new ServiceConfigState(2, Optional.empty()),
                                                        new ServiceConfigState(1, Optional.of(true))),
                                        "b",
                                                List.of(
                                                        new ServiceConfigState(1, Optional.empty()),
                                                        new ServiceConfigState(1, Optional.of(true)))),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty()
                                        .withRestarts(1, List.of("a", "b"))
                                        .withRestarts(2, List.of("b", "c"))
                                        .withRestarts(3, List.of("c")),
                                log)
                        .generationsForRestarts());
    }
}

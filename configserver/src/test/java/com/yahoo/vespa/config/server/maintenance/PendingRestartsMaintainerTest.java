// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.model.api.ServiceConfigState;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceListResponse;
import com.yahoo.vespa.config.server.application.PendingRestarts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.maintenance.PendingRestartsMaintainer.triggerPendingRestarts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Jon Marius Venstad
 * @author glebashnik
 */
class PendingRestartsMaintainerTest {
    private static final Logger log = Logger.getLogger(PendingRestartsMaintainerTest.class.getName());

    @Test
    void test_TriggerPendingRestarts() {
        // Nothing happens with no pending restarts.
        assertSame(
                PendingRestarts.empty(),
                triggerPendingRestarts(
                        hosts -> {
                            fail("Should not be called");
                            return null;
                        },
                        hosts -> {
                            fail("Should not be called");
                            return null;
                        },
                        (id, hosts) -> {
                            fail("Should not be called");
                        },
                        ApplicationId.defaultId(),
                        PendingRestarts.empty(),
                        false,
                        log));

        // Nothing happens when services are on a too low generation.
        assertEquals(
                Map.of(1L, Set.of("a", "b"), 2L, Set.of("c")),
                triggerPendingRestarts(
                                hosts -> new ServiceListResponse(Map.of(), 3, 0),
                                hosts -> {
                                    fail("Should not be called");
                                    return null;
                                },
                                (id, hosts) -> {
                                    fail("Should not be called");
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty()
                                        .withRestarts(1, List.of("a", "b"))
                                        .withRestarts(2, List.of("c")),
                                false,
                                log)
                        .generationsForRestarts());

        // Only the first hosts are restarted before the second generation is reached.
        assertEquals(
                Map.of(2L, Set.of("c")),
                triggerPendingRestarts(
                                hosts -> new ServiceListResponse(Map.of(), 3, 1),
                                hosts -> {
                                    fail("Should not be called");
                                    return null;
                                },
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty()
                                        .withRestarts(1, List.of("a", "b"))
                                        .withRestarts(2, List.of("c")),
                                false,
                                log)
                        .generationsForRestarts());

        // All hosts are restarted when the second generation is reached.
        assertEquals(
                Map.of(),
                triggerPendingRestarts(
                                hosts -> new ServiceListResponse(Map.of(), 3, 2),
                                hosts -> {
                                    fail("Should not be called");
                                    return null;
                                },
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b", "c"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty()
                                        .withRestarts(1, List.of("a", "b"))
                                        .withRestarts(2, List.of("c")),
                                false,
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_with_waitForApplyOnRestart() {
        // Nothing happens when applyOnRestart is false for all hosts.
        assertEquals(
                Map.of(1L, Set.of("a", "b")),
                triggerPendingRestarts(
                                hosts -> new ServiceListResponse(Map.of(), 3, 1),
                                host -> Map.of(
                                        "a",
                                        new ServiceConfigState(0, Optional.of(false)),
                                        "b",
                                        new ServiceConfigState(0, Optional.of(false))),
                                (id, hosts) -> {
                                    fail("Should not be called");
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                true,
                                log)
                        .generationsForRestarts());

        // Only hosts with applyOnRestart=true are restarted, only restarted hosts are removed from pending.
        assertEquals(
                Map.of(1L, Set.of("b")),
                triggerPendingRestarts(
                                hosts -> new ServiceListResponse(Map.of(), 3, 1),
                                host -> Map.of(
                                        "a",
                                        new ServiceConfigState(0, Optional.of(true)),
                                        "b",
                                        new ServiceConfigState(0, Optional.of(false))),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                true,
                                log)
                        .generationsForRestarts());

        // Hosts with applyOnRestart=true or empty are restarted (backwards compatibility).
        assertEquals(
                Map.of(),
                triggerPendingRestarts(
                                hosts -> new ServiceListResponse(Map.of(), 3, 1),
                                host -> Map.of(
                                        "a",
                                        new ServiceConfigState(0, Optional.of(true)),
                                        "b",
                                        new ServiceConfigState(0, Optional.empty())),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                true,
                                log)
                        .generationsForRestarts());
    }
}

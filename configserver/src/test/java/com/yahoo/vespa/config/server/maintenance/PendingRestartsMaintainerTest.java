package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceListResponse;
import com.yahoo.vespa.config.server.application.PendingRestarts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.maintenance.PendingRestartsMaintainer.triggerPendingRestarts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

class PendingRestartsMaintainerTest {

    private static final Logger log = Logger.getLogger(PendingRestartsMaintainerTest.class.getName());

    @Test
    void testMaintenance() {
        // Nothing happens with no pending restarts.
        assertSame(PendingRestarts.empty(),
                   triggerPendingRestarts(hosts -> { fail("Should not be called"); return null; },
                                          (id, hosts) -> { fail("Should not be called"); },
                                          ApplicationId.defaultId(),
                                          PendingRestarts.empty(),
                                          log));

        // Nothing happens when services are on a too low generation.
        assertEquals(Map.of(1L, Set.of("a", "b"), 2L, Set.of("c")),
                     triggerPendingRestarts(hosts -> new ServiceListResponse(Map.of(), 3, 0),
                                            (id, hosts) -> { fail("Should not be called"); },
                                            ApplicationId.defaultId(),
                                            PendingRestarts.empty()
                                                           .withRestarts(1, List.of("a", "b"))
                                                           .withRestarts(2, List.of("c")),
                                            log)
                             .generationsForRestarts());

        // Only the first hosts are restarted before the second generation is reached.
        assertEquals(Map.of(2L, Set.of("c")),
                     triggerPendingRestarts(hosts -> new ServiceListResponse(Map.of(), 3, 1),
                                            (id, hosts) -> {
                                                assertEquals(ApplicationId.defaultId(), id);
                                                assertEquals(Set.of("a", "b"), hosts);
                                            },
                                            ApplicationId.defaultId(),
                                            PendingRestarts.empty()
                                                           .withRestarts(1, List.of("a", "b"))
                                                           .withRestarts(2, List.of("c")),
                                            log)
                             .generationsForRestarts());

        // All hosts are restarted when the second generation is reached.
        assertEquals(Map.of(),
                     triggerPendingRestarts(hosts -> new ServiceListResponse(Map.of(), 3, 2),
                                            (id, hosts) -> {
                                                assertEquals(ApplicationId.defaultId(), id);
                                                assertEquals(Set.of("a", "b", "c"), hosts);
                                            },
                                            ApplicationId.defaultId(),
                                            PendingRestarts.empty()
                                                           .withRestarts(1, List.of("a", "b"))
                                                           .withRestarts(2, List.of("c")),
                                            log)
                             .generationsForRestarts());

    }

}

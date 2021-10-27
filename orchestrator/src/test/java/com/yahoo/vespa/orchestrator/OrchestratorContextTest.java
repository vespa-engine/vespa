// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.test.ManualClock;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.TenantId;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hakonhall
 */
public class OrchestratorContextTest {
    private final ApplicationInstanceReference application = new ApplicationInstanceReference(
            new TenantId("tenant"),
            new ApplicationInstanceId("app:dev:us-east-1:default"));

    @Test
    public void testLargeLocks() {
        var mutable = new Object() { boolean locked = true; };
        Runnable unlock = () -> mutable.locked = false;

        try (OrchestratorContext rootContext = OrchestratorContext.createContextForMultiAppOp(new ManualClock())) {
            try (OrchestratorContext probeContext = rootContext.createSubcontextForSingleAppOp(true)) {
                assertFalse(probeContext.hasLock(application));
                assertTrue(probeContext.registerLockAcquisition(application, unlock));

                assertTrue(probeContext.hasLock(application));
                assertTrue(mutable.locked);
            }

            try (OrchestratorContext nonProbeContext = rootContext.createSubcontextForSingleAppOp(false)) {
                assertTrue(nonProbeContext.hasLock(application));
                assertTrue(mutable.locked);
            }

            assertTrue(mutable.locked);
        }
        assertFalse(mutable.locked);
    }
}
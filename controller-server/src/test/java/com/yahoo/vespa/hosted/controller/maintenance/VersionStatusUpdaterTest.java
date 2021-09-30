// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.organization.SystemMonitor;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class VersionStatusUpdaterTest {

    /** Test that this job updates the status. Test of the content of the update is in
     * {@link com.yahoo.vespa.hosted.controller.versions.VersionStatusTest} */
    @Test
    public void testVersionUpdating() {
        ControllerTester tester = new ControllerTester();
        tester.controller().updateVersionStatus(new VersionStatus(Collections.emptyList()));
        assertFalse(tester.controller().readVersionStatus().systemVersion().isPresent());

        VersionStatusUpdater updater = new VersionStatusUpdater(tester.controller(), Duration.ofDays(1)
        );
        updater.maintain();
        assertTrue(tester.controller().readVersionStatus().systemVersion().isPresent());
    }

    @Test
    public void testConfidenceConversion() {
        List.of(VespaVersion.Confidence.values()).forEach(VersionStatusUpdater::convert);
        assertEquals(SystemMonitor.Confidence.broken, VersionStatusUpdater.convert(VespaVersion.Confidence.broken));
        assertEquals(SystemMonitor.Confidence.low, VersionStatusUpdater.convert(VespaVersion.Confidence.low));
        assertEquals(SystemMonitor.Confidence.normal, VersionStatusUpdater.convert(VespaVersion.Confidence.normal));
        assertEquals(SystemMonitor.Confidence.high, VersionStatusUpdater.convert(VespaVersion.Confidence.high));
    }
    
}

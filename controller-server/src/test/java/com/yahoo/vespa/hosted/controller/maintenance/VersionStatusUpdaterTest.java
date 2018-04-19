// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;

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
        assertFalse(tester.controller().versionStatus().systemVersion().isPresent());

        VersionStatusUpdater updater = new VersionStatusUpdater(tester.controller(), Duration.ofMinutes(3), 
                                                                new JobControl(new MockCuratorDb()));
        updater.maintain();
        assertTrue(tester.controller().versionStatus().systemVersion().isPresent());
    }
    
}

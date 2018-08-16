// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

/**
 * @author mpolden
 */
public class OsVersionStatusUpdaterTest {

    @Test
    public void test_update() {
        ControllerTester tester = new ControllerTester();
        OsVersionStatusUpdater statusUpdater = new OsVersionStatusUpdater(tester.controller(), Duration.ofDays(1),
                                                                          new JobControl(new MockCuratorDb()));

        // Initially empty
        assertSame(OsVersionStatus.empty, tester.controller().osVersionStatus());

        // Setting a new target adds it to current status
        Version version1 = Version.fromString("7.1");
        tester.controller().upgradeOs(version1);
        statusUpdater.maintain();
        List<OsVersion> osVersions = tester.controller().osVersionStatus().versions();
        assertFalse(osVersions.isEmpty());
        assertEquals(version1, osVersions.get(0).version());
    }

}

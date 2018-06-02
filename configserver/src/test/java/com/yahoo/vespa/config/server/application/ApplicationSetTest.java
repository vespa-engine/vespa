// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Version;
import com.yahoo.vespa.config.server.ModelStub;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author Vegard Sjonfjell
 */
public class ApplicationSetTest {

    ApplicationSet applicationSet;
    List<Version> vespaVersions = new ArrayList<>();
    List<Application> applications = new ArrayList<>();

    @Before
    public void setUp() {
        vespaVersions.add(Version.fromString("1.2.3"));
        vespaVersions.add(Version.fromString("1.2.4"));
        vespaVersions.add(Version.fromString("1.2.5"));
        applications.add(new Application(new ModelStub(), null, 0, false, vespaVersions.get(0), MetricUpdater.createTestUpdater(), ApplicationId.defaultId()));
        applications.add(new Application(new ModelStub(), null, 0, false, vespaVersions.get(1), MetricUpdater.createTestUpdater(), ApplicationId.defaultId()));
        applications.add(new Application(new ModelStub(), null, 0, false, vespaVersions.get(2), MetricUpdater.createTestUpdater(), ApplicationId.defaultId()));
    }

    @Test
    public void testGetForVersionOrLatestReturnsCorrectVersion() {
        applicationSet = ApplicationSet.fromList(applications);
        assertEquals(applicationSet.getForVersionOrLatest(Optional.of(vespaVersions.get(0)), Instant.now()), applications.get(0));
        assertEquals(applicationSet.getForVersionOrLatest(Optional.of(vespaVersions.get(1)), Instant.now()), applications.get(1));
        assertEquals(applicationSet.getForVersionOrLatest(Optional.of(vespaVersions.get(2)), Instant.now()), applications.get(2));
    }

    @Test
    public void testGetForVersionOrLatestReturnsLatestVersion() {
        applicationSet = ApplicationSet.fromList(applications);
        assertEquals(applicationSet.getForVersionOrLatest(Optional.empty(), Instant.now()), applications.get(2));
    }

    @Test (expected = VersionDoesNotExistException.class)
    public void testGetForVersionOrLatestThrows() {
        applicationSet = ApplicationSet.fromList(Arrays.asList(applications.get(0), applications.get(2)));
        applicationSet.getForVersionOrLatest(Optional.of(vespaVersions.get(1)), Instant.now());
    }

}

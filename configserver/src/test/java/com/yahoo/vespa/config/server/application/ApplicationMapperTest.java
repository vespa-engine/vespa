// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.component.Version;
import com.yahoo.vespa.config.server.ModelStub;
import com.yahoo.vespa.config.server.NotFoundException;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ApplicationMapperTest {

    private ApplicationId appId;
    private ApplicationMapper applicationMapper;
    private final ArrayList<Version> vespaVersions = new ArrayList<>();
    private final ArrayList<Application> applications = new ArrayList<>();

    @Before
    public void setUp() {
        applicationMapper = new ApplicationMapper();
        appId = new ApplicationId.Builder()
                .tenant("test")
                .applicationName("test")
                .instanceName("test")
                .build();
        vespaVersions.addAll(List.of(Version.fromString("1.2.3"), Version.fromString("1.2.4"), Version.fromString("1.2.5")));
        applications.addAll(List.of(createApplication(vespaVersions.get(0)), createApplication(vespaVersions.get(1)), createApplication(vespaVersions.get(2))));
    }

    @Test
    public void testGetForVersionReturnsCorrectVersion() {
        applicationMapper.register(appId, ApplicationSet.fromList(applications));
        assertEquals(applicationMapper.getForVersion(appId, Optional.of(vespaVersions.get(0)), Instant.now()), applications.get(0));
        assertEquals(applicationMapper.getForVersion(appId, Optional.of(vespaVersions.get(1)), Instant.now()), applications.get(1));
        assertEquals(applicationMapper.getForVersion(appId, Optional.of(vespaVersions.get(2)), Instant.now()), applications.get(2));
    }

    @Test
    public void testGetForVersionReturnsLatestVersion() {
        applicationMapper.register(appId, ApplicationSet.fromList(applications));
        assertEquals(applicationMapper.getForVersion(appId, Optional.empty(), Instant.now()), applications.get(2));
    }

    @Test (expected = VersionDoesNotExistException.class)
    public void testGetForVersionThrows() {
        applicationMapper.register(appId, ApplicationSet.fromList(Arrays.asList(applications.get(0), applications.get(2))));
        applicationMapper.getForVersion(appId, Optional.of(vespaVersions.get(1)), Instant.now());
    }

    @Test (expected = NotFoundException.class)
    public void testGetForVersionThrows2() {
        applicationMapper.register(appId, ApplicationSet.from(applications.get(0)));

        applicationMapper.getForVersion(new ApplicationId.Builder()
                                                .tenant("different")
                                                .applicationName("different")
                                                .instanceName("different")
                                                .build(),
                                        Optional.of(vespaVersions.get(1)),
                                        Instant.now());
    }

    private Application createApplication(Version version) {
        return new Application(new ModelStub(), null, 0, version, MetricUpdater.createTestUpdater(), ApplicationId.defaultId());
    }

}

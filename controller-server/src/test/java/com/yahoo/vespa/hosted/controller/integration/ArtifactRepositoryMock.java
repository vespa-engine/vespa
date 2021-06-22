// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.StableOsVersion;

import java.util.HashMap;
import java.util.Map;

/**
 * @author mpolden
 */
public class ArtifactRepositoryMock extends AbstractComponent implements ArtifactRepository {

    private final Map<Integer, StableOsVersion> stableOsVersions = new HashMap<>();

    @Override
    public byte[] getSystemApplicationPackage(ApplicationId application, ZoneId zone, Version version) {
        return new byte[0];
    }

    @Override
    public StableOsVersion stableOsVersion(int major) {
        StableOsVersion version = stableOsVersions.get(major);
        if (version == null) throw new IllegalArgumentException("No version set for major " + major);
        return version;
    }

    public void promoteOsVersion(StableOsVersion stableOsVersion) {
        stableOsVersions.put(stableOsVersion.version().getMajor(), stableOsVersion);
    }

}

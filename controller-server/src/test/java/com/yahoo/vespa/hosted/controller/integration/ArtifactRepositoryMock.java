// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.OsRelease;

import java.util.HashMap;
import java.util.Map;

/**
 * @author mpolden
 */
public class ArtifactRepositoryMock extends AbstractComponent implements ArtifactRepository {

    private final Map<String, OsRelease> releases = new HashMap<>();

    @Override
    public byte[] getSystemApplicationPackage(ApplicationId application, ZoneId zone, Version version) {
        return new byte[0];
    }

    @Override
    public OsRelease osRelease(int major, OsRelease.Tag tag) {
        OsRelease release = releases.get(key(major, tag));
        if (release == null) throw new IllegalArgumentException("No version set for major " + major + " with tag " + tag);
        return release;
    }

    public void addRelease(OsRelease osRelease) {
        releases.put(key(osRelease.version().getMajor(), osRelease.tag()), osRelease);
    }

    private static String key(int major, OsRelease.Tag tag) {
        return major + "@" + tag.name();
    }

}

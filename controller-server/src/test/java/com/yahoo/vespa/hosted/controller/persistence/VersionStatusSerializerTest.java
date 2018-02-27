// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.versions.DeploymentStatistics;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class VersionStatusSerializerTest {

    @Test
    public void testSerialization() {
        List<VespaVersion> vespaVersions = new ArrayList<>();
        DeploymentStatistics statistics = new DeploymentStatistics(
                Version.fromString("5.0"),
                Arrays.asList(ApplicationId.from("tenant1", "failing1", "default")),
                Arrays.asList(ApplicationId.from("tenant2", "success1", "default"),
                              ApplicationId.from("tenant2", "success2", "default")),
                Arrays.asList(ApplicationId.from("tenant1", "failing1", "default"),
                              ApplicationId.from("tenant2", "success2", "default"))
        );
        vespaVersions.add(new VespaVersion(statistics, "dead", Instant.now(), false,
                                           Arrays.asList("cfg1", "cfg2", "cfg3"), VespaVersion.Confidence.normal));
        vespaVersions.add(new VespaVersion(statistics, "cafe", Instant.now(), true,
                                           Arrays.asList("cfg1", "cfg2", "cfg3"), VespaVersion.Confidence.normal));
        VersionStatus status = new VersionStatus(vespaVersions);
        VersionStatusSerializer serializer = new VersionStatusSerializer();
        VersionStatus deserialized = serializer.fromSlime(serializer.toSlime(status));

        assertEquals(status.versions().size(), deserialized.versions().size());
        for (int i = 0; i < status.versions().size(); i++) {
            VespaVersion a = status.versions().get(i);
            VespaVersion b = deserialized.versions().get(i);
            assertEquals(a.releaseCommit(), b.releaseCommit());
            assertEquals(a.committedAt(), b.committedAt());
            assertEquals(a.isCurrentSystemVersion(), b.isCurrentSystemVersion());
            assertEquals(a.statistics(), b.statistics());
            assertEquals(a.configServerHostnames(), b.configServerHostnames());
            assertEquals(a.confidence(), b.confidence());
        }

    }

}

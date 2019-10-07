// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import com.yahoo.vespa.hosted.controller.versions.NodeVersions;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class OsVersionStatusSerializerTest {

    @Test
    public void test_serialization() {
        Version version1 = Version.fromString("7.1");
        Version version2 = Version.fromString("7.2");
        var versions = ImmutableMap.<OsVersion, NodeVersions>builder();

        versions.put(new OsVersion(version1, CloudName.defaultName()), NodeVersions.EMPTY.with(List.of(
                new NodeVersion(HostName.from("node1"), ZoneId.from("prod", "us-west"), version1, version2, Instant.ofEpochMilli(1)),
                new NodeVersion(HostName.from("node2"), ZoneId.from("prod", "us-east"), version1, version2, Instant.ofEpochMilli(2))
        )));
        versions.put(new OsVersion(version2, CloudName.defaultName()), NodeVersions.EMPTY.with(List.of(
                new NodeVersion(HostName.from("node3"), ZoneId.from("prod", "us-west"), version2, version2, Instant.ofEpochMilli(3)),
                new NodeVersion(HostName.from("node4"), ZoneId.from("prod", "us-east"), version2, version2, Instant.ofEpochMilli(4))
        )));

        OsVersionStatusSerializer serializer = new OsVersionStatusSerializer(new OsVersionSerializer(), new NodeVersionSerializer());
        OsVersionStatus status = new OsVersionStatus(versions.build());
        OsVersionStatus serialized = serializer.fromSlime(serializer.toSlime(status));
        assertEquals(status.versions(), serialized.versions());
    }

}

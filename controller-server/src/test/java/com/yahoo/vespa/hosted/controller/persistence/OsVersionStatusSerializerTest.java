// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class OsVersionStatusSerializerTest {

    @Test
    void serialization() {
        Version version1 = Version.fromString("7.1");
        Version version2 = Version.fromString("7.2");
        Map<OsVersion, List<NodeVersion>> versions = new LinkedHashMap<>();

        versions.put(new OsVersion(version1, CloudName.DEFAULT), List.of(
                new NodeVersion(HostName.of("node1"), ZoneId.from("prod", "us-west"), version1, version2, Optional.of(Instant.ofEpochMilli(11))),
                new NodeVersion(HostName.of("node2"), ZoneId.from("prod", "us-east"), version1, version2, Optional.of(Instant.ofEpochMilli(22)))
        ));
        versions.put(new OsVersion(version2, CloudName.DEFAULT), List.of(
                new NodeVersion(HostName.of("node3"), ZoneId.from("prod", "us-west"), version2, version2, Optional.of(Instant.ofEpochMilli(33))),
                new NodeVersion(HostName.of("node4"), ZoneId.from("prod", "us-east"), version2, version2, Optional.of(Instant.ofEpochMilli(44)))
        ));

        OsVersionStatusSerializer serializer = new OsVersionStatusSerializer(new OsVersionSerializer(), new NodeVersionSerializer());
        OsVersionStatus status = new OsVersionStatus(versions);
        OsVersionStatus serialized = serializer.fromSlime(serializer.toSlime(status));
        assertEquals(status.versions(), serialized.versions());
    }

}

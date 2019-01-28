// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.CloudName;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class OsVersionStatusSerializerTest {

    @Test
    public void test_serialization() {
        Version version1 = Version.fromString("7.1");
        Version version2 = Version.fromString("7.2");
        Map<OsVersion, List<OsVersionStatus.Node>> versions = new TreeMap<>();

        versions.put(new OsVersion(version1, CloudName.defaultName()), List.of(
                new OsVersionStatus.Node(HostName.from("node1"), version1, Environment.prod, RegionName.from("us-west")),
                new OsVersionStatus.Node(HostName.from("node2"), version1, Environment.prod, RegionName.from("us-east"))
        ));
        versions.put(new OsVersion(version2, CloudName.defaultName()), List.of(
                new OsVersionStatus.Node(HostName.from("node3"), version2, Environment.prod, RegionName.from("us-west")),
                new OsVersionStatus.Node(HostName.from("node4"), version2, Environment.prod, RegionName.from("us-east"))

        ));

        OsVersionStatusSerializer serializer = new OsVersionStatusSerializer(new OsVersionSerializer());
        OsVersionStatus status = new OsVersionStatus(versions);
        OsVersionStatus serialized = serializer.fromSlime(serializer.toSlime(status));
        assertEquals(status.versions(), serialized.versions());
    }

}

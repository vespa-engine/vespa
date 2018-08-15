// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import org.junit.Test;

import java.util.Arrays;
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
        List<OsVersion> osVersions = Arrays.asList(
                new OsVersion(version1, Arrays.asList(
                        new OsVersion.Node(HostName.from("node1"), version1, Environment.prod, RegionName.from("us-west")),
                        new OsVersion.Node(HostName.from("node2"), version1, Environment.prod, RegionName.from("us-east"))
                )),
                new OsVersion(version2, Arrays.asList(
                        new OsVersion.Node(HostName.from("node3"), version2, Environment.prod, RegionName.from("us-west")),
                        new OsVersion.Node(HostName.from("node4"), version2, Environment.prod, RegionName.from("us-east"))
                ))
        );

        OsVersionStatusSerializer serializer = new OsVersionStatusSerializer();
        OsVersionStatus status = new OsVersionStatus(osVersions);
        OsVersionStatus serialized = serializer.fromSlime(serializer.toSlime(status));

        for (int i = 0; i < status.versions().size(); i++) {
            OsVersion a = status.versions().get(i);
            OsVersion b = serialized.versions().get(i);
            assertEquals(a.version(), b.version());
            assertEquals(a.nodes(), b.nodes());
        }
    }

}

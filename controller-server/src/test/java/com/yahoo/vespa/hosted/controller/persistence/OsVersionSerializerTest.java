// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class OsVersionSerializerTest {

    @Test
    void test_serialization() {
        OsVersionSerializer serializer = new OsVersionSerializer();
        Set<OsVersion> osVersions = ImmutableSet.of(
                new OsVersion(Version.fromString("7.1"), CloudName.DEFAULT),
                new OsVersion(Version.fromString("7.1"), CloudName.from("foo"))
        );
        Set<OsVersion> serialized = serializer.fromSlime(serializer.toSlime(osVersions));
        assertEquals(osVersions, serialized);
    }

}

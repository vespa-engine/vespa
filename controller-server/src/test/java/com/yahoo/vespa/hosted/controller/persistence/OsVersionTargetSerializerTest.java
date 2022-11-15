// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionTarget;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class OsVersionTargetSerializerTest {

    @Test
    void serialization() {
        OsVersionTargetSerializer serializer = new OsVersionTargetSerializer(new OsVersionSerializer());
        Set<OsVersionTarget> targets = ImmutableSet.of(
                new OsVersionTarget(new OsVersion(Version.fromString("7.1"), CloudName.DEFAULT), Instant.ofEpochMilli(123)),
                new OsVersionTarget(new OsVersion(Version.fromString("7.1"), CloudName.from("foo")), Instant.ofEpochMilli(456))
        );
        Set<OsVersionTarget> serialized = serializer.fromSlime(serializer.toSlime(targets));
        assertEquals(targets, serialized);
    }

}

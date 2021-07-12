// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionTarget;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class OsVersionTargetSerializerTest {

    @Test
    public void serialization() {
        OsVersionTargetSerializer serializer = new OsVersionTargetSerializer(new OsVersionSerializer());
        Set<OsVersionTarget> targets = ImmutableSet.of(
                new OsVersionTarget(new OsVersion(Version.fromString("7.1"), CloudName.defaultName()), Duration.ZERO, Instant.ofEpochMilli(123)),
                new OsVersionTarget(new OsVersion(Version.fromString("7.1"), CloudName.from("foo")), Duration.ofDays(1), Instant.ofEpochMilli(456))
        );
        Set<OsVersionTarget> serialized = serializer.fromSlime(serializer.toSlime(targets));
        assertEquals(targets, serialized);
    }

}

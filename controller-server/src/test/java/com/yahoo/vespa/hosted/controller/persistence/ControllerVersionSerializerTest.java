// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.identifiers.ControllerVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class ControllerVersionSerializerTest {

    private final ControllerVersionSerializer serializer = new ControllerVersionSerializer();

    @Test
    void serialization() {
        var version = new ControllerVersion(Version.fromString("7.42.1"), "badc0ffee", Instant.ofEpochSecond(1565876112));
        var serialized = serializer.fromSlime(serializer.toSlime(version));
        assertEquals(version.version(), serialized.version());
        assertEquals(version.commitSha(), serialized.commitSha());
        assertEquals(version.commitDate(), serialized.commitDate());
    }

}

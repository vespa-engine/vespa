// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class OsVersionsSerializerTest {

    @Test
    public void serialization() {
        var versions = Map.of(
                NodeType.host, Version.fromString("1.2.3"),
                NodeType.proxyhost, Version.fromString("4.5.6"),
                NodeType.confighost, Version.fromString("7.8.9")
        );
        var serialized = OsVersionsSerializer.fromJson(OsVersionsSerializer.toJson(versions));
        assertEquals(serialized, versions);
    }

}

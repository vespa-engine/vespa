// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.os.OsVersion;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class OsVersionsSerializerTest {

    @Test
    public void legacy_format() {
        var json = "{\"host\":\"1.2.3\",\"proxyhost\":\"4.5.6\",\"confighost\":\"7.8.9\"}";
        var serializedFromString = OsVersionsSerializer.fromJson(json.getBytes(StandardCharsets.UTF_8));
        var versions = Map.of(
                NodeType.host, new OsVersion(Version.fromString("1.2.3"), true),
                NodeType.proxyhost, new OsVersion(Version.fromString("4.5.6"), true),
                NodeType.confighost, new OsVersion(Version.fromString("7.8.9"), true)
        );
        assertEquals(versions, serializedFromString);

        var serialized = OsVersionsSerializer.fromJson(OsVersionsSerializer.toJson(versions));
        assertEquals(serialized, versions);
    }

    @Test
    public void serialization() {
        var versions = Map.of(
                NodeType.host, new OsVersion(Version.fromString("1.2.3"), true),
                NodeType.proxyhost, new OsVersion(Version.fromString("4.5.6"), false),
                NodeType.confighost, new OsVersion(Version.fromString("7.8.9"), true)
        );
        var serialized = OsVersionsSerializer.fromJson(OsVersionsSerializer.toJson(versions));
        assertEquals(serialized, versions);
    }

}

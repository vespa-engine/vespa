// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
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

    @Test
    public void ignores_unknown_keys() {
        var jsonWithUnknownKeys = "{\n" +
                                  "  \"foo\": \"bar\",\n" +
                                  "  " +
                                  "\"host\": {\n" +
                                  "    \"version\": \"1.2.3\"\n" +
                                  "  },\n" +
                                  "  " +
                                  "\"proxyhost\": {\n" +
                                  "    \"version\": \"4.5.6\"\n" +
                                  "  },\n" +
                                  "  " +
                                  "\"confighost\": {\n" +
                                  "    \"version\": \"7.8.9\"\n" +
                                  "  }\n" +
                                  "}";
        var versions = Map.of(
                NodeType.host, Version.fromString("1.2.3"),
                NodeType.proxyhost, Version.fromString("4.5.6"),
                NodeType.confighost, Version.fromString("7.8.9")
        );
        assertEquals(versions, OsVersionsSerializer.fromJson(jsonWithUnknownKeys.getBytes(StandardCharsets.UTF_8)));
    }

}

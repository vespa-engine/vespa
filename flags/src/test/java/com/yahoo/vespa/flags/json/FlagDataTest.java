// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.RawFlag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hakonhall
 */
public class FlagDataTest {
    private final String json = "{\n" +
            "    \"id\": \"id1\",\n" +
            "    \"rules\": [\n" +
            "        {\n" +
            "            \"conditions\": [\n" +
            "                {\n" +
            "                    \"type\": \"whitelist\",\n" +
            "                    \"dimension\": \"hostname\",\n" +
            "                    \"values\": [ \"host1\", \"host2\" ]\n" +
            "                },\n" +
            "                {\n" +
            "                    \"type\": \"blacklist\",\n" +
            "                    \"dimension\": \"application\",\n" +
            "                    \"values\": [ \"app1\", \"app2\" ]\n" +
            "                }\n" +
            "            ],\n" +
            "            \"value\": true\n" +
            "        },\n" +
            "        {\n" +
            "            \"conditions\": [\n" +
            "                {\n" +
            "                    \"type\": \"whitelist\",\n" +
            "                    \"dimension\": \"zone\",\n" +
            "                    \"values\": [ \"zone1\", \"zone2\" ]\n" +
            "                }\n" +
            "            ],\n" +
            "            \"value\": false\n" +
            "        }\n" +
            "    ],\n" +
            "    \"attributes\": {\n" +
            "        \"zone\": \"zone1\"\n" +
            "    }\n" +
            "}";

    private final FetchVector vector = new FetchVector();

    @Test
    void test() {
        // Second rule matches with the default zone matching
        verify(Optional.of("false"), vector);

        // First rule matches only if both conditions match
        verify(Optional.of("false"), vector
                .with(FetchVector.Dimension.HOSTNAME, "host1")
                .with(FetchVector.Dimension.APPLICATION_ID, "app2"));
        verify(Optional.of("true"), vector
                .with(FetchVector.Dimension.HOSTNAME, "host1")
                .with(FetchVector.Dimension.APPLICATION_ID, "app3"));

        // Verify unsetting a dimension with null works.
        verify(Optional.of("true"), vector
                .with(FetchVector.Dimension.HOSTNAME, "host1")
                .with(FetchVector.Dimension.APPLICATION_ID, "app3")
                .with(FetchVector.Dimension.APPLICATION_ID, null));

        // No rules apply if zone is overridden to an unknown zone
        verify(Optional.empty(), vector.with(FetchVector.Dimension.ZONE_ID, "unknown zone"));
    }

    private void verify(Optional<String> expectedValue, FetchVector vector) {
        FlagData data = FlagData.deserialize(json);
        assertEquals("id1", data.id().toString());
        Optional<RawFlag> rawFlag = data.resolve(vector);

        if (expectedValue.isPresent()) {
            assertTrue(rawFlag.isPresent());
            assertEquals(expectedValue.get(), rawFlag.get().asJson());
        } else {
            assertFalse(rawFlag.isPresent());
        }

    }
}
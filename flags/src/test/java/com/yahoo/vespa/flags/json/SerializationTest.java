// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.yahoo.test.json.Jackson;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.RawFlag;
import com.yahoo.vespa.flags.UnboundDoubleFlag;
import com.yahoo.vespa.flags.UnboundFlag;
import com.yahoo.vespa.flags.file.FlagDbFile;
import com.yahoo.vespa.flags.json.wire.WireCondition;
import com.yahoo.vespa.flags.json.wire.WireFlagData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hakonhall
 */
public class SerializationTest {
    @Test
    void emptyJson() throws IOException {
        String json = "{\"id\":\"id1\"}";
        WireFlagData wireData = WireFlagData.deserialize(json);
        assertThat(wireData.id, equalTo("id1"));
        assertThat(wireData.defaultFetchVector, nullValue());
        assertThat(wireData.rules, nullValue());
        assertThat(wireData.serializeToJson(), equalTo(json));

        assertThat(FlagData.deserialize(json).serializeToJson(), equalTo(json));
    }

    @Test
    void deserialization() throws IOException {
        String json = "{\n" +
                "    \"id\": \"id2\",\n" +
                "    \"rules\": [\n" +
                "        {\n" +
                "            \"conditions\": [\n" +
                "                {\n" +
                "                    \"type\": \"whitelist\",\n" +
                "                    \"dimension\": \"application\",\n" +
                "                    \"values\": [ \"a1\", \"a2\" ]\n" +
                "                },\n" +
                "                {\n" +
                "                    \"type\": \"blacklist\",\n" +
                "                    \"dimension\": \"hostname\",\n" +
                "                    \"values\": [ \"h1\" ]\n" +
                "                },\n" +
                "                {\n" +
                "                    \"type\": \"relational\",\n" +
                "                    \"dimension\": \"vespa-version\",\n" +
                "                    \"predicate\": \">=7.3.4\"\n" +
                "                }\n" +
                "            ],\n" +
                "            \"value\": true\n" +
                "        }\n" +
                "    ],\n" +
                "    \"attributes\": {\n" +
                "        \"zone\": \"z1\",\n" +
                "        \"application\": \"a1\",\n" +
                "        \"hostname\": \"h1\",\n" +
                "        \"node-type\": \"nt1\"\n" +
                "    }\n" +
                "}";

        WireFlagData wireData = WireFlagData.deserialize(json);

        assertThat(wireData.id, equalTo("id2"));
        // rule
        assertThat(wireData.rules.size(), equalTo(1));
        assertThat(wireData.rules.get(0).andConditions.size(), equalTo(3));
        assertThat(wireData.rules.get(0).value.getNodeType(), equalTo(JsonNodeType.BOOLEAN));
        assertThat(wireData.rules.get(0).value.asBoolean(), equalTo(true));
        // first condition
        WireCondition whitelistCondition = wireData.rules.get(0).andConditions.get(0);
        assertThat(whitelistCondition.type, equalTo("whitelist"));
        assertThat(whitelistCondition.dimension, equalTo("application"));
        assertThat(whitelistCondition.values, equalTo(List.of("a1", "a2")));
        // second condition
        WireCondition blacklistCondition = wireData.rules.get(0).andConditions.get(1);
        assertThat(blacklistCondition.type, equalTo("blacklist"));
        assertThat(blacklistCondition.dimension, equalTo("hostname"));
        assertThat(blacklistCondition.values, equalTo(List.of("h1")));
        // third condition
        WireCondition relationalCondition = wireData.rules.get(0).andConditions.get(2);
        assertThat(relationalCondition.type, equalTo("relational"));
        assertThat(relationalCondition.dimension, equalTo("vespa-version"));
        assertThat(relationalCondition.predicate, equalTo(">=7.3.4"));

        // attributes
        assertThat(wireData.defaultFetchVector, notNullValue());
        assertThat(wireData.defaultFetchVector.get("zone"), equalTo("z1"));
        assertThat(wireData.defaultFetchVector.get("application"), equalTo("a1"));
        assertThat(wireData.defaultFetchVector.get("hostname"), equalTo("h1"));
        assertThat(wireData.defaultFetchVector.get("node-type"), equalTo("nt1"));

        // Verify serialization of RawFlag == serialization by ObjectMapper
        var mapper = Jackson.mapper();
        String serializedWithObjectMapper = Jackson.mapper().writeValueAsString(mapper.readTree(json));
        assertThat(wireData.serializeToJson(), equalTo(serializedWithObjectMapper));

        // Unfortunately the order of attributes members are different...
        // assertThat(FlagData.deserialize(json).serializeToJson(), equalTo(serializedWithObjectMapper));
    }

    @Test
    void jsonWithStrayFields() {
        String json = """
                      {
                          "id": "id3",
                          "foo": true,
                          "rules": [
                              {
                                  "conditions": [
                                      {
                                          "type": "whitelist",
                                          "dimension": "zone",
                                          "bar": "zoo"
                                      }
                                  ],
                                  "other": true
                              }
                          ],
                          "attributes": {
                          }
                      }""";

        WireFlagData wireData = WireFlagData.deserialize(json);

        assertThat(wireData.rules.size(), equalTo(1));
        assertThat(wireData.rules.get(0).andConditions.size(), equalTo(1));
        WireCondition whitelistCondition = wireData.rules.get(0).andConditions.get(0);
        assertThat(whitelistCondition.type, equalTo("whitelist"));
        assertThat(whitelistCondition.dimension, equalTo("zone"));
        assertThat(whitelistCondition.values, nullValue());
        assertThat(wireData.rules.get(0).value, nullValue());
        assertTrue(wireData.defaultFetchVector.isEmpty());

        assertThat(wireData.serializeToJson(), equalTo("{\"id\":\"id3\",\"rules\":[{\"conditions\":[{\"type\":\"whitelist\",\"dimension\":\"zone\"}]}],\"attributes\":{}}"));

        assertThat(FlagData.deserialize(json).serializeToJson(), equalTo("{\"id\":\"id3\"}"));
    }

    @Test
    void deserializationOfDouble() {
        assertEquals(3.0, deserializedValue("3"), 1e-4);
        assertEquals(3.0, deserializedValue("3.0"), 1e-4);
    }

    private double deserializedValue(String jsonValue) {
        try (var cleanup = Flags.clearFlagsForTesting()) {
            String id = "id1";
            UnboundFlag<Double, ?, ?> doubleFlag = Flags.defineDoubleFlag(id, 1.2, List.of(), "1970-01-01", "2100-01-01", "description", "modification effect");
            String json = """
                          {
                              "id": "%s",
                              "rules": [
                                  {
                                      "value": %s
                                  }
                              ]
                          }
                          """.formatted(id, jsonValue);
            FlagData data = FlagData.deserialize(json);
            FlagSource flagSource = new SimpleFlagSource(data);
            return doubleFlag.bindTo(flagSource).boxedValue();
        }
    }

    public record SimpleFlagSource(FlagData flagData) implements FlagSource {
        @Override
        public Optional<RawFlag> fetch(FlagId id, FetchVector vector) {
            return flagData.id().equals(id) ? flagData.resolve(vector) : Optional.empty();
        }
    }
}

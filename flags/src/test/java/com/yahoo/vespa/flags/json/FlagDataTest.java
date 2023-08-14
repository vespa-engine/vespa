// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.text.JSON;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.RawFlag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hakonhall
 */
public class FlagDataTest {
    private final String json = """
                                {
                                    "id": "id1",
                                    "rules": [
                                        {
                                            "conditions": [
                                                {
                                                    "type": "whitelist",
                                                    "dimension": "hostname",
                                                    "values": [ "host1", "host2" ]
                                                },
                                                {
                                                    "type": "blacklist",
                                                    "dimension": "application",
                                                    "values": [ "app1", "app2" ]
                                                }
                                            ],
                                            "value": true
                                        },
                                        {
                                            "conditions": [
                                                {
                                                    "type": "whitelist",
                                                    "dimension": "zone",
                                                    "values": [ "zone1", "zone2" ]
                                                }
                                            ],
                                            "value": false
                                        }
                                    ],
                                    "attributes": {
                                        "zone": "zone1"
                                    }
                                }""";

    private final FetchVector vector = new FetchVector();

    @Test
    void testResolve() {
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

    @Test
    void testPartialResolve() {
        FlagData data = FlagData.deserialize(json);
        assertEquals(data.partialResolve(vector), data);
        assertEquals(data.partialResolve(vector.with(FetchVector.Dimension.APPLICATION_ID, "app1")),
                     FlagData.deserialize("""
                                {
                                    "id": "id1",
                                    "rules": [
                                        {
                                            "conditions": [
                                                {
                                                    "type": "whitelist",
                                                    "dimension": "zone",
                                                    "values": [ "zone1", "zone2" ]
                                                }
                                            ],
                                            "value": false
                                        }
                                    ],
                                    "attributes": {
                                        "zone": "zone1"
                                    }
                                }"""));

        assertEquals(data.partialResolve(vector.with(FetchVector.Dimension.APPLICATION_ID, "app1")),
                     FlagData.deserialize("""
                                {
                                    "id": "id1",
                                    "rules": [
                                        {
                                            "conditions": [
                                                {
                                                    "type": "whitelist",
                                                    "dimension": "zone",
                                                    "values": [ "zone1", "zone2" ]
                                                }
                                            ],
                                            "value": false
                                        }
                                    ],
                                    "attributes": {
                                        "zone": "zone1"
                                    }
                                }"""));

        assertEquals(data.partialResolve(vector.with(FetchVector.Dimension.APPLICATION_ID, "app3")),
                     FlagData.deserialize("""
                                {
                                    "id": "id1",
                                    "rules": [
                                        {
                                            "conditions": [
                                                {
                                                    "type": "whitelist",
                                                    "dimension": "hostname",
                                                    "values": [ "host1", "host2" ]
                                                }
                                            ],
                                            "value": true
                                        },
                                        {
                                            "conditions": [
                                                {
                                                    "type": "whitelist",
                                                    "dimension": "zone",
                                                    "values": [ "zone1", "zone2" ]
                                                }
                                            ],
                                            "value": false
                                        }
                                    ],
                                    "attributes": {
                                        "zone": "zone1"
                                    }
                                }"""));

        assertEquals(data.partialResolve(vector.with(FetchVector.Dimension.APPLICATION_ID, "app3")
                                               .with(FetchVector.Dimension.HOSTNAME, "host1")),
                     FlagData.deserialize("""
                                {
                                    "id": "id1",
                                    "rules": [
                                        {
                                            "value": true
                                        }
                                    ],
                                    "attributes": {
                                        "zone": "zone1"
                                    }
                                }"""));

        assertEquals(data.partialResolve(vector.with(FetchVector.Dimension.APPLICATION_ID, "app3")
                                               .with(FetchVector.Dimension.HOSTNAME, "host3")),
                     FlagData.deserialize("""
                                {
                                    "id": "id1",
                                    "rules": [
                                        {
                                            "conditions": [
                                                {
                                                    "type": "whitelist",
                                                    "dimension": "zone",
                                                    "values": [ "zone1", "zone2" ]
                                                }
                                            ],
                                            "value": false
                                        }
                                    ],
                                    "attributes": {
                                        "zone": "zone1"
                                    }
                                }"""));

        assertEquals(data.partialResolve(vector.with(FetchVector.Dimension.APPLICATION_ID, "app3")
                                               .with(FetchVector.Dimension.HOSTNAME, "host3")
                                               .with(FetchVector.Dimension.ZONE_ID, "zone2")),
                     FlagData.deserialize("""
                                {
                                    "id": "id1",
                                    "rules": [
                                        {
                                            "value": false
                                        }
                                    ]
                                }"""));

        FlagData fullyResolved = data.partialResolve(vector.with(FetchVector.Dimension.APPLICATION_ID, "app3")
                                                           .with(FetchVector.Dimension.HOSTNAME, "host3")
                                                           .with(FetchVector.Dimension.ZONE_ID, "zone3"));
        assertEquals(fullyResolved, FlagData.deserialize("""
                                {
                                    "id": "id1"
                                }"""));
        assertTrue(fullyResolved.isEmpty());
    }

    @Test
    void testRemovalOfSentinelRuleWithNullValue() {
        FlagData data = FlagData.deserialize("""
                                             {
                                                 "id": "id1",
                                                 "rules": [
                                                     {
                                                         "conditions": [
                                                             {
                                                                 "type": "whitelist",
                                                                 "dimension": "zone",
                                                                 "values": [ "zone1", "zone2" ]
                                                             }
                                                         ],
                                                         "value": null
                                                     }
                                                 ]
                                             }""");
        assertEquals(data, new FlagData(data.id(), new FetchVector(), List.of()));
        assertTrue(data.isEmpty());
    }

    @Test
    void testRemovalOfSentinelRuleWithoutValue() {
        String json = """
                        {
                            "id": "id1",
                            "rules": [
                                {
                                    "conditions": [
                                        {
                                            "type": "whitelist",
                                            "dimension": "zone",
                                            "values": [ "zone1", "zone2" ]
                                        }
                                    ]
                                },
                                {
                                    "conditions": [
                                        {
                                            "type": "whitelist",
                                            "dimension": "cloud",
                                            "values": [ "aws" ]
                                        }
                                    ],
                                    "value": true
                                }
                            ]
                        }""";
        FlagData data = FlagData.deserialize(json);
        assertTrue(JSON.equals(data.serializeToJson(), json));
        FlagData flagData = data.partialResolve(vector.with(FetchVector.Dimension.CLOUD, "gcp"));
        assertEquals(flagData, new FlagData(data.id(), new FetchVector(), List.of()));
        assertTrue(flagData.isEmpty());
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
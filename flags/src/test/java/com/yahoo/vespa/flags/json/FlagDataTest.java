// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.text.JSON;
import com.yahoo.vespa.flags.Dimension;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.JsonNodeRawFlag;
import com.yahoo.vespa.flags.RawFlag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
                                                    "dimension": "instance",
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
                .with(Dimension.HOSTNAME, "host1")
                .with(Dimension.INSTANCE_ID, "app2"));
        verify(Optional.of("true"), vector
                .with(Dimension.HOSTNAME, "host1")
                .with(Dimension.INSTANCE_ID, "app3"));

        // Verify unsetting a dimension with null works.
        verify(Optional.of("true"), vector
                .with(Dimension.HOSTNAME, "host1")
                .with(Dimension.INSTANCE_ID, "app3")
                .with(Dimension.INSTANCE_ID, null));

        // No rules apply if zone is overridden to an unknown zone
        verify(Optional.empty(), vector.with(Dimension.ZONE_ID, "unknown zone"));
    }

    @Test
    void testPartialResolve() {
        FlagData data = FlagData.deserialize(json);
        assertEquals(data.partialResolve(vector), data);
        assertEquals(data.partialResolve(vector.with(Dimension.INSTANCE_ID, "app1")),
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

        assertEquals(data.partialResolve(vector.with(Dimension.INSTANCE_ID, "app1")),
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

        assertEquals(data.partialResolve(vector.with(Dimension.INSTANCE_ID, "app3")),
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

        assertEquals(data.partialResolve(vector.with(Dimension.INSTANCE_ID, "app3")
                                               .with(Dimension.HOSTNAME, "host1")),
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

        assertEquals(data.partialResolve(vector.with(Dimension.INSTANCE_ID, "app3")
                                               .with(Dimension.HOSTNAME, "host3")),
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

        assertEquals(data.partialResolve(vector.with(Dimension.INSTANCE_ID, "app3")
                                               .with(Dimension.HOSTNAME, "host3")
                                               .with(Dimension.ZONE_ID, "zone2")),
                     FlagData.deserialize("""
                                {
                                    "id": "id1",
                                    "rules": [
                                        {
                                            "value": false
                                        }
                                    ]
                                }"""));

        FlagData fullyResolved = data.partialResolve(vector.with(Dimension.INSTANCE_ID, "app3")
                                                           .with(Dimension.HOSTNAME, "host3")
                                                           .with(Dimension.ZONE_ID, "zone3"));
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
        FlagData flagData = data.partialResolve(vector.with(Dimension.CLOUD, "gcp"));
        assertEquals(flagData, new FlagData(data.id(), new FetchVector(), List.of()));
        assertTrue(flagData.isEmpty());
    }

    // --- partialResolve(Map) tests ---

    @Test
    void whitelist_with_no_overlap_drops_rule() {
        Rule rule = whitelistRule(Dimension.HOSTNAME, "host1", "host2");
        Optional<Rule> result = rule.partialResolve(Map.of(Dimension.HOSTNAME, Set.of("host3", "host4")));
        assertTrue(result.isEmpty(), "rule should be dropped when whitelist has no overlap");
    }

    @Test
    void whitelist_with_full_overlap_leaves_condition_unchanged() {
        Rule rule = whitelistRule(Dimension.HOSTNAME, "host1", "host2");
        Optional<Rule> result = rule.partialResolve(Map.of(Dimension.HOSTNAME, Set.of("host1", "host2", "host3")));
        assertTrue(result.isPresent());
        assertEquals(rule, result.get(), "conditions should be unchanged when allowed values is a superset");
    }

    @Test
    void whitelist_with_partial_overlap_narrows_values() {
        Rule rule = whitelistRule(Dimension.HOSTNAME, "host1", "host2", "host3");
        Optional<Rule> result = rule.partialResolve(Map.of(Dimension.HOSTNAME, Set.of("host1", "host3")));
        assertTrue(result.isPresent());
        assertEquals(whitelistRule(Dimension.HOSTNAME, "host1", "host3"), result.get());
    }

    @Test
    void blacklist_with_no_overlap_removes_condition_but_keeps_rule() {
        Rule rule = blacklistRule(Dimension.INSTANCE_ID, "t1:app1:i1", "t2:app2:i2");
        Optional<Rule> result = rule.partialResolve(Map.of(Dimension.INSTANCE_ID, Set.of("t3:app3:i3")));
        assertTrue(result.isPresent());
        assertTrue(result.get().conditions().isEmpty(), "condition should be removed when blacklist has no overlap");
    }

    @Test
    void blacklist_with_full_overlap_leaves_condition_unchanged() {
        Rule rule = blacklistRule(Dimension.TENANT_ID, "tenant1", "tenant2");
        Optional<Rule> result = rule.partialResolve(Map.of(Dimension.TENANT_ID, Set.of("tenant1", "tenant2", "tenant3")));
        assertTrue(result.isPresent());
        assertEquals(rule, result.get());
    }

    @Test
    void blacklist_with_partial_overlap_narrows_values() {
        Rule rule = blacklistRule(Dimension.TENANT_ID, "tenant1", "tenant2", "tenant3");
        Optional<Rule> result = rule.partialResolve(Map.of(Dimension.TENANT_ID, Set.of("tenant1", "tenant3")));
        assertTrue(result.isPresent());
        assertEquals(blacklistRule(Dimension.TENANT_ID, "tenant1", "tenant3"), result.get());
    }

    @Test
    void unknown_dimension_not_in_allowed_values_keeps_condition_unchanged() {
        Rule rule = whitelistRule(Dimension.ZONE_ID, "prod.us-east-3", "prod.us-west-1");
        // ZONE_ID not present in allowedValues — keep as-is
        Optional<Rule> result = rule.partialResolve(Map.of(Dimension.HOSTNAME, Set.of("host1")));
        assertTrue(result.isPresent());
        assertEquals(rule, result.get());
    }

    @Test
    void multiple_conditions_both_narrowed() {
        Rule rule = new Rule(boolValue(true),
                             whitelistCondition(Dimension.HOSTNAME, "host1", "host2"),
                             blacklistCondition(Dimension.TENANT_ID, "tenant1", "tenant2"));
        Optional<Rule> result = rule.partialResolve(Map.of(
                Dimension.HOSTNAME, Set.of("host1", "host3"),
                Dimension.TENANT_ID, Set.of("tenant3")));  // blacklist has no overlap → condition removed
        assertTrue(result.isPresent());
        assertEquals(new Rule(boolValue(true), whitelistCondition(Dimension.HOSTNAME, "host1")), result.get());
    }

    @Test
    void whitelist_drop_with_multiple_conditions_drops_whole_rule() {
        Rule rule = new Rule(boolValue(true),
                             whitelistCondition(Dimension.HOSTNAME, "host1"),
                             blacklistCondition(Dimension.TENANT_ID, "tenant1"));
        // hostname whitelist has no overlap → entire rule dropped
        Optional<Rule> result = rule.partialResolve(Map.of(
                Dimension.HOSTNAME, Set.of("host2"),
                Dimension.TENANT_ID, Set.of("tenant1")));
        assertTrue(result.isEmpty());
    }

    @Test
    void flag_with_no_rules_is_unchanged() {
        FlagData data = new FlagData(new FlagId("flag1"), new FetchVector());
        FlagData result = data.partialResolve(Map.of(Dimension.HOSTNAME, Set.of("host1")));
        assertEquals(data, result);
    }

    @Test
    void flag_with_unconditional_rule_is_unchanged() {
        FlagData data = FlagData.deserialize("{\"id\":\"flag1\",\"rules\":[{\"value\":true}]}");
        FlagData result = data.partialResolve(Map.of(Dimension.HOSTNAME, Set.of("host1")));
        assertEquals(data, result);
    }

    @Test
    void first_rule_dropped_second_kept() {
        FlagData data = FlagData.deserialize("""
                {
                    "id": "flag1",
                    "rules": [
                        {
                            "conditions": [{"type":"whitelist","dimension":"tenant","values":["tenant1"]}],
                            "value": true
                        },
                        {
                            "conditions": [{"type":"whitelist","dimension":"hostname","values":["host1","host2"]}],
                            "value": false
                        }
                    ]
                }""");

        FlagData result = data.partialResolve(Map.of(
                Dimension.TENANT_ID, Set.of("tenant2"),
                Dimension.HOSTNAME, Set.of("host1")));

        assertEquals(FlagData.deserialize("""
                {
                    "id": "flag1",
                    "rules": [
                        {
                            "conditions": [{"type":"whitelist","dimension":"hostname","values":["host1"]}],
                            "value": false
                        }
                    ]
                }"""), result);
    }

    @Test
    void unconditional_rule_after_narrowing_stops_processing() {
        FlagData data = FlagData.deserialize("""
                {
                    "id": "flag1",
                    "rules": [
                        {
                            "conditions": [{"type":"blacklist","dimension":"tenant","values":["tenant2","tenant3"]}],
                            "value": true
                        },
                        {
                            "conditions": [{"type":"whitelist","dimension":"hostname","values":["host2"]}],
                            "value": false
                        }
                    ]
                }""");

        FlagData result = data.partialResolve(Map.of(
                Dimension.TENANT_ID, Set.of("tenant1"),
                Dimension.HOSTNAME, Set.of("host1")));

        assertEquals(FlagData.deserialize("{\"id\":\"flag1\",\"rules\":[{\"value\":true}]}"), result);
    }

    @Test
    void all_rules_dropped_yields_empty_flag() {
        FlagData data = FlagData.deserialize("""
                {
                    "id": "flag1",
                    "rules": [
                        {
                            "conditions": [{"type":"whitelist","dimension":"tenant","values":["tenant1"]}],
                            "value": true
                        }
                    ]
                }""");

        assertTrue(data.partialResolve(Map.of(Dimension.TENANT_ID, Set.of("tenant2"))).isEmpty());
    }

    private static Optional<RawFlag> boolValue(boolean value) {
        return Optional.of(JsonNodeRawFlag.fromJson(String.valueOf(value)));
    }

    private static Rule whitelistRule(Dimension dimension, String... values) {
        return new Rule(boolValue(true), whitelistCondition(dimension, values));
    }

    private static Rule blacklistRule(Dimension dimension, String... values) {
        return new Rule(boolValue(true), blacklistCondition(dimension, values));
    }

    private static Condition whitelistCondition(Dimension dimension, String... values) {
        return new Condition.CreateParams(dimension).withValues(values).createAs(Condition.Type.WHITELIST);
    }

    private static Condition blacklistCondition(Dimension dimension, String... values) {
        return new Condition.CreateParams(dimension).withValues(values).createAs(Condition.Type.BLACKLIST);
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

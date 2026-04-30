// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
class CloudResourceTagsTest {

    @Test
    void empty_tags() {
        CloudResourceTags tags = CloudResourceTags.empty();
        assertTrue(tags.isEmpty());
        assertEquals(0, tags.size());
        assertEquals(Map.of(), tags.asMap());
    }

    @Test
    void from_empty_map_returns_empty() {
        assertEquals(CloudResourceTags.empty(), CloudResourceTags.from(Map.of()));
    }

    @Test
    void basic_construction() {
        var tags = CloudResourceTags.from(Map.of("env", "prod", "team", "search"));
        assertFalse(tags.isEmpty());
        assertEquals(2, tags.size());
        assertEquals("prod", tags.asMap().get("env"));
        assertEquals("search", tags.asMap().get("team"));
    }

    @Test
    void map_is_unmodifiable() {
        var tags = CloudResourceTags.from(Map.of("key", "value"));
        assertThrows(UnsupportedOperationException.class, () -> tags.asMap().put("new", "entry"));
    }

    @Test
    void equals_and_hashcode() {
        var tags1 = CloudResourceTags.from(Map.of("a", "1", "b", "2"));
        var tags2 = CloudResourceTags.from(Map.of("b", "2", "a", "1"));
        assertEquals(tags1, tags2);
        assertEquals(tags1.hashCode(), tags2.hashCode());

        var tags3 = CloudResourceTags.from(Map.of("a", "1", "b", "3"));
        assertNotEquals(tags1, tags3);
    }

    @Test
    void merge_with_empty() {
        var tags = CloudResourceTags.from(Map.of("a", "1"));
        assertEquals(tags, tags.mergedWith(CloudResourceTags.empty()));
        assertEquals(tags, CloudResourceTags.empty().mergedWith(tags));
    }

    @Test
    void merge_combines_tags() {
        var base = CloudResourceTags.from(Map.of("a", "1", "b", "2"));
        var overlay = CloudResourceTags.from(Map.of("b", "override", "c", "3"));
        var merged = base.mergedWith(overlay);

        assertEquals(3, merged.size());
        assertEquals("1", merged.asMap().get("a"));
        assertEquals("override", merged.asMap().get("b"));
        assertEquals("3", merged.asMap().get("c"));
    }

    @Test
    void empty_key_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("", "value")));
    }

    @Test
    void key_exceeding_max_length_rejected() {
        String longKey = "k".repeat(64);
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of(longKey, "value")));
    }

    @Test
    void value_exceeding_max_length_rejected() {
        String longValue = "v".repeat(64);
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("key", longValue)));
    }

    @Test
    void max_length_keys_and_values_accepted() {
        var tags = CloudResourceTags.from(Map.of("k".repeat(63), "v".repeat(63)));
        assertEquals(1, tags.size());
    }

    @Test
    void too_many_tags_rejected() {
        Map<String, String> tooMany = IntStream.rangeClosed(1, 21)
                                               .boxed()
                                               .collect(toMap(i -> "key" + i, i -> "val" + i));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(tooMany));
    }

    @Test
    void max_allowed_tags_accepted() {
        Map<String, String> maxTags = IntStream.rangeClosed(1, 20)
                                               .boxed()
                                               .collect(toMap(i -> "key" + i, i -> "val" + i));
        var tags = CloudResourceTags.from(maxTags);
        assertEquals(20, tags.size());
    }

    @Test
    void empty_value_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("key", "")));
    }

    @Test
    void key_with_invalid_characters_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("My Key", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("KEY", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("key.name", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("123key", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("_key", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("-key", "value")));
    }

    @Test
    void value_with_invalid_characters_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("key", "My Value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("key", "VALUE")));
    }

    @Test
    void vai_prefix_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("vai_custom", "value")));
    }

    @Test
    void reserved_tag_names_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("applicationid", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("athenz", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("athenz-domain", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("athenzservice", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("fqdn", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("name", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("owner", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("zone", "value")));
    }

    @Test
    void reserved_key_prefixes_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("corp_tag", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("bastion_tag", "value")));
    }

    @Test
    void template_variables_in_values_accepted() {
        // Pure template variable
        var tags1 = CloudResourceTags.from(Map.of("env", "${environment}"));
        assertEquals("${environment}", tags1.asMap().get("env"));

        // Template variable mixed with literal text
        var tags2 = CloudResourceTags.from(Map.of("env", "prefix-${region}"));
        assertEquals("prefix-${region}", tags2.asMap().get("env"));

        // Multiple template variables
        var tags3 = CloudResourceTags.from(Map.of("env", "${environment}-${region}"));
        assertEquals("${environment}-${region}", tags3.asMap().get("env"));
    }

    @Test
    void template_variables_with_invalid_literal_parts_rejected() {
        assertThrows(IllegalArgumentException.class,
                     () -> CloudResourceTags.from(Map.of("env", "UPPER${environment}")));
        assertThrows(IllegalArgumentException.class,
                     () -> CloudResourceTags.from(Map.of("env", "${environment} space")));
    }

    @Test
    void valid_key_patterns_accepted() {
        var tags = CloudResourceTags.from(Map.of("my-key", "value", "my-key2", "value2", "k123", "v456"));
        assertEquals(3, tags.size());
    }

    @Test
    void resolve_deployment_substitutes_placeholders() {
        var tags = CloudResourceTags.from(Map.of(
                "env", "${environment}",
                "loc", "${region}",
                "team", "${tenant}-${application}-${instance}"));
        var resolved = tags.resolveDeployment("Tenant1", "App1", "Default", "prod", "aws-us-east-1c");
        assertEquals("prod", resolved.asMap().get("env"));
        assertEquals("aws-us-east-1c", resolved.asMap().get("loc"));
        assertEquals("tenant1-app1-default", resolved.asMap().get("team"));
    }

    @Test
    void resolve_deployment_passes_through_cluster_placeholders() {
        var tags = CloudResourceTags.from(Map.of(
                "tag", "${environment}-${clustertype}"));
        var resolved = tags.resolveDeployment("t", "a", "i", "prod", "r");
        assertEquals("prod-${clustertype}", resolved.asMap().get("tag"));
    }

    @Test
    void resolve_deployment_on_empty_returns_empty() {
        var resolved = CloudResourceTags.empty().resolveDeployment("t", "a", "i", "prod", "r");
        assertTrue(resolved.isEmpty());
    }

    @Test
    void validate_placeholders_accepts_all_known() {
        var tags = CloudResourceTags.from(Map.of(
                "a", "${tenant}-${application}-${instance}",
                "b", "${environment}-${region}",
                "c", "${clustername}-${clustertype}"));
        tags.validatePlaceholders();
    }

    @Test
    void validate_placeholders_rejects_unknown() {
        var tags = CloudResourceTags.from(Map.of("bad", "${unknown}"));
        var e = assertThrows(IllegalArgumentException.class, tags::validatePlaceholders);
        assertTrue(e.getMessage().contains("Unknown template variable"));
    }

    @Test
    void resolve_cluster_substitutes_placeholders() {
        var tags = CloudResourceTags.from(Map.of(
                "cluster", "${clustername}",
                "type", "${clustertype}",
                "combined", "${clustername}-${clustertype}"));
        var resolved = tags.resolveCluster(ClusterSpec.Id.from("my-search"), ClusterSpec.Type.content);
        assertEquals("my-search", resolved.asMap().get("cluster"));
        assertEquals("content", resolved.asMap().get("type"));
        assertEquals("my-search-content", resolved.asMap().get("combined"));
    }

    @Test
    void resolve_cluster_lowercases_cluster_id() {
        var tags = CloudResourceTags.from(Map.of("cluster", "${clustername}"));
        var resolved = tags.resolveCluster(ClusterSpec.Id.from("MyCluster"), ClusterSpec.Type.container);
        assertEquals("mycluster", resolved.asMap().get("cluster"));
    }

    @Test
    void resolve_cluster_leaves_other_placeholders_untouched_and_throws() {
        var tags = CloudResourceTags.from(Map.of("tag", "${environment}-${clustername}"));
        var e = assertThrows(IllegalArgumentException.class,
                             () -> tags.resolveCluster(ClusterSpec.Id.from("search"), ClusterSpec.Type.content));
        assertTrue(e.getMessage().contains("Unresolved template variable"));
    }

    @Test
    void resolve_cluster_on_empty_tags_returns_empty() {
        var resolved = CloudResourceTags.empty().resolveCluster(ClusterSpec.Id.from("x"), ClusterSpec.Type.admin);
        assertTrue(resolved.isEmpty());
    }

    @Test
    void resolve_cluster_on_tags_without_placeholders() {
        var tags = CloudResourceTags.from(Map.of("env", "prod"));
        var resolved = tags.resolveCluster(ClusterSpec.Id.from("search"), ClusterSpec.Type.content);
        assertEquals("prod", resolved.asMap().get("env"));
    }

    @Test
    void contains_cluster_placeholders() {
        assertFalse(CloudResourceTags.empty().containsClusterPlaceholders());
        assertFalse(CloudResourceTags.from(Map.of("env", "prod")).containsClusterPlaceholders());
        assertTrue(CloudResourceTags.from(Map.of("cluster", "${clustername}")).containsClusterPlaceholders());
        assertTrue(CloudResourceTags.from(Map.of("type", "${clustertype}")).containsClusterPlaceholders());
        assertTrue(CloudResourceTags.from(Map.of("tag", "prefix-${clustername}")).containsClusterPlaceholders());
    }

    @Test
    void to_string() {
        var tags = CloudResourceTags.from(Map.of("env", "prod"));
        assertTrue(tags.toString().contains("env"));
        assertTrue(tags.toString().contains("prod"));
    }

}

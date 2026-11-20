// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    // --- Structural validation (from()) ---

    @Test
    void empty_key_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("", "value")));
    }

    @Test
    void empty_value_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("key", "")));
    }

    @Test
    void key_exceeding_structural_max_length_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("k".repeat(513), "value")));
    }

    @Test
    void value_exceeding_structural_max_length_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("key", "v".repeat(257))));
    }

    @Test
    void max_length_keys_and_values_accepted() {
        var tags = CloudResourceTags.from(Map.of("k".repeat(512), "v".repeat(256)));
        assertEquals(1, tags.size());
    }

    @Test
    void exceeding_structural_max_tags_rejected() {
        Map<String, String> tooMany = IntStream.rangeClosed(1, 65)
                                               .boxed()
                                               .collect(toMap(i -> "key" + i, i -> "val" + i));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(tooMany));
    }

    @Test
    void structural_max_tags_accepted() {
        Map<String, String> maxTags = IntStream.rangeClosed(1, 64)
                                               .boxed()
                                               .collect(toMap(i -> "key" + i, i -> "val" + i));
        assertEquals(64, CloudResourceTags.from(maxTags).size());
    }

    @Test
    void null_bytes_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("key\0", "value")));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("key", "val\0ue")));
    }

    @Test
    void permissive_key_characters_accepted() {
        var tags = CloudResourceTags.from(Map.of(
                "My Key", "value",
                "KEY", "value2",
                "key.name", "value3",
                "indeed:cost_tag_01", "value4",
                "key/path", "value5",
                "key+extra=1@host", "value6"
        ));
        assertEquals(6, tags.size());
    }

    @Test
    void permissive_value_characters_accepted() {
        var tags = CloudResourceTags.from(Map.of(
                "key1", "My Value with spaces",
                "key2", "value.with.dots",
                "key3", "UPPERCASE",
                "key4", "path/to/thing",
                "key5", "a:b:c"
        ));
        assertEquals(5, tags.size());
    }

    @Test
    void reserved_keys_are_accepted_by_from() {
        var tags = CloudResourceTags.from(Map.of("vai_os_version", "9.4", "owner", "value", "env", "prod"));
        assertEquals(3, tags.size());
    }

    @Test
    void twenty_customer_tags_and_a_platform_tag_are_accepted_by_from_but_not_as_customer_tags() {
        Map<String, String> map = new HashMap<>(IntStream.rangeClosed(1, 20).boxed()
                .collect(toMap(i -> "key" + i, i -> "val" + i)));
        map.put("vai_os_version", "9.4");
        var tags = CloudResourceTags.from(map);
        assertEquals(21, tags.size());
        assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.AWS));
    }

    @Test
    void reserved_tag_names_rejected() {
        for (String key : new String[] { "applicationid", "athenz", "athenz-domain", "athenzservice", "fqdn", "name",
                                         "owner", "zone", "tenant", "tenantName", "app", "clusterid", "system",
                                         "application", "cluster", "generation", "auth-method", "preprovisioned" })
            assertRejectedAsCustomerTag(key);
    }

    @Test
    void reserved_tag_names_rejected_case_insensitively() {
        // System tags like "Name" (capital N) must collide with reserved "name".
        assertRejectedAsCustomerTag("Name");
        assertRejectedAsCustomerTag("APPLICATIONID");
        assertRejectedAsCustomerTag("Owner");
    }

    @Test
    void reserved_key_prefixes_rejected() {
        for (String key : new String[] { "vai_tag", "corp_tag", "corp:tag", "bastion_tag", "bastion:tag" })
            assertRejectedAsCustomerTag(key);
    }

    @Test
    void reserved_key_prefixes_rejected_case_insensitively() {
        for (String key : new String[] { "VAI_tag", "CORP_tag", "CORP:tag", "Bastion_tag", "Bastion:tag" })
            assertRejectedAsCustomerTag(key);
    }

    private static void assertRejectedAsCustomerTag(String key) {
        var tags = CloudResourceTags.from(Map.of(key, "value"));
        for (CloudName cloud : new CloudName[] { CloudName.AWS, CloudName.AZURE }) {
            var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(cloud),
                                 "Should reject key: " + key);
            assertTrue(e.getMessage().contains("reserved by the platform"), e.getMessage());
        }
    }

    // --- Template variables ---

    @Test
    void template_variables_in_values_accepted() {
        var tags1 = CloudResourceTags.from(Map.of("env", "${environment}"));
        assertEquals("${environment}", tags1.asMap().get("env"));

        var tags2 = CloudResourceTags.from(Map.of("env", "prefix-${region}"));
        assertEquals("prefix-${region}", tags2.asMap().get("env"));

        var tags3 = CloudResourceTags.from(Map.of("env", "${environment}-${region}"));
        assertEquals("${environment}-${region}", tags3.asMap().get("env"));
    }

    @Test
    void template_variables_mixed_with_literals_accepted() {
        var tags = CloudResourceTags.from(Map.of(
                "env1", "UPPER${environment}",
                "env2", "${environment} with space",
                "env3", "prefix:${region}/suffix"
        ));
        assertEquals("UPPER${environment}", tags.asMap().get("env1"));
        assertEquals("${environment} with space", tags.asMap().get("env2"));
        assertEquals("prefix:${region}/suffix", tags.asMap().get("env3"));
    }

    @Test
    void valid_key_patterns_accepted() {
        var tags = CloudResourceTags.from(Map.of(
                "my-key", "value",
                "123key", "value2",
                "_key", "value3",
                "-key", "value4"
        ));
        assertEquals(4, tags.size());
    }

    @Test
    void validate_placeholders_accepts_all_known() {
        var tags = CloudResourceTags.from(Map.of(
                "a", "${tenant}-${application}-${instance}",
                "b", "${environment}-${region}",
                "c", "${clustername}-${clustertype}"));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AWS));
    }

    @Test
    void validate_placeholders_rejects_unknown() {
        var tags = CloudResourceTags.from(Map.of("bad", "${unknown}"));
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.AWS));
        assertTrue(e.getMessage().contains("Unknown template variable"));
    }

    // --- Per-cloud validation: AWS ---

    @Test
    void aws_allows_colons_in_keys() {
        var tags = CloudResourceTags.from(Map.of("indeed:cost_tag_01", "value"));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AWS));
    }

    @Test
    void aws_allows_uppercase_in_keys_and_values() {
        var tags = CloudResourceTags.from(Map.of("MyKey", "MyValue"));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AWS));
    }

    @Test
    void aws_allows_slashes_dots_spaces_in_keys() {
        var tags = CloudResourceTags.from(Map.of("path/to.key with-spaces", "value"));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AWS));
    }

    @Test
    void aws_rejects_forbidden_characters() {
        var tags = CloudResourceTags.from(Map.of("key<bad", "value"));
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.AWS));
        assertTrue(e.getMessage().contains("AWS"));
    }

    @Test
    void aws_rejects_key_exceeding_128_chars() {
        var tags = CloudResourceTags.from(Map.of("k".repeat(129), "value"));
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.AWS));
        assertTrue(e.getMessage().contains("AWS"));
    }

    @Test
    void aws_accepts_value_at_256_char_limit() {
        var tags = CloudResourceTags.from(Map.of("key", "v".repeat(256)));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AWS));
    }

    @Test
    void aws_allows_template_variables_in_values() {
        var tags = CloudResourceTags.from(Map.of("env", "prefix-${environment}-suffix"));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AWS));
    }

    @Test
    void aws_rejects_forbidden_literal_mixed_with_template() {
        var tags = CloudResourceTags.from(Map.of("env", "${environment}<bad>"));
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.AWS));
        assertTrue(e.getMessage().contains("AWS"));
    }

    @Test
    void aws_rejects_more_than_20_tags() {
        Map<String, String> tags21 = IntStream.rangeClosed(1, 21).boxed()
                .collect(toMap(i -> "key" + i, i -> "val" + i));
        var tags = CloudResourceTags.from(tags21);
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.AWS));
        assertTrue(e.getMessage().contains("AWS"));
    }

    // --- Per-cloud validation: GCP ---

    @Test
    void gcp_rejects_uppercase_keys() {
        var tags = CloudResourceTags.from(Map.of("MyKey", "value"));
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.GCP));
        assertTrue(e.getMessage().contains("GCP"));
    }

    @Test
    void gcp_rejects_uppercase_values() {
        var tags = CloudResourceTags.from(Map.of("key", "MyValue"));
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.GCP));
        assertTrue(e.getMessage().contains("GCP"));
    }

    @Test
    void gcp_rejects_colons_in_keys() {
        var tags = CloudResourceTags.from(Map.of("cost:tag", "value"));
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.GCP));
        assertTrue(e.getMessage().contains("GCP"));
    }

    @Test
    void gcp_rejects_key_starting_with_digit() {
        var tags = CloudResourceTags.from(Map.of("1key", "value"));
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.GCP));
        assertTrue(e.getMessage().contains("GCP"));
    }

    @Test
    void gcp_rejects_key_starting_with_underscore_or_hyphen() {
        for (String key : new String[]{"_key", "-key"}) {
            var tags = CloudResourceTags.from(Map.of(key, "value"));
            assertThrows(IllegalArgumentException.class,
                         () -> tags.validateCustomerTags(CloudName.GCP),
                         "Should reject key: " + key);
        }
    }

    @Test
    void gcp_rejects_more_than_20_tags() {
        Map<String, String> tags21 = IntStream.rangeClosed(1, 21).boxed()
                .collect(toMap(i -> "key" + i, i -> "val" + i));
        var tags = CloudResourceTags.from(tags21);
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.GCP));
        assertTrue(e.getMessage().contains("GCP"));
    }

    @Test
    void gcp_rejects_key_exceeding_63_chars() {
        var tags = CloudResourceTags.from(Map.of("k".repeat(64), "value"));
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.GCP));
        assertTrue(e.getMessage().contains("GCP"));
    }

    @Test
    void gcp_rejects_value_exceeding_63_chars() {
        var tags = CloudResourceTags.from(Map.of("key", "v".repeat(64)));
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.GCP));
        assertTrue(e.getMessage().contains("GCP"));
    }

    @Test
    void gcp_allows_valid_lowercase_tags() {
        var tags = CloudResourceTags.from(Map.of("env", "prod", "team-name", "search_team"));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.GCP));
    }

    @Test
    void gcp_allows_template_variables_in_values() {
        var tags = CloudResourceTags.from(Map.of("env", "${environment}-${region}"));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.GCP));
    }

    @Test
    void gcp_rejects_uppercase_literal_mixed_with_template() {
        var tags = CloudResourceTags.from(Map.of("env", "PREFIX-${environment}"));
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.GCP));
        assertTrue(e.getMessage().contains("GCP"));
    }

    // --- Per-cloud validation: Azure ---

    @Test
    void azure_allows_uppercase_in_keys_and_values() {
        var tags = CloudResourceTags.from(Map.of("MyKey", "MyValue"));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AZURE));
    }

    @Test
    void azure_allows_colons_in_keys() {
        var tags = CloudResourceTags.from(Map.of("cost:tag", "value"));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AZURE));
    }

    @Test
    void azure_rejects_forbidden_key_characters() {
        for (String forbidden : new String[]{"key<", "key>", "key%x", "key&x", "key\\x", "key?x", "key/x"}) {
            var tags = CloudResourceTags.from(Map.of(forbidden, "value"));
            var e = assertThrows(IllegalArgumentException.class,
                                 () -> tags.validateCustomerTags(CloudName.AZURE),
                                 "Should reject key: " + forbidden);
            assertTrue(e.getMessage().contains("Azure"));
        }
    }

    @Test
    void azure_allows_forbidden_key_chars_in_values() {
        var tags = CloudResourceTags.from(Map.of("key", "value<with>special%chars&more"));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AZURE));
    }

    @Test
    void azure_accepts_key_at_512_char_limit() {
        var tags = CloudResourceTags.from(Map.of("k".repeat(512), "value"));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AZURE));
    }

    @Test
    void azure_accepts_value_at_256_char_limit() {
        var tags = CloudResourceTags.from(Map.of("key", "v".repeat(256)));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AZURE));
    }

    @Test
    void azure_rejects_more_than_20_tags() {
        Map<String, String> tags21 = IntStream.rangeClosed(1, 21).boxed()
                .collect(toMap(i -> "key" + i, i -> "val" + i));
        var tags = CloudResourceTags.from(tags21);
        var e = assertThrows(IllegalArgumentException.class, () -> tags.validateCustomerTags(CloudName.AZURE));
        assertTrue(e.getMessage().contains("Azure"));
    }

    // --- Per-cloud validation: cross-cloud ---

    @Test
    void empty_tags_accepted_for_all_clouds() {
        var empty = CloudResourceTags.empty();
        assertDoesNotThrow(() -> empty.validateCustomerTags(CloudName.AWS));
        assertDoesNotThrow(() -> empty.validateCustomerTags(CloudName.GCP));
        assertDoesNotThrow(() -> empty.validateCustomerTags(CloudName.AZURE));
    }

    @Test
    void twenty_customer_tags_accepted_for_all_clouds() {
        Map<String, String> tags20 = IntStream.rangeClosed(1, 20).boxed()
                .collect(toMap(i -> "key" + i, i -> "val" + i));
        var tags = CloudResourceTags.from(tags20);
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AWS));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.GCP));
        assertDoesNotThrow(() -> tags.validateCustomerTags(CloudName.AZURE));
    }

    @Test
    void unknown_cloud_throws() {
        var tags = CloudResourceTags.from(Map.of("key", "value"));
        var e = assertThrows(IllegalArgumentException.class,
                             () -> tags.validateCustomerTags(CloudName.from("unknown")));
        assertTrue(e.getMessage().contains("No resource tag validation rules"));
    }

    // --- Resolution ---

    private static final ApplicationId testApp = ApplicationId.from("Tenant1", "App1", "Default");
    private static final Environment testEnv = Environment.prod;
    private static final RegionName testRegion = RegionName.from("aws-us-east-1c");

    @Test
    void resolve_substitutes_all_placeholders() {
        var tags = CloudResourceTags.from(Map.of(
                "env", "${environment}",
                "loc", "${region}",
                "team", "${tenant}-${application}-${instance}",
                "cluster_name", "${clustername}",
                "type", "${clustertype}",
                "combined", "${environment}-${clustername}-${clustertype}"));
        var resolved = tags.resolve(testApp, testEnv, testRegion,
                                    ClusterSpec.Id.from("my-search"), ClusterSpec.Type.content,
                                    CloudName.AWS);
        assertEquals("prod", resolved.asMap().get("env"));
        assertEquals("aws-us-east-1c", resolved.asMap().get("loc"));
        assertEquals("tenant1-app1-default", resolved.asMap().get("team"));
        assertEquals("my-search", resolved.asMap().get("cluster_name"));
        assertEquals("content", resolved.asMap().get("type"));
        assertEquals("prod-my-search-content", resolved.asMap().get("combined"));
    }

    @Test
    void resolve_lowercases_tenant_application_instance_and_cluster_id() {
        var tags = CloudResourceTags.from(Map.of("tag", "${tenant}-${clustername}"));
        var resolved = tags.resolve(testApp, testEnv, testRegion,
                                    ClusterSpec.Id.from("MyCluster"), ClusterSpec.Type.container,
                                    CloudName.AWS);
        assertEquals("tenant1-mycluster", resolved.asMap().get("tag"));
    }

    @Test
    void resolve_on_empty_returns_empty() {
        var resolved = CloudResourceTags.empty().resolve(testApp, testEnv, testRegion,
                                                         ClusterSpec.Id.from("c"), ClusterSpec.Type.admin,
                                                         CloudName.AWS);
        assertTrue(resolved.isEmpty());
    }

    @Test
    void resolve_on_tags_without_placeholders() {
        var tags = CloudResourceTags.from(Map.of("env", "prod"));
        var resolved = tags.resolve(testApp, testEnv, testRegion,
                                    ClusterSpec.Id.from("c"), ClusterSpec.Type.content,
                                    CloudName.AWS);
        assertEquals("prod", resolved.asMap().get("env"));
    }

    @Test
    void resolve_validates_resolved_values_against_cloud_limits() {
        // Raw value is 59 chars and passes GCP's 63-char value limit, but resolves to 64 chars and must fail.
        String prefix = "p".repeat(50);
        var tags = CloudResourceTags.from(Map.of("tag", prefix + "${region}"));
        var e = assertThrows(IllegalArgumentException.class,
                             () -> tags.resolve(testApp, testEnv, testRegion,
                                                ClusterSpec.Id.from("c"), ClusterSpec.Type.content,
                                                CloudName.GCP));
        assertTrue(e.getMessage().contains("GCP"), "error must mention GCP: " + e.getMessage());
    }

    @Test
    void resolve_rejects_reserved_keys() {
        var tags = CloudResourceTags.from(Map.of("vai_os_version", "${environment}"));
        var e = assertThrows(IllegalArgumentException.class,
                             () -> tags.resolve(testApp, testEnv, testRegion,
                                                ClusterSpec.Id.from("c"), ClusterSpec.Type.content,
                                                CloudName.AWS));
        assertTrue(e.getMessage().contains("reserved by the platform"), e.getMessage());
    }

    @Test
    void to_string() {
        var tags = CloudResourceTags.from(Map.of("env", "prod"));
        assertTrue(tags.toString().contains("env"));
        assertTrue(tags.toString().contains("prod"));
    }

}

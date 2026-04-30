// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable key-value tags to apply to cloud resources in custom enclave deployments.
 * These tags are propagated to the underlying cloud provider (AWS, GCP, Azure) and
 * applied to infrastructure resources provisioned for the enclave.
 *
 * @author gjoranv
 */
public class CloudResourceTags {

    private static final CloudResourceTags EMPTY = new CloudResourceTags(Map.of());

    private static final int MAX_KEY_LENGTH = 63;
    private static final int MAX_VALUE_LENGTH = 63;
    private static final int MAX_TAGS = 20;

    /** Keys must start with a lowercase letter (GCP requirement) and contain only [a-z0-9_-]. */
    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");

    /** Values may only contain lowercase alphanumeric characters, hyphens and underscores. */
    private static final Pattern VALID_VALUE_PATTERN = Pattern.compile("[a-z0-9_-]+");

    /** Pattern for template variables, e.g. ${environment}, ${region}. */
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{[^}]+\\}");

    /** System tag names reserved by the platform. */
    private static final List<String> RESERVED_TAG_NAMES = List.of(
            "applicationid", "athenz", "athenz-domain", "athenzservice", "fqdn", "name", "owner", "zone");

    /** Key prefixes reserved by the platform. */
    private static final List<String> RESERVED_KEY_PREFIXES = List.of("vai_", "corp_", "bastion_");

    private static final List<String> DEPLOYMENT_PLACEHOLDERS = List.of(
            "${tenant}", "${application}", "${instance}", "${environment}", "${region}");

    private static final List<String> CLUSTER_PLACEHOLDERS = List.of("${clustername}", "${clustertype}");

    private final Map<String, String> tags;

    private CloudResourceTags(Map<String, String> tags) {
        this.tags = Map.copyOf(tags);
    }

    /** Returns the tags as an unmodifiable map. */
    public Map<String, String> asMap() { return tags; }

    public boolean isEmpty() { return tags.isEmpty(); }

    public int size() { return tags.size(); }

    /**
     * Validates that all {@code ${...}} placeholders in tag values are recognized.
     * Throws on any unknown placeholder. Does not resolve anything.
     */
    public void validatePlaceholders() {
        for (var entry : tags.entrySet()) {
            String remaining = entry.getValue();
            for (String p : DEPLOYMENT_PLACEHOLDERS) remaining = remaining.replace(p, "");
            for (String p : CLUSTER_PLACEHOLDERS) remaining = remaining.replace(p, "");
            if (remaining.contains("${"))
                throw new IllegalArgumentException("Unknown template variable in resource tag value for key '" +
                                                   entry.getKey() + "': " + entry.getValue());
        }
    }

    /**
     * Returns a new instance with deployment-context placeholders
     * ({@code ${tenant}}, {@code ${application}}, etc.) substituted.
     * Cluster-scoped placeholders ({@code ${clustername}}, {@code ${clustertype}}) are not affected.
     */
    public CloudResourceTags resolveDeployment(String tenant, String application, String instance,
                                               String environment, String region) {
        if (tags.isEmpty()) return this;
        Map<String, String> resolved = new LinkedHashMap<>();
        for (var entry : tags.entrySet()) {
            resolved.put(entry.getKey(), entry.getValue()
                    .replace("${tenant}", tenant.toLowerCase(Locale.ROOT))
                    .replace("${application}", application.toLowerCase(Locale.ROOT))
                    .replace("${instance}", instance.toLowerCase(Locale.ROOT))
                    .replace("${environment}", environment)
                    .replace("${region}", region));
        }
        return from(resolved);
    }

    /**
     * Returns a new instance with {@code ${clustername}} and {@code ${clustertype}} substituted.
     * Throws if any {@code ${...}} placeholders remain after substitution. Call
     * {@link #resolveDeployment} first to resolve deployment-context placeholders.
     */
    public CloudResourceTags resolveCluster(ClusterSpec.Id clusterId, ClusterSpec.Type clusterType) {
        if (tags.isEmpty()) return this;
        Map<String, String> resolved = new LinkedHashMap<>();
        for (var entry : tags.entrySet()) {
            String value = entry.getValue()
                    .replace("${clustername}", clusterId.value().toLowerCase(Locale.ROOT))
                    .replace("${clustertype}", clusterType.name());
            if (value.contains("${"))
                throw new IllegalArgumentException("Unresolved template variable in resource tag value for key '" +
                                                   entry.getKey() + "': " + value);
            resolved.put(entry.getKey(), value);
        }
        return from(resolved);
    }

    /** Returns true if any tag value contains cluster-scoped placeholders. */
    public boolean containsClusterPlaceholders() {
        return tags.values().stream().anyMatch(v -> CLUSTER_PLACEHOLDERS.stream().anyMatch(v::contains));
    }

    /**
     * Returns a new instance with the given tags merged into this.
     * Tags from {@code other} take precedence on key conflicts.
     */
    public CloudResourceTags mergedWith(CloudResourceTags other) {
        if (other.isEmpty()) return this;
        if (this.isEmpty()) return other;
        var merged = new LinkedHashMap<>(this.tags);
        merged.putAll(other.tags);
        return from(merged);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return tags.equals(((CloudResourceTags) o).tags);
    }

    @Override
    public int hashCode() { return tags.hashCode(); }

    @Override
    public String toString() { return tags.toString(); }

    public static CloudResourceTags empty() { return EMPTY; }

    /** Creates a new instance from the given map, validating keys and values. */
    public static CloudResourceTags from(Map<String, String> tags) {
        if (tags.isEmpty()) return EMPTY;
        validate(tags);
        return new CloudResourceTags(tags);
    }

    private static void validate(Map<String, String> tags) {
        if (tags.size() > MAX_TAGS)
            throw new IllegalArgumentException("Too many cloud resource tags (" + tags.size() +
                                               "): maximum is " + MAX_TAGS);
        tags.forEach((key, value) -> {
            Objects.requireNonNull(key, "Tag key cannot be null");
            Objects.requireNonNull(value, "Tag value cannot be null");
            if (key.isEmpty())
                throw new IllegalArgumentException("Tag key cannot be empty");
            if (value.isEmpty())
                throw new IllegalArgumentException("Tag value cannot be empty for key '" + key + "'");
            if (key.length() > MAX_KEY_LENGTH)
                throw new IllegalArgumentException("Tag key exceeds " + MAX_KEY_LENGTH +
                                                   " characters: '" + key + "'");
            if (value.length() > MAX_VALUE_LENGTH)
                throw new IllegalArgumentException("Tag value exceeds " + MAX_VALUE_LENGTH +
                                                   " characters for key '" + key + "'");
            if ( ! VALID_KEY_PATTERN.matcher(key).matches())
                throw new IllegalArgumentException("Tag key contains invalid characters: '" + key +
                                                   "'. Must start with a lowercase letter and contain only [a-z0-9_-]");
            String strippedValue = TEMPLATE_VARIABLE.matcher(value).replaceAll("");
            if ( ! strippedValue.isEmpty() && ! VALID_VALUE_PATTERN.matcher(strippedValue).matches())
                throw new IllegalArgumentException("Tag value contains invalid characters for key '" + key +
                                                   "'. Only [a-z0-9_-] and template variables like ${environment} are allowed");
            for (String reserved : RESERVED_TAG_NAMES) {
                if (key.equals(reserved))
                    throw new IllegalArgumentException("Tag key '" + key + "' is reserved by the platform");
            }
            for (String prefix : RESERVED_KEY_PREFIXES) {
                if (key.startsWith(prefix))
                    throw new IllegalArgumentException("Tag key prefix '" + prefix + "' is reserved by the platform: '" + key + "'");
            }
        });
    }

}

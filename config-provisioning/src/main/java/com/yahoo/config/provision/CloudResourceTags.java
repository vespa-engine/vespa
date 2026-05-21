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

    /** Max across all clouds (Azure). Per-cloud limits enforced in {@link #validateFor}. */
    private static final int MAX_KEY_LENGTH = 512;
    /** Max across all clouds (AWS, Azure). Per-cloud limits enforced in {@link #validateFor}. */
    private static final int MAX_VALUE_LENGTH = 256;
    /** Max across all clouds (GCP). Per-cloud limits enforced in {@link #validateFor}. */
    private static final int MAX_TAGS = 64;

    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{[^}]+\\}");

    /**
     * System tag names reserved by the platform. Compared case-insensitively against customer keys.
     * All new tag names must use the 'vai_' prefix.
     */
    private static final List<String> RESERVED_TAG_NAMES = List.of(
            "applicationid", "athenz", "athenz-domain", "athenzservice", "fqdn", "name", "owner", "zone",
            "tenant", "tenantName", "app", "clusterid",
            "system", "application", "cluster", "generation", "auth-method",
            "preprovisioned");

    /** Key prefixes reserved by the platform. Compared case-insensitively against customer keys. */
    private static final List<String> RESERVED_KEY_PREFIXES = List.of("vai_", "corp_", "bastion_");

    private static final List<String> PLACEHOLDERS = List.of(
            "${tenant}", "${application}", "${instance}", "${environment}", "${region}",
            "${clustername}", "${clustertype}");

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
            for (String p : PLACEHOLDERS) remaining = remaining.replace(p, "");
            if (remaining.contains("${"))
                throw new IllegalArgumentException("Unknown template variable in resource tag value for key '" +
                                                   entry.getKey() + "': " + entry.getValue());
        }
    }

    /**
     * Validates these tags against the character set and length limits of the given cloud provider.
     * Template-variable placeholders are stripped from values before checking per-cloud character
     * rules; keys are validated as-is. Callers should also invoke {@link #validatePlaceholders()}
     * to reject unrecognized template variables.
     */
    public void validateFor(CloudName cloud) {
        if (tags.isEmpty()) return;
        if (cloud.equals(CloudName.AWS))        Aws.validate(tags);
        else if (cloud.equals(CloudName.GCP))   Gcp.validate(tags);
        else if (cloud.equals(CloudName.AZURE)) Azure.validate(tags);
        else throw new IllegalArgumentException("No resource tag validation rules for cloud '" + cloud + "'");
    }

    /**
     * Returns a new instance with all template variables substituted.
     * Throws if any {@code ${...}} placeholders remain after substitution.
     */
    public CloudResourceTags resolve(ApplicationId application, Environment environment, RegionName region,
                                     ClusterSpec.Id clusterId, ClusterSpec.Type clusterType) {
        if (tags.isEmpty()) return this;
        Map<String, String> resolved = new LinkedHashMap<>();
        for (var entry : tags.entrySet()) {
            String value = entry.getValue()
                    .replace("${tenant}", application.tenant().value().toLowerCase(Locale.ROOT))
                    .replace("${application}", application.application().value().toLowerCase(Locale.ROOT))
                    .replace("${instance}", application.instance().value().toLowerCase(Locale.ROOT))
                    .replace("${environment}", environment.value())
                    .replace("${region}", region.value())
                    .replace("${clustername}", clusterId.value().toLowerCase(Locale.ROOT))
                    .replace("${clustertype}", clusterType.name());
            if (value.contains("${"))
                throw new IllegalArgumentException("Unresolved template variable in resource tag value for key '" +
                                                   entry.getKey() + "': " + value);
            resolved.put(entry.getKey(), value);
        }
        return from(resolved);
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
            if (key.indexOf('\0') >= 0 || value.indexOf('\0') >= 0)
                throw new IllegalArgumentException("Tag key or value contains null bytes for key '" + key + "'");
            for (String reserved : RESERVED_TAG_NAMES) {
                if (key.equalsIgnoreCase(reserved))
                    throw new IllegalArgumentException("Tag key '" + key + "' is reserved by the platform");
            }
            for (String prefix : RESERVED_KEY_PREFIXES) {
                if (key.regionMatches(true, 0, prefix, 0, prefix.length()))
                    throw new IllegalArgumentException("Tag key prefix '" + prefix + "' is reserved by the platform: '" + key + "'");
            }
        });
    }

    /** AWS: cross-service safe character set, used for both keys and values. */
    private static class Aws {
        private static final String NAME = "AWS";
        private static final Pattern TAG_PATTERN = Pattern.compile("[a-zA-Z0-9 +\\-=._:/@]+");
        private static final String ALLOWED = "letters, digits, spaces, and + - = . _ : / @";
        private static final int MAX_KEY_LENGTH = 128;
        private static final int MAX_VALUE_LENGTH = 256; // matches structural cap
        private static final int MAX_TAGS = 50;

        static void validate(Map<String, String> tags) {
            checkTagCount(tags.size(), MAX_TAGS, NAME);
            for (var entry : tags.entrySet()) {
                checkLength(entry.getKey(),   MAX_KEY_LENGTH,   "key",   NAME);
                checkLength(entry.getValue(), MAX_VALUE_LENGTH, "value", NAME);
                checkPattern(entry.getKey(), TAG_PATTERN, "key", NAME, ALLOWED);
                checkLiteralParts(entry.getKey(), entry.getValue(), TAG_PATTERN, NAME, ALLOWED);
            }
        }
    }

    /** GCP: lowercase letters, digits, underscores, hyphens. Keys must start with a lowercase letter. */
    private static class Gcp {
        private static final String NAME = "GCP";
        private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");
        private static final Pattern VALUE_PATTERN = Pattern.compile("[a-z0-9_-]*");
        private static final String VALUE_ALLOWED = "lowercase letters, digits, underscores, and hyphens";
        private static final int MAX_KEY_LENGTH = 63;
        private static final int MAX_VALUE_LENGTH = 63;
        private static final int MAX_TAGS = 64; // matches structural cap

        static void validate(Map<String, String> tags) {
            checkTagCount(tags.size(), MAX_TAGS, NAME);
            for (var entry : tags.entrySet()) {
                checkLength(entry.getKey(),   MAX_KEY_LENGTH,   "key",   NAME);
                checkLength(entry.getValue(), MAX_VALUE_LENGTH, "value", NAME);
                if ( ! KEY_PATTERN.matcher(entry.getKey()).matches())
                    throw new IllegalArgumentException("Tag key '" + entry.getKey() + "' is not valid for " + NAME +
                                                       ": must start with a lowercase letter and contain only [a-z0-9_-]");
                checkLiteralParts(entry.getKey(), entry.getValue(), VALUE_PATTERN, NAME, VALUE_ALLOWED);
            }
        }
    }

    /** Azure: keys may not contain {@code < > % & \ ? /}. Values unrestricted. */
    private static class Azure {
        private static final String NAME = "Azure";
        private static final Pattern FORBIDDEN_KEY_CHARS = Pattern.compile("[<>%&\\\\?/]");
        private static final int MAX_KEY_LENGTH = 512; // matches structural cap
        private static final int MAX_VALUE_LENGTH = 256; // matches structural cap
        private static final int MAX_TAGS = 50;

        static void validate(Map<String, String> tags) {
            checkTagCount(tags.size(), MAX_TAGS, NAME);
            for (var entry : tags.entrySet()) {
                checkLength(entry.getKey(),   MAX_KEY_LENGTH,   "key",   NAME);
                checkLength(entry.getValue(), MAX_VALUE_LENGTH, "value", NAME);
                if (FORBIDDEN_KEY_CHARS.matcher(entry.getKey()).find())
                    throw new IllegalArgumentException("Tag key '" + entry.getKey() + "' contains characters " +
                                                       "not allowed in " + NAME + ": < > % & \\ ? /");
            }
        }
    }

    private static void checkTagCount(int count, int max, String cloud) {
        if (count > max)
            throw new IllegalArgumentException("Too many cloud resource tags (" + count +
                                               "): " + cloud + " allows at most " + max);
    }

    private static void checkLength(String value, int max, String field, String cloud) {
        if (value.length() > max)
            throw new IllegalArgumentException("Tag " + field + " exceeds " + cloud + " limit of " +
                                               max + " characters: '" + value + "'");
    }

    private static void checkPattern(String value, Pattern pattern, String field, String cloud,
                                     String allowed) {
        if ( ! pattern.matcher(value).matches())
            throw new IllegalArgumentException("Tag " + field + " '" + value + "' contains characters " +
                                               "not allowed in " + cloud + ". Allowed: " + allowed);
    }

    private static void checkLiteralParts(String key, String value, Pattern pattern, String cloud,
                                          String allowed) {
        String stripped = TEMPLATE_VARIABLE.matcher(value).replaceAll("");
        if ( ! stripped.isEmpty() && ! pattern.matcher(stripped).matches())
            throw new IllegalArgumentException("Tag value for key '" + key + "' contains characters " +
                                               "not allowed in " + cloud + ". Allowed: " + allowed +
                                               ". Value: '" + value + "'");
    }

}

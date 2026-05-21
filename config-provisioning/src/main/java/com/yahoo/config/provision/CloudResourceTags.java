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

    /** Max across all clouds (Azure). Per-cloud limits enforced in {@link #validateForCloud}. */
    private static final int MAX_KEY_LENGTH = 512;
    /** Max across all clouds (AWS, Azure). Per-cloud limits enforced in {@link #validateForCloud}. */
    private static final int MAX_VALUE_LENGTH = 256;
    /** Max across all clouds (GCP). Per-cloud limits enforced in {@link #validateForCloud}. */
    private static final int MAX_TAGS = 64;

    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{[^}]+\\}");

    // AWS: cross-service safe character set
    private static final Pattern AWS_TAG_PATTERN = Pattern.compile("[a-zA-Z0-9 +\\-=._:/@]+");
    private static final int AWS_MAX_KEY_LENGTH = 128;
    private static final int AWS_MAX_VALUE_LENGTH = 256; // matches structural cap
    private static final int AWS_MAX_TAGS = 50;

    // GCP: lowercase letters, digits, underscores, hyphens. Keys must start with lowercase letter.
    private static final Pattern GCP_KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");
    private static final Pattern GCP_VALUE_PATTERN = Pattern.compile("[a-z0-9_-]*");
    private static final int GCP_MAX_KEY_LENGTH = 63;
    private static final int GCP_MAX_VALUE_LENGTH = 63;
    private static final int GCP_MAX_TAGS = 64; // matches structural cap

    // Azure: keys may not contain < > % & \ ? /. Values unrestricted.
    private static final Pattern AZURE_FORBIDDEN_KEY_CHARS = Pattern.compile("[<>%&\\\\?/]");
    private static final int AZURE_MAX_KEY_LENGTH = 512; // matches structural cap
    private static final int AZURE_MAX_VALUE_LENGTH = 256; // matches structural cap
    private static final int AZURE_MAX_TAGS = 50;

    /** System tag names reserved by the platform. */
    private static final List<String> RESERVED_TAG_NAMES = List.of(
            "applicationid", "athenz", "athenz-domain", "athenzservice", "fqdn", "name", "owner", "zone");

    /** Key prefixes reserved by the platform. */
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
    public void validateForCloud(CloudName cloud) {
        if (tags.isEmpty()) return;
        if (cloud.equals(CloudName.AWS)) validateAws();
        else if (cloud.equals(CloudName.GCP)) validateGcp();
        else if (cloud.equals(CloudName.AZURE)) validateAzure();
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
                if (key.equals(reserved))
                    throw new IllegalArgumentException("Tag key '" + key + "' is reserved by the platform");
            }
            for (String prefix : RESERVED_KEY_PREFIXES) {
                if (key.startsWith(prefix))
                    throw new IllegalArgumentException("Tag key prefix '" + prefix + "' is reserved by the platform: '" + key + "'");
            }
        });
    }

    private void validateAws() {
        validateTagCount(tags.size(), AWS_MAX_TAGS, "AWS");
        for (var entry : tags.entrySet()) {
            validateLength(entry.getKey(), AWS_MAX_KEY_LENGTH, "key", "AWS");
            validateLength(entry.getValue(), AWS_MAX_VALUE_LENGTH, "value", "AWS");
            validatePattern(entry.getKey(), AWS_TAG_PATTERN, "key", "AWS",
                           "letters, digits, spaces, and + - = . _ : / @");
            validateLiteralParts(entry.getKey(), entry.getValue(), AWS_TAG_PATTERN, "AWS",
                                "letters, digits, spaces, and + - = . _ : / @");
        }
    }

    private void validateGcp() {
        validateTagCount(tags.size(), GCP_MAX_TAGS, "GCP");
        for (var entry : tags.entrySet()) {
            validateLength(entry.getKey(), GCP_MAX_KEY_LENGTH, "key", "GCP");
            validateLength(entry.getValue(), GCP_MAX_VALUE_LENGTH, "value", "GCP");
            if ( ! GCP_KEY_PATTERN.matcher(entry.getKey()).matches())
                throw new IllegalArgumentException("Tag key '" + entry.getKey() + "' is not valid for GCP: " +
                                                   "must start with a lowercase letter and contain only [a-z0-9_-]");
            validateLiteralParts(entry.getKey(), entry.getValue(), GCP_VALUE_PATTERN, "GCP",
                                "lowercase letters, digits, underscores, and hyphens");
        }
    }

    private void validateAzure() {
        validateTagCount(tags.size(), AZURE_MAX_TAGS, "Azure");
        for (var entry : tags.entrySet()) {
            validateLength(entry.getKey(), AZURE_MAX_KEY_LENGTH, "key", "Azure");
            validateLength(entry.getValue(), AZURE_MAX_VALUE_LENGTH, "value", "Azure");
            if (AZURE_FORBIDDEN_KEY_CHARS.matcher(entry.getKey()).find())
                throw new IllegalArgumentException("Tag key '" + entry.getKey() + "' contains characters " +
                                                   "not allowed in Azure: < > % & \\ ? /");
        }
    }

    private static void validateTagCount(int count, int max, String cloud) {
        if (count > max)
            throw new IllegalArgumentException("Too many cloud resource tags (" + count +
                                               "): " + cloud + " allows at most " + max);
    }

    private static void validateLength(String value, int max, String field, String cloud) {
        if (value.length() > max)
            throw new IllegalArgumentException("Tag " + field + " exceeds " + cloud + " limit of " +
                                               max + " characters: '" + value + "'");
    }

    private static void validatePattern(String value, Pattern pattern, String field, String cloud,
                                        String allowed) {
        if ( ! pattern.matcher(value).matches())
            throw new IllegalArgumentException("Tag " + field + " '" + value + "' contains characters " +
                                               "not allowed in " + cloud + ". Allowed: " + allowed);
    }

    private static void validateLiteralParts(String key, String value, Pattern pattern, String cloud,
                                             String allowed) {
        String stripped = TEMPLATE_VARIABLE.matcher(value).replaceAll("");
        if ( ! stripped.isEmpty() && ! pattern.matcher(stripped).matches())
            throw new IllegalArgumentException("Tag value for key '" + key + "' contains characters " +
                                               "not allowed in " + cloud + ". Allowed: " + allowed +
                                               ". Value: '" + value + "'");
    }

}

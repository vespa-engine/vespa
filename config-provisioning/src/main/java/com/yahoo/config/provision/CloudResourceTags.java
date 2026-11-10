// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Immutable key-value tags to apply to cloud resources in custom enclave deployments.
 * These tags are propagated to the underlying cloud provider (AWS, GCP, Azure) and
 * applied to infrastructure resources provisioned for the enclave.
 *
 * <p>Rules shared with all tags are in {@link CloudTagRules}. This class adds the rules for customer tags:
 * keys reserved by the platform are rejected, the number of tags is limited, and values may contain
 * template variables.</p>
 *
 * @author gjoranv
 */
public class CloudResourceTags {

    private static final CloudResourceTags EMPTY = new CloudResourceTags(Map.of());

    /** Maximum number of customer-provided tags allowed per cloud. */
    private static final int MAX_CUSTOMER_TAGS = 20;

    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{[^}]+\\}");

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
        CloudTagRules rules = CloudTagRules.forCloud(cloud);
        if (tags.size() > MAX_CUSTOMER_TAGS)
            throw new IllegalArgumentException("Too many cloud resource tags (" + tags.size() + "): " +
                                               rules.cloudName() + " allows at most " + MAX_CUSTOMER_TAGS +
                                               " customer tags");
        tags.forEach((key, value) -> rules.validate(key, value, TEMPLATE_VARIABLE.matcher(value).replaceAll("")));
    }

    /**
     * Returns a new instance with all template variables substituted and validated against the target cloud's
     * character set, length, and count limits. Throws if any {@code ${...}} placeholders remain after
     * substitution, or if the resolved tags violate the cloud's rules.
     */
    public CloudResourceTags resolve(ApplicationId application, Environment environment, RegionName region,
                                     ClusterSpec.Id clusterId, ClusterSpec.Type clusterType, CloudName cloud) {
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
        CloudResourceTags result = from(resolved);
        result.validateFor(cloud);
        return result;
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
        CloudTagRules.validateStructure(tags);
        for (String key : tags.keySet()) {
            if (CloudTagRules.isReservedName(key))
                throw new IllegalArgumentException("Tag key '" + key + "' is reserved by the platform");
            Optional<String> reservedPrefix = CloudTagRules.reservedPrefixOf(key);
            if (reservedPrefix.isPresent())
                throw new IllegalArgumentException("Tag key prefix '" + reservedPrefix.get() +
                                                   "' is reserved by the platform: '" + key + "'");
        }
    }

}

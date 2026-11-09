// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Rules for tags on cloud resources that apply to all tags, regardless of who owns them: coarse structural
 * bounds, the character set and length limits of each cloud provider, and the key names and prefixes
 * reserved by the platform. Each kind of tags adds its own rules on top; see {@link CloudResourceTags}
 * for customer tags.
 *
 * <p>Instances hold the rules of one cloud provider, see {@link #forCloud(CloudName)}.</p>
 *
 * @author gjoranv
 */
public abstract sealed class CloudTagRules {

    /** The key prefix of tags owned by the platform. Reserved, see {@link #reservedPrefixOf(String)}. */
    public static final String PLATFORM_KEY_PREFIX = "vai_";

    // Coarse structural ceilings checked in validateStructure(), independent of the per-cloud limits
    // checked in validate(). They bound resource usage when constructing tags from untrusted input
    // (deployment.xml, persisted state) so a pathologically large map or string cannot be held in
    // memory or echoed in an exception message. Set to the loosest value any supported cloud allows,
    // so they never reject input a per-cloud check would accept.
    private static final int MAX_KEY_LENGTH = 512;   // Azure hard limit (max across clouds)
    private static final int MAX_VALUE_LENGTH = 256;  // AWS/Azure hard limit (max across clouds)
    private static final int MAX_TAGS = 64;           // GCP hard limit (max across clouds)

    /**
     * System tag names reserved by the platform. Compared case-insensitively.
     * New Vespa-defined tag names must use the {@link #PLATFORM_KEY_PREFIX}.
     */
    private static final List<String> RESERVED_TAG_NAMES = List.of(
            "applicationid", "athenz", "athenz-domain", "athenzservice", "fqdn", "name", "owner", "zone",
            "tenant", "tenantName", "app", "clusterid",
            "system", "application", "cluster", "generation", "auth-method",
            "preprovisioned");

    /** Key prefixes reserved by the platform. Compared case-insensitively. */
    private static final List<String> RESERVED_KEY_PREFIXES =
            List.of(PLATFORM_KEY_PREFIX, "corp_", "corp:", "bastion_", "bastion:");

    private static final CloudTagRules AWS = new Aws();
    private static final CloudTagRules GCP = new Gcp();
    private static final CloudTagRules AZURE = new Azure();

    private final String cloudName;
    private final int maxKeyLength;
    private final int maxValueLength;

    private CloudTagRules(String cloudName, int maxKeyLength, int maxValueLength) {
        this.cloudName = cloudName;
        this.maxKeyLength = maxKeyLength;
        this.maxValueLength = maxValueLength;
    }

    /** Returns the rules of the given cloud provider. Throws if there are no rules for the cloud. */
    public static CloudTagRules forCloud(CloudName cloud) {
        if (cloud.equals(CloudName.AWS))   return AWS;
        if (cloud.equals(CloudName.GCP))   return GCP;
        if (cloud.equals(CloudName.AZURE)) return AZURE;
        throw new IllegalArgumentException("No resource tag validation rules for cloud '" + cloud + "'");
    }

    /** Returns the name of the cloud provider these rules belong to, as used in validation messages. */
    public String cloudName() { return cloudName; }

    /**
     * Validates the given tag against the character set and length limits of this cloud provider.
     * Callers must first run {@link #validateStructure(Map)} on the tag map, as this method does not
     * repeat its checks, such as rejecting null or empty keys and values.
     */
    public void validate(String key, String value) {
        validate(key, value, value);
    }

    /**
     * Validates the given tag against the character set and length limits of this cloud provider.
     * Length limits apply to the full value, while character rules apply only to {@code valueCharacters},
     * which callers use to exempt parts of the value that are replaced before the tag is applied.
     * Messages show the full value. Callers must first run {@link #validateStructure(Map)} on the tag map,
     * as this method does not repeat its checks, such as rejecting null or empty keys and values.
     */
    public void validate(String key, String value, String valueCharacters) {
        checkLength(key, maxKeyLength, "key");
        checkLength(value, maxValueLength, "value");
        validateKeyCharacters(key);
        validateValueCharacters(key, value, valueCharacters);
    }

    abstract void validateKeyCharacters(String key);

    abstract void validateValueCharacters(String key, String value, String valueCharacters);

    private void checkLength(String value, int max, String field) {
        if (value.length() > max)
            throw new IllegalArgumentException("Tag " + field + " exceeds " + cloudName + " limit of " +
                                               max + " characters: '" + value + "'");
    }

    void checkValueCharacters(String key, String value, String valueCharacters, Pattern pattern, String allowed) {
        if ( ! valueCharacters.isEmpty() && ! pattern.matcher(valueCharacters).matches())
            throw new IllegalArgumentException("Tag value for key '" + key + "' contains characters " +
                                               "not allowed in " + cloudName + ". Allowed: " + allowed +
                                               ". Value: '" + value + "'");
    }

    /**
     * Validates the cloud-independent structure of the given tags: the number of tags, and that keys and
     * values are non-null, non-empty, within coarse length bounds, and free of null bytes.
     * Does not check character sets or reserved keys.
     */
    public static void validateStructure(Map<String, String> tags) {
        if (tags.size() > MAX_TAGS)
            throw new IllegalArgumentException("Too many cloud resource tags (" + tags.size() +
                                               "): maximum is " + MAX_TAGS);
        tags.forEach((key, value) -> {
            Objects.requireNonNull(key, "Tag key cannot be null");
            Objects.requireNonNull(value, "Tag value cannot be null");
            // Length checks first, reporting only lengths, so a pathologically large key or value
            // is rejected before any message below echoes it.
            if (key.length() > MAX_KEY_LENGTH)
                throw new IllegalArgumentException("Tag key exceeds " + MAX_KEY_LENGTH +
                                                   " characters (was " + key.length() + ")");
            if (value.length() > MAX_VALUE_LENGTH)
                throw new IllegalArgumentException("Tag value exceeds " + MAX_VALUE_LENGTH +
                                                   " characters (was " + value.length() + ")");
            if (key.isEmpty())
                throw new IllegalArgumentException("Tag key cannot be empty");
            if (value.isEmpty())
                throw new IllegalArgumentException("Tag value cannot be empty for key '" + key + "'");
            if (key.indexOf('\0') >= 0 || value.indexOf('\0') >= 0)
                throw new IllegalArgumentException("Tag key or value contains null bytes for key '" + key + "'");
        });
    }

    /** Returns whether the given key is a tag name reserved by the platform, ignoring case. */
    public static boolean isReservedName(String key) {
        return RESERVED_TAG_NAMES.stream().anyMatch(key::equalsIgnoreCase);
    }

    /** Returns the reserved key prefix the given key starts with, ignoring case, or empty if there is none. */
    public static Optional<String> reservedPrefixOf(String key) {
        return RESERVED_KEY_PREFIXES.stream()
                                    .filter(prefix -> key.regionMatches(true, 0, prefix, 0, prefix.length()))
                                    .findFirst();
    }

    /** AWS: cross-service safe character set, used for both keys and values. */
    private static final class Aws extends CloudTagRules {
        private static final Pattern TAG_PATTERN = Pattern.compile("[a-zA-Z0-9 +\\-=._:/@]+");
        private static final String ALLOWED = "letters, digits, spaces, and + - = . _ : / @";

        Aws() { super("AWS", 128, 256); }

        @Override
        void validateKeyCharacters(String key) {
            if ( ! TAG_PATTERN.matcher(key).matches())
                throw new IllegalArgumentException("Tag key '" + key + "' contains characters " +
                                                   "not allowed in " + cloudName() + ". Allowed: " + ALLOWED);
        }

        @Override
        void validateValueCharacters(String key, String value, String valueCharacters) {
            checkValueCharacters(key, value, valueCharacters, TAG_PATTERN, ALLOWED);
        }
    }

    /** GCP: lowercase letters, digits, underscores, hyphens. Keys must start with a lowercase letter. */
    private static final class Gcp extends CloudTagRules {
        private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");
        private static final Pattern VALUE_PATTERN = Pattern.compile("[a-z0-9_-]*");
        private static final String VALUE_ALLOWED = "lowercase letters, digits, underscores, and hyphens";

        Gcp() { super("GCP", 63, 63); }

        @Override
        void validateKeyCharacters(String key) {
            if ( ! KEY_PATTERN.matcher(key).matches())
                throw new IllegalArgumentException("Tag key '" + key + "' is not valid for " + cloudName() +
                                                   ": must start with a lowercase letter and contain only [a-z0-9_-]");
        }

        @Override
        void validateValueCharacters(String key, String value, String valueCharacters) {
            checkValueCharacters(key, value, valueCharacters, VALUE_PATTERN, VALUE_ALLOWED);
        }
    }

    /** Azure: keys may not contain {@code < > % & \ ? /}. Values unrestricted. */
    private static final class Azure extends CloudTagRules {
        private static final Pattern FORBIDDEN_KEY_CHARS = Pattern.compile("[<>%&\\\\?/]");

        Azure() { super("Azure", 512, 256); }

        @Override
        void validateKeyCharacters(String key) {
            if (FORBIDDEN_KEY_CHARS.matcher(key).find())
                throw new IllegalArgumentException("Tag key '" + key + "' contains characters " +
                                                   "not allowed in " + cloudName() + ": < > % & \\ ? /");
        }

        @Override
        void validateValueCharacters(String key, String value, String valueCharacters) { }
    }

}

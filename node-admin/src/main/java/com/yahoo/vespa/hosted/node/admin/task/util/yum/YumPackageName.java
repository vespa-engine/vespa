// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YUM package name.
 *
 * @author hakonhall
 */
public class YumPackageName {
    private static final Pattern ARCHITECTURE_PATTERN = Pattern.compile("\\.(noarch|x86_64|i686|i386|\\*)$");
    private static final Pattern NAME_VER_REL_PATTERN = Pattern.compile("^(.+)-([^-]*[0-9][^-]*)-([^-]*[0-9][^-]*)$");

    public final Optional<String> epoch;
    public final String name;
    public final Optional<String> version;
    public final Optional<String> release;
    public final Optional<String> architecture;

    /** @see Builder */
    private YumPackageName(Optional<String> epoch,
                           String name,
                           Optional<String> version,
                           Optional<String> release,
                           Optional<String> architecture) {
        this.epoch = epoch;
        this.name = name;
        this.version = version;
        this.release = release;
        this.architecture = architecture;
    }

    /**
     * Parse the string specification of a YUM package.
     *
     * <p>According to yum(8) a package can be specified using a variety of different
     * and ambiguous formats. We'll use a subset:
     *
     * <ul>
     *     <li>spec MUST be of the form name-ver-rel, name-ver-rel.arch, or epoch:name-ver-rel.arch.
     *     <li>If specified, arch should be one of "noarch", "i686", "x86_64", or "*". The wildcard
     *     is equivalent to not specifying arch.
     *     <li>rel cannot end in something that would be mistaken for the '.arch' suffix.
     *     <li>ver and rel are assumed to not contain any '-' to uniquely identify name,
     *     and must contain a digit.
     * </ul>
     *
     * @param spec A package name of the form epoch:name-ver-rel.arch, name-ver-rel.arch, or name-ver-rel.
     * @return The package with that name.
     * @throws IllegalArgumentException if spec does not specify a package name.
     */
    public static YumPackageName fromString(String spec) {
        return parseString(spec).orElseThrow(() -> new IllegalArgumentException("Failed to decode the YUM package spec '" + spec + "'"));
    }

    /** See {@link #fromString(String)}. */
    public static Optional<YumPackageName> parseString(String spec) {
        Optional<String> epoch = Optional.empty();
        int epochColon = spec.indexOf(':');
        if (epochColon >= 0) {
            epoch = Optional.of(spec.substring(0, epochColon));
            if (!epoch.get().chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Epoch is not a number: " + epoch.get());
            }

            spec = spec.substring(epochColon + 1);
        }

        Optional<String> architecture = Optional.empty();
        Matcher architectureMatcher = ARCHITECTURE_PATTERN.matcher(spec);
        if (architectureMatcher.find()) {
            architecture = Optional.of(architectureMatcher.group(1));
            spec = spec.substring(0, architectureMatcher.start());
        }


        Matcher matcher = NAME_VER_REL_PATTERN.matcher(spec);
        if (matcher.find()) {
            return Optional.of(new YumPackageName(
                    epoch,
                    matcher.group(1),
                    Optional.of(matcher.group(2)),
                    Optional.of(matcher.group(3)),
                    architecture));
        }

        return Optional.empty();
    }

    public Optional<String> getEpoch() { return epoch; }
    public String getName() { return name; }
    public Optional<String> getVersion() { return version; }
    public Optional<String> getRelease() { return release; }
    public Optional<String> getArchitecture() { return architecture; }

    /**
     * Return the full name of the package in the format epoch:name-ver-rel.arch, which can
     * be used with e.g. the YUM install and versionlock commands.
     *
     * <p>The package MUST have both version and release. Absent epoch defaults to "0".
     * Absent arch defaults to "*".
     */
    public String toFullName() {
        return String.format("%s:%s-%s-%s.%s",
                epoch.orElse("0"),
                name,
                version.orElseThrow(() -> new IllegalStateException("Version is missing for YUM package " + name)),
                release.orElseThrow(() -> new IllegalStateException("Release is missing for YUM package " + name)),
                architecture.orElse("*"));
    }

    /** The package name output by 'yum versionlock list'. Can also be used with 'add' and 'delete'. */
    public String toVersionLock() {
        return String.format("%s:%s-%s-%s.%s",
                epoch.orElse("0"),
                name,
                version.orElseThrow(() -> new IllegalStateException("Version is missing for YUM package " + name)),
                release.orElseThrow(() -> new IllegalStateException("Release is missing for YUM package " + name)),
                "*");
    }

    public static class Builder {
        private Optional<String> epoch;
        private String name;
        private Optional<String> version;
        private Optional<String> release;
        private Optional<String> architecture;

        public Builder(YumPackageName aPackage) {
            epoch = aPackage.epoch;
            name = aPackage.name;
            version = aPackage.version;
            release = aPackage.release;
            architecture = aPackage.architecture;
        }

        public Builder setEpoch(String epoch) { this.epoch = Optional.of(epoch); return this; }
        public Builder setName(String name) { this.name = name; return this; }
        public Builder setRelease(String version) { this.version = Optional.of(version); return this; }
        public Builder setVersion(String release) { this.release = Optional.of(release); return this; }
        public Builder setArchitecture(String architecture) { this.architecture = Optional.of(architecture); return this; }

        public YumPackageName build() { return new YumPackageName(epoch, name, version, release, architecture); }
    }
}

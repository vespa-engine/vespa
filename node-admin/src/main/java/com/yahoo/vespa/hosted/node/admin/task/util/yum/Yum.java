// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandLine;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author hakonhall
 */
public class Yum {
    // Note: "(?dm)" makes newline be \n (only), and enables multiline mode where ^$ match lines with find()
    private static final Pattern INSTALL_NOOP_PATTERN = Pattern.compile("(?dm)^Nothing to do$");
    private static final Pattern UPGRADE_NOOP_PATTERN = Pattern.compile("(?dm)^No packages marked for update$");
    private static final Pattern REMOVE_NOOP_PATTERN = Pattern.compile("(?dm)^No Packages marked for removal$");

    private static final Pattern UNKNOWN_PACKAGE_PATTERN = Pattern.compile(
            "(?dm)^No package ([^ ]+) available\\.$");

    private final Terminal terminal;

    public Yum(Terminal terminal) {
        this.terminal = terminal;
    }

    /**
     * @param packages A list of packages, each package being of the form name-1.2.3-1.el7.noarch
     */
    public GenericYumCommand install(String... packages) {
        return newYumCommand("install", packages, INSTALL_NOOP_PATTERN);
    }

    /**
     * @param packages A list of packages, each package being of the form name-1.2.3-1.el7.noarch,
     *                 if no packages are given, will upgrade all installed packages
     */
    public GenericYumCommand upgrade(String... packages) {
        return newYumCommand("upgrade", packages, UPGRADE_NOOP_PATTERN);
    }

    public GenericYumCommand remove(String... packages) {
        return newYumCommand("remove", packages, REMOVE_NOOP_PATTERN);
    }

    private GenericYumCommand newYumCommand(String yumCommand,
                                            String[] packages,
                                            Pattern noopPattern) {
        return new GenericYumCommand(
                terminal,
                yumCommand,
                Arrays.asList(packages),
                noopPattern);
    }

    public static class GenericYumCommand {
        private final Terminal terminal;
        private final String yumCommand;
        private final List<String> packages;
        private final Pattern commandOutputNoopPattern;

        private Optional<String> enabledRepo = Optional.empty();
        private boolean lockVersion = false;

        private GenericYumCommand(Terminal terminal,
                                  String yumCommand,
                                  List<String> packages,
                                  Pattern commandOutputNoopPattern) {
            this.terminal = terminal;
            this.yumCommand = yumCommand;
            this.packages = packages;
            this.commandOutputNoopPattern = commandOutputNoopPattern;

            if (packages.isEmpty() && ! "upgrade".equals(yumCommand)) {
                throw new IllegalArgumentException("No packages specified");
            }
        }

        @SuppressWarnings("unchecked")
        public GenericYumCommand enableRepo(String repo) {
            enabledRepo = Optional.of(repo);
            return this;
        }

        /**
         * Ensure the version of the installs are locked.
         *
         * <p>WARNING: In order to simplify the user interface of {@link #lockVersion()},
         * the package name specified in the command, e.g. {@link #install(String...)}, MUST be of
         * a simple format, see {@link PackageName#fromString(String)}.
         */
        public GenericYumCommand lockVersion() {
            packages.forEach(PackageName::fromString); // to throw any parse error here instead of later
            lockVersion = true;
            return this;
        }

        public boolean converge(TaskContext context) {
            Set<String> packageNamesToLock = new HashSet<>();
            Set<String> fullPackageNamesToLock = new HashSet<>();

            if (lockVersion) {
                // Remove all locks for other version

                packages.stream()
                        .map(PackageName::fromString)
                        .map(PackageName.Builder::new)
                        .map(builder -> builder.setArchitecture("*").build())
                        .forEach(packageName -> {
                            packageNamesToLock.add(packageName.getName());
                            fullPackageNamesToLock.add(packageName.toFullName());
                        });

                terminal.newCommandLine(context)
                        .add("yum", "--quiet", "versionlock", "list")
                        .executeSilently()
                        .getOutputLinesStream()
                        .map(PackageName::parseString)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .forEach(packageName -> {
                            // Ignore lines for other packages
                            if (packageNamesToLock.contains(packageName.getName())) {
                                // If existing lock doesn't exactly match the full package name,
                                // it means it's locked to another version and we must remove that lock.
                                String fullName = packageName.toFullName();
                                if (!fullPackageNamesToLock.remove(fullName)) {
                                    terminal.newCommandLine(context)
                                            .add("yum", "versionlock", "delete", fullName)
                                            .execute();
                                }
                            }
                        });
            }

            CommandLine commandLine = terminal.newCommandLine(context);
            commandLine.add("yum", yumCommand, "--assumeyes");
            enabledRepo.ifPresent(repo -> commandLine.add("--enablerepo=" + repo));
            commandLine.add(packages);

            // There's no way to figure out whether a yum command would have been a no-op.
            // Therefore, run the command and parse the output to decide.
            boolean modifiedSystem = commandLine
                    .executeSilently()
                    .mapOutput(this::mapOutput);

            if (modifiedSystem) {
                commandLine.recordSilentExecutionAsSystemModification();
            }

            fullPackageNamesToLock.forEach(fullPackageName ->
                    terminal.newCommandLine(context)
                            .add("yum", "versionlock", "add", fullPackageName)
                            .execute());
            modifiedSystem |= !fullPackageNamesToLock.isEmpty();

            return modifiedSystem;
        }

        private boolean mapOutput(String output) {
            Matcher unknownPackageMatcher = UNKNOWN_PACKAGE_PATTERN.matcher(output);
            if (unknownPackageMatcher.find()) {
                throw new IllegalArgumentException("Unknown package: " + unknownPackageMatcher.group(1));
            }

            return !commandOutputNoopPattern.matcher(output).find();
        }
    }

    /** YUM package name. */
    static class PackageName {
        private static final Pattern ARCHITECTURE_PATTERN = Pattern.compile("\\.(noarch|x86_64|i686|i386|\\*)$");
        private static final Pattern NAME_VER_REL_PATTERN = Pattern.compile("^(.+)-([^-]*[0-9][^-]*)-([^-]*[0-9][^-]*)$");

        public final Optional<String> epoch;
        public final String name;
        public final Optional<String> version;
        public final Optional<String> release;
        public final Optional<String> architecture;

        private PackageName(Optional<String> epoch,
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
         *     <li>If specified, arch MUST be one of "noarch", "i686", "x86_64", or "*". The wildcard
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
        public static PackageName fromString(String spec) {
            return parseString(spec).orElseThrow(() -> new IllegalArgumentException("Failed to decode the YUM package spec '" + spec + "'"));
        }

        /** See {@link #fromString(String)}. */
        public static Optional<PackageName> parseString(String spec) {
            Optional<String> epoch = Optional.empty();
            int epochColon = spec.indexOf(':');
            if (epochColon >= 0) {
                epoch = Optional.of(spec.substring(0, epochColon));
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
                return Optional.of(new PackageName(
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

        public static class Builder {
            private Optional<String> epoch;
            private String name;
            private Optional<String> version;
            private Optional<String> release;
            private Optional<String> architecture;

            public Builder(PackageName aPackage) {
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

            public PackageName build() { return new PackageName(epoch, name, version, release, architecture); }
        }
    }
}

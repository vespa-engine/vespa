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
         * a simple format, see {@link YumPackageName#fromString(String)}.
         */
        public GenericYumCommand lockVersion() {
            packages.forEach(YumPackageName::fromString); // to throw any parse error here instead of later
            lockVersion = true;
            return this;
        }

        public boolean converge(TaskContext context) {
            Set<String> packageNamesToLock = new HashSet<>();
            Set<String> fullPackageNamesToLock = new HashSet<>();

            if (lockVersion) {
                // Remove all locks for other version

                packages.stream()
                        .map(YumPackageName::fromString)
                        .forEach(packageName -> {
                            packageNamesToLock.add(packageName.getName());
                            fullPackageNamesToLock.add(packageName.toVersionLock());
                        });

                terminal.newCommandLine(context)
                        .add("yum", "--quiet", "versionlock", "list")
                        .executeSilently()
                        .getOutputLinesStream()
                        .map(YumPackageName::parseString)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .forEach(packageName -> {
                            // Ignore lines for other packages
                            if (packageNamesToLock.contains(packageName.getName())) {
                                // If existing lock doesn't exactly match the full package name,
                                // it means it's locked to another version and we must remove that lock.
                                String versionLockName = packageName.toVersionLock();
                                if (!fullPackageNamesToLock.remove(versionLockName)) {
                                    terminal.newCommandLine(context)
                                            .add("yum", "versionlock", "delete", versionLockName)
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

}

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
import java.util.stream.Collectors;

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

    public GenericYumCommand install(YumPackageName... packages) {
        return newYumCommand("install", packages, INSTALL_NOOP_PATTERN);
    }

    public GenericYumCommand install(String package1, String... packages) {
        return install(toYumPackageNameArray(package1, packages));
    }

    public GenericYumCommand upgrade(YumPackageName... packages) {
        return newYumCommand("upgrade", packages, UPGRADE_NOOP_PATTERN);
    }

    public GenericYumCommand upgrade(String package1, String... packages) {
        return upgrade(toYumPackageNameArray(package1, packages));
    }

    public GenericYumCommand remove(YumPackageName... packages) {
        return newYumCommand("remove", packages, REMOVE_NOOP_PATTERN);
    }

    public GenericYumCommand remove(String package1, String... packages) {
        return remove(toYumPackageNameArray(package1, packages));
    }

    static YumPackageName[] toYumPackageNameArray(String package1, String... packages) {
        YumPackageName[] array = new YumPackageName[1 + packages.length];
        array[0] = YumPackageName.fromString(package1);
        for (int i = 0; i < packages.length; ++i) {
            array[1 + i] = YumPackageName.fromString(packages[i]);
        }
        return array;
    }

    private GenericYumCommand newYumCommand(String yumCommand,
                                            YumPackageName[] packages,
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
        private final List<YumPackageName> packages;
        private final Pattern commandOutputNoopPattern;

        private Optional<String> enabledRepo = Optional.empty();
        private boolean lockVersion = false;

        private GenericYumCommand(Terminal terminal,
                                  String yumCommand,
                                  List<YumPackageName> packages,
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
         * the package name specified in the command, e.g. {@link #install(String, String...)}, MUST be of
         * a simple format, see {@link YumPackageName#fromString(String)}.
         */
        public GenericYumCommand lockVersion() {
            // Verify each package has sufficient info to form a proper version lock name.
            packages.forEach(YumPackageName::toVersionLockName);
            lockVersion = true;
            return this;
        }

        public boolean converge(TaskContext context) {
            Set<String> packageNamesToLock = new HashSet<>();
            Set<String> fullPackageNamesToLock = new HashSet<>();

            if (lockVersion) {
                // Remove all locks for other version

                packages.forEach(packageName -> {
                            packageNamesToLock.add(packageName.getName());
                            fullPackageNamesToLock.add(packageName.toVersionLockName());
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
                                String versionLockName = packageName.toVersionLockName();
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
            commandLine.add(packages.stream().map(YumPackageName::toName).collect(Collectors.toList()));

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

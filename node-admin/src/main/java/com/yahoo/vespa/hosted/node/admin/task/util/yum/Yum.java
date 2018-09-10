// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandLine;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author hakonhall
 */
public class Yum {
    // Note: "(?dm)" makes newline be \n (only), and enables multiline mode where ^$ match lines with find()
    private static final Pattern CHECKING_FOR_UPDATE_PATTERN =
            Pattern.compile("(?dm)^Package matching [^ ]+ already installed\\. Checking for update\\.$");
    private static final Pattern NOTHING_TO_DO_PATTERN = Pattern.compile("(?dm)^Nothing to do$");
    private static final Pattern INSTALL_NOOP_PATTERN = NOTHING_TO_DO_PATTERN;
    private static final Pattern UPGRADE_NOOP_PATTERN = Pattern.compile("(?dm)^No packages marked for update$");
    private static final Pattern REMOVE_NOOP_PATTERN = Pattern.compile("(?dm)^No Packages marked for removal$");


    private static final Pattern UNKNOWN_PACKAGE_PATTERN = Pattern.compile(
            "(?dm)^No package ([^ ]+) available\\.$");

    private final Terminal terminal;

    public Yum(Terminal terminal) {
        this.terminal = terminal;
    }

    /**
     * Lock and install, or if necessary downgrade, a package to a given version.
     *
     * @return false only if the package was already locked and installed at the given version (no-op)
     */
    public boolean installFixedVersion(TaskContext context, YumPackageName yumPackage) {
        String targetVersionLockName = yumPackage.toVersionLockName();

        boolean alreadyLocked = terminal
                .newCommandLine(context)
                .add("yum", "--quiet", "versionlock", "list")
                .executeSilently()
                .getOutputLinesStream()
                .map(YumPackageName::parseString)
                .filter(Optional::isPresent) // removes garbage first lines, even with --quiet
                .map(Optional::get)
                .anyMatch(packageName -> {
                    // Ignore lines for other packages
                    if (packageName.getName().equals(yumPackage.getName())) {
                        // If existing lock doesn't exactly match the full package name,
                        // it means it's locked to another version and we must remove that lock.
                        String versionLockName = packageName.toVersionLockName();
                        if (versionLockName.equals(targetVersionLockName)) {
                            return true;
                        } else {
                            terminal.newCommandLine(context)
                                    .add("yum", "versionlock", "delete", versionLockName)
                                    .execute();
                        }
                    }

                    return false;
                });

        boolean modified = false;

        if (!alreadyLocked) {
            terminal.newCommandLine(context)
                    .add("yum", "versionlock", "add", targetVersionLockName)
                    .execute();
            modified = true;
        }

        // The following 3 things may happen with yum install:
        //  1. The package is installed or upgraded to the target version, in case we'd return
        //     true from converge()
        //  2. The package is already installed at target version, in case
        //     "Nothing to do" is printed in the last line and we may return false from converge()
        //  3. The package is already installed but at a later version than the target version,
        //     in case the last 2 lines of the output is:
        //       - "Package matching yakl-client-0.10-654.el7.x86_64 already installed. Checking for update."
        //       - "Nothing to do"
        //     And in case we need to downgrade and return true from converge()

        CommandLine commandLine = terminal
                .newCommandLine(context)
                .add("yum", "install", "--assumeyes", yumPackage.toName());

        String output = commandLine.executeSilently().getUntrimmedOutput();

        if (NOTHING_TO_DO_PATTERN.matcher(output).find()) {
            if (CHECKING_FOR_UPDATE_PATTERN.matcher(output).find()) {
                // case 3.
                terminal.newCommandLine(context)
                        .add("yum", "downgrade", "--assumeyes", yumPackage.toName())
                        .execute();
                modified = true;
            } else {
                // case 2.
            }
        } else {
            // case 1.
            commandLine.recordSilentExecutionAsSystemModification();
            modified = true;
        }

        return modified;
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

        private final List<String> enabledRepo = new ArrayList<>();

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
        public GenericYumCommand enableRepos(String... repos) {
            enabledRepo.addAll(Arrays.asList(repos));
            return this;
        }

        public boolean converge(TaskContext context) {
            CommandLine commandLine = terminal.newCommandLine(context);
            commandLine.add("yum", yumCommand, "--assumeyes");
            enabledRepo.forEach(repo -> commandLine.add("--enablerepo=" + repo));
            commandLine.add(packages.stream().map(YumPackageName::toName).collect(Collectors.toList()));

            // There's no way to figure out whether a yum command would have been a no-op.
            // Therefore, run the command and parse the output to decide.
            boolean modifiedSystem = commandLine
                    .executeSilently()
                    .mapOutput(this::mapOutput);

            if (modifiedSystem) {
                commandLine.recordSilentExecutionAsSystemModification();
            }

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

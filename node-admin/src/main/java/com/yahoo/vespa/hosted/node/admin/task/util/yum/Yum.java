// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandLine;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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

    private final TaskContext taskContext;
    private final Terminal terminal;

    public Yum(TaskContext taskContext, Terminal terminal) {
        this.taskContext = taskContext;
        this.terminal = terminal;
    }

    /**
     * @param packages A list of packages, each package being of the form name-1.2.3-1.el7.noarch
     */
    public GenericYumCommand install(String... packages) {
        return newYumCommand("install", packages, INSTALL_NOOP_PATTERN);
    }

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
                taskContext,
                terminal,
                yumCommand,
                Arrays.asList(packages),
                noopPattern);
    }

    public static class GenericYumCommand {
        private final TaskContext taskContext;
        private final Terminal terminal;
        private final String yumCommand;
        private final List<String> packages;
        private final Pattern commandOutputNoopPattern;
        private Optional<String> enabledRepo = Optional.empty();

        private GenericYumCommand(TaskContext taskContext,
                                  Terminal terminal,
                                  String yumCommand,
                                  List<String> packages,
                                  Pattern commandOutputNoopPattern) {
            this.taskContext = taskContext;
            this.terminal = terminal;
            this.yumCommand = yumCommand;
            this.packages = packages;
            this.commandOutputNoopPattern = commandOutputNoopPattern;

            if (packages.isEmpty()) {
                throw new IllegalArgumentException("No packages specified");
            }
        }

        @SuppressWarnings("unchecked")
        public GenericYumCommand enableRepo(String repo) {
            enabledRepo = Optional.of(repo);
            return this;
        }

        public boolean converge() {
            CommandLine commandLine = terminal.newCommandLine(taskContext);
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

            return modifiedSystem;
        }

        public boolean mapOutput(String output) {
            Matcher unknownPackageMatcher = UNKNOWN_PACKAGE_PATTERN.matcher(output);
            if (unknownPackageMatcher.find()) {
                throw new IllegalArgumentException("Unknown package: " + unknownPackageMatcher.group(1));
            }

            return !commandOutputNoopPattern.matcher(output).find();
        }
    }
}

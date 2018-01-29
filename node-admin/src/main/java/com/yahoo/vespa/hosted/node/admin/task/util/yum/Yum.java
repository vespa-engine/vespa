// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.ChildProcess;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Command;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author hakonhall
 */
public class Yum {
    private static final Pattern INSTALL_NOOP_PATTERN =
            Pattern.compile("\nNothing to do\n$");
    private static final Pattern UPGRADE_NOOP_PATTERN =
            Pattern.compile("\nNo packages marked for update\n$");
    private static final Pattern REMOVE_NOOP_PATTERN =
            Pattern.compile("\nNo Packages marked for removal\n$");

    private final TaskContext taskContext;
    private final Supplier<Command> commandSupplier;

    public Yum(TaskContext taskContext) {
        this.taskContext = taskContext;
        this.commandSupplier = () -> new Command(taskContext);
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
                commandSupplier,
                yumCommand,
                Arrays.asList(packages),
                noopPattern);
    }

    public static class GenericYumCommand {
        private static Logger logger = Logger.getLogger(Yum.class.getName());

        private final TaskContext taskContext;
        private final Supplier<Command> commandSupplier;
        private final String yumCommand;
        private final List<String> packages;
        private final Pattern commandOutputNoopPattern;
        private Optional<String> enabledRepo = Optional.empty();

        private GenericYumCommand(TaskContext taskContext,
                                  Supplier<Command> commandSupplier,
                                  String yumCommand,
                                  List<String> packages,
                                  Pattern commandOutputNoopPattern) {
            this.taskContext = taskContext;
            this.commandSupplier = commandSupplier;
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
            Command command = commandSupplier.get();
            command.add("yum", yumCommand, "--assumeyes");
            enabledRepo.ifPresent(repo -> command.add("--enablerepo=" + repo));
            command.add(packages);
            ChildProcess childProcess = command
                    .spawnProgramWithoutSideEffects()
                    .waitForTermination()
                    .throwIfFailed();

            // There's no way to figure out whether a yum command would have been a no-op.
            // Therefore, run the command and parse the output to decide.
            String output = childProcess.getUtf8Output();
            if (commandOutputNoopPattern.matcher(output).matches()) {
                return false;
            } else {
                childProcess.logAsModifyingSystemAfterAll(logger);
                return true;
            }
        }
    }

    // For testing
    Yum(TaskContext taskContext, Supplier<Command> commandSupplier) {
        this.taskContext = taskContext;
        this.commandSupplier = commandSupplier;
    }
}

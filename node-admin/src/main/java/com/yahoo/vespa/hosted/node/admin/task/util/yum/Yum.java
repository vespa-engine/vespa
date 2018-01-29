// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.ChildProcess;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * @author hakonhall
 */
public class Yum {
    private static Logger logger = Logger.getLogger(Yum.class.getName());

    private final TaskContext taskContext;
    private final Supplier<Command> commandSupplier;
    private List<String> packages = new ArrayList<>();

    public Yum(TaskContext taskContext) {
        this.taskContext = taskContext;
        this.commandSupplier = () -> new Command(taskContext);
    }

    /**
     * @param packages A list of packages, each package being of the form name-1.2.3-1.el7.noarch
     */
    public Install install(String... packages) {
        return new Install(taskContext, Arrays.asList(packages));
    }

    public class Install {
        private final TaskContext taskContext;
        private final List<String> packages;
        private Optional<String> enabledRepo = Optional.empty();

        public Install(TaskContext taskContext, List<String> packages) {
            this.taskContext = taskContext;
            this.packages = packages;

            if (packages.isEmpty()) {
                throw new IllegalArgumentException("No packages specified");
            }
        }

        public Install enableRepo(String repo) {
            enabledRepo = Optional.of(repo);
            return this;
        }

        public boolean converge() {
            if (packages.stream().allMatch(Yum.this::isInstalled)) {
                return false;
            }

            execute();
            return true;
        }

        private void execute() {
            Command command = commandSupplier.get();
            command.add("yum", "install", "--assumeyes");
            enabledRepo.ifPresent(repo -> command.add("--enablerepo=" + repo));
            command.add(packages);
            command.spawn(logger).waitForTermination().throwIfFailed();
        }
    }

    Yum(TaskContext taskContext, Supplier<Command> commandSupplier) {
        this.taskContext = taskContext;
        this.commandSupplier = commandSupplier;
    }

    private boolean isInstalled(String package_) {
        ChildProcess childProcess = commandSupplier.get()
                .add("yum", "list", "installed", package_)
                .spawnWithoutLoggingCommand();
        childProcess.waitForTermination();
        return childProcess.exitValue() == 0;
    }
}

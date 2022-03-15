// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandLine;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author freva
 */
public abstract class YumCommand<T extends YumCommand<T>> {

    // Note: "(?dm)" makes newline be \n (only), and enables multiline mode where ^$ match lines with find()
    public static final Pattern INSTALL_NOOP_PATTERN = Pattern.compile("(?dm)^Nothing to do\\.?$");
    public static final Pattern UPGRADE_NOOP_PATTERN = Pattern.compile("(?dm)^No packages marked for update$");
    public static final Pattern REMOVE_NOOP_PATTERN = Pattern.compile("(?dm)^No [pP]ackages marked for removal\\.?$");

    // WARNING: These must be in the same order as the supplier below
    private static final String RPM_QUERYFORMAT = Stream.of("NAME", "EPOCH", "VERSION", "RELEASE", "ARCH")
            .map(formatter -> "%{" + formatter + "}")
            .collect(Collectors.joining("\\n"));
    private static final Function<YumPackageName.Builder, List<Function<String, YumPackageName.Builder>>>
            PACKAGE_NAME_BUILDERS_GENERATOR = builder -> List.of(
            builder::setName, builder::setEpoch, builder::setVersion, builder::setRelease, builder::setArchitecture);

    private List<String> enabledRepos = List.of();
    private final Terminal terminal;

    protected YumCommand(Terminal terminal) {
        this.terminal = terminal;
    }

    /** Enables the given repos for this command */
    public T enableRepo(String... repo) {
        enabledRepos = List.of(repo);
        return getThis();
    }

    protected abstract T getThis(); // Hack to get around unchecked cast warning

    protected void addParametersToCommandLine(CommandLine commandLine) {
        commandLine.add("--assumeyes");
        enabledRepos.forEach(repo -> commandLine.add("--enablerepo=" + repo));
    }

    public abstract boolean converge(TaskContext context);

    public static class GenericYumCommand extends YumCommand<GenericYumCommand> {
        private static final Pattern UNKNOWN_PACKAGE_PATTERN = Pattern.compile("(?dm)^No package ([^ ]+) available\\.$");

        private final Terminal terminal;
        private final CommandType yumCommand;
        private final List<YumPackageName> packages;
        private final List<String> options = new ArrayList<>();

        GenericYumCommand(Terminal terminal, CommandType yumCommand, List<YumPackageName> packages) {
            super(terminal);
            this.terminal = terminal;
            this.yumCommand = yumCommand;
            this.packages = packages;

            switch (yumCommand) {
                case install: {
                    if (packages.size() > 1) options.add("skip_missing_names_on_install=False");
                    break;
                }
                case upgrade: {
                    if (packages.size() > 1) options.add("skip_missing_names_on_update=False");
                    break;
                }
                case remove: break;
                default: throw new IllegalArgumentException("Unknown yum command: " + yumCommand);
            }

            if (packages.isEmpty() && yumCommand != CommandType.upgrade)
                throw new IllegalArgumentException("No packages specified");
        }

        @Override
        protected void addParametersToCommandLine(CommandLine commandLine) {
            super.addParametersToCommandLine(commandLine);
            options.forEach(option -> commandLine.add("--setopt", option));
        }

        @Override
        public boolean converge(TaskContext context) {
            if (yumCommand == CommandType.install)
                if (packages.stream().allMatch(pkg -> isInstalled(context, pkg))) return false;
            if (yumCommand == CommandType.remove)
                if (packages.stream().noneMatch(pkg -> isInstalled(context, pkg))) return false;

            CommandLine commandLine = terminal.newCommandLine(context);
            commandLine.add("yum", yumCommand.name());
            addParametersToCommandLine(commandLine);
            commandLine.add(packages.stream().map(pkg -> pkg.toName()).collect(Collectors.toList()));

            // There's no way to figure out whether a yum command would have been a no-op.
            // Therefore, run the command and parse the output to decide.
            boolean modifiedSystem = commandLine
                    .executeSilently()
                    .mapOutput(this::packageChanged);

            if (modifiedSystem) {
                commandLine.recordSilentExecutionAsSystemModification();
            }

            return modifiedSystem;
        }

        private boolean packageChanged(String output) {
            Matcher unknownPackageMatcher = UNKNOWN_PACKAGE_PATTERN.matcher(output);
            if (unknownPackageMatcher.find()) {
                throw new IllegalArgumentException("Unknown package: " + unknownPackageMatcher.group(1));
            }

            return yumCommand.outputNoopPatterns.stream().noneMatch(pattern -> pattern.matcher(output).find());
        }

        protected GenericYumCommand getThis() { return this; }

        enum CommandType {
            install(INSTALL_NOOP_PATTERN), remove(REMOVE_NOOP_PATTERN), upgrade(INSTALL_NOOP_PATTERN, UPGRADE_NOOP_PATTERN);

            private final List<Pattern> outputNoopPatterns;
            CommandType(Pattern... outputNoopPatterns) {
                this.outputNoopPatterns = List.of(outputNoopPatterns);
            }
        }
    }


    public static class InstallFixedYumCommand extends YumCommand<InstallFixedYumCommand> {
        // Note: "(?dm)" makes newline be \n (only), and enables multiline mode where ^$ match lines with find()
        private static final Pattern CHECKING_FOR_UPDATE_PATTERN =
                Pattern.compile("(?dm)^Package matching [^ ]+ already installed\\. Checking for update\\.$");

        private final Terminal terminal;
        private final YumPackageName yumPackage;

        InstallFixedYumCommand(Terminal terminal, YumPackageName yumPackage) {
            super(terminal);
            this.terminal = terminal;
            this.yumPackage = yumPackage;
        }

        @Override
        public boolean converge(TaskContext context) {
            String targetVersionLockName = yumPackage.toVersionLockName();

            boolean alreadyLocked = false;
            Optional<String> versionLock = versionLockExists(context, terminal, yumPackage);
            if (versionLock.isPresent()) {
                if (versionLock.get().equals(targetVersionLockName)) {
                    alreadyLocked = true;
                } else {
                    YumCommand.deleteVersionLock(context, terminal, versionLock.get());
                }
            }

            boolean modified = false;

            if (!alreadyLocked) {
                CommandLine commandLine = terminal.newCommandLine(context).add("yum", "versionlock", "add");
                // If the targetVersionLockName refers to a package in a by-default-disabled repo,
                // we must enable the repo unless targetVersionLockName is already installed.
                // The other versionlock commands (list, delete) does not require --enablerepo.
                addParametersToCommandLine(commandLine);
                commandLine.add(targetVersionLockName).execute();
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

            var installCommand = terminal.newCommandLine(context).add("yum", "install");
            addParametersToCommandLine(installCommand);
            installCommand.add(yumPackage.toName());

            String output = installCommand.executeSilently().getUntrimmedOutput();

            if (INSTALL_NOOP_PATTERN.matcher(output).find()) {
                if (CHECKING_FOR_UPDATE_PATTERN.matcher(output).find()) {
                    // case 3.
                    var upgradeCommand = terminal.newCommandLine(context).add("yum", "downgrade");
                    addParametersToCommandLine(upgradeCommand);
                    upgradeCommand.add(yumPackage.toName()).execute();
                    modified = true;
                } else {
                    // case 2.
                }
            } else {
                // case 1.
                installCommand.recordSilentExecutionAsSystemModification();
                modified = true;
            }

            return modified;
        }

        protected InstallFixedYumCommand getThis() { return this; }
    }

    public static class DeleteVersionLockYumCommand extends YumCommand<DeleteVersionLockYumCommand> {
        private final Terminal terminal;
        private final YumPackageName yumPackage;

        DeleteVersionLockYumCommand(Terminal terminal, YumPackageName yumPackage) {
            super(terminal);
            this.terminal = terminal;
            this.yumPackage = yumPackage;
        }

        @Override
        public boolean converge(TaskContext context) {
            return deleteVersionLock(context, terminal, yumPackage.toName());
        }

        protected DeleteVersionLockYumCommand getThis() { return this; }
    }

    protected boolean isInstalled(TaskContext context, YumPackageName yumPackage) {
        return queryInstalled(terminal, context, yumPackage).map(yumPackage::isSubsetOf).orElse(false);
    }

    static Optional<YumPackageName> queryInstalled(Terminal terminal, TaskContext context, YumPackageName yumPackage) {
        String packageName = yumPackage.toName();
        CommandResult commandResult = terminal.newCommandLine(context)
                .add("rpm", "-q", packageName, "--queryformat", RPM_QUERYFORMAT)
                .ignoreExitCode()
                .executeSilently();

        if (commandResult.getExitCode() != 0) return Optional.empty();

        YumPackageName.Builder builder = new YumPackageName.Builder();
        List<Function<String, YumPackageName.Builder>> builders = PACKAGE_NAME_BUILDERS_GENERATOR.apply(builder);
        List<Optional<String>> lines = commandResult.mapEachLine(line -> Optional.of(line).filter(s -> !"(none)".equals(s)));
        if (lines.size() % builders.size() != 0) throw new IllegalStateException(String.format("Unexpected response from rpm, expected %d lines, got '%s'", builders.size(), commandResult.getOutput()));
        if (lines.size() > builders.size()) throw new IllegalArgumentException("Found multiple installed packages for '" + packageName + "'. Version is required to match package exactly");

        IntStream.range(0, builders.size()).forEach(i -> lines.get(i).ifPresent(builders.get(i)::apply));
        if (builder.epoch().isEmpty()) builder.setEpoch("0");

        return Optional.of(builder.build());
    }

    private static Optional<String> versionLockExists(TaskContext context, Terminal terminal, YumPackageName yumPackage) {

        List<String> command = new ArrayList<>(4);
        command.add("yum");
        command.add("versionlock");
        command.add("list");

        return terminal
                .newCommandLine(context)
                .add(command)
                .executeSilently()
                .getOutputLinesStream()
                .map(YumPackageName::parseString)
                .filter(Optional::isPresent) // removes garbage first lines, even with --quiet
                .map(Optional::get)
                // Ignore lines for other packages
                .filter(packageName -> packageName.getName().equals(yumPackage.getName()))
                // If existing lock doesn't exactly match the full package name,
                // it means it's locked to another version and we must remove that lock.
                .map(YumPackageName::toVersionLockName)
                .findFirst();
    }

    private static boolean deleteVersionLock(TaskContext context, Terminal terminal, String wildcardEntry) {
        // Idempotent command, gives exit code 0 also when versionlock does not exist
        terminal.newCommandLine(context)
                       .add("yum", "versionlock", "delete", wildcardEntry)
                       .execute()
                       .getOutputLinesStream();
        return true;
    }

}

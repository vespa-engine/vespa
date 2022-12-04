// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.task.util.process.TestChildProcess2;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link Yum} tester that simplifies testing interaction with yum.
 *
 * @author freva
 */
public class YumTester extends Yum {

    private final TestTerminal terminal;

    public YumTester(TestTerminal terminal) {
        super(terminal);
        this.terminal = terminal;
    }

    public GenericYumCommandExpectation expectInstall(String... packages) {
        return new GenericYumCommandExpectation(CommandType.install, packages);
    }

    public GenericYumCommandExpectation expectUpdate(String... packages) {
        return new GenericYumCommandExpectation(CommandType.upgrade, packages);
    }

    public GenericYumCommandExpectation expectRemove(String... packages) {
        return new GenericYumCommandExpectation(CommandType.remove, packages);
    }

    public InstallFixedCommandExpectation expectInstallFixedVersion(String yumPackage) {
        return new InstallFixedCommandExpectation(yumPackage);
    }

    public DeleteVersionLockCommandExpectation expectDeleteVersionLock(String yumPackage) {
        return new DeleteVersionLockCommandExpectation(yumPackage);
    }

    public QueryInstalledExpectation expectQueryInstalled(String packageName) {
        return new QueryInstalledExpectation(packageName);
    }


    public class GenericYumCommandExpectation {
        private final CommandType commandType;
        protected final List<YumPackageName> packages;
        private List<String> enableRepos = List.of();

        private GenericYumCommandExpectation(CommandType commandType, String... packages) {
            this.commandType = commandType;
            this.packages = Stream.of(packages).map(YumPackageName::fromString).collect(Collectors.toList());
        }

        public GenericYumCommandExpectation withEnableRepo(String... repo) {
            this.enableRepos = List.of(repo);
            return this;
        }

        /** Mock the return value of the converge(TaskContext) method for this operation (true iff system was modified) */
        public YumTester andReturn(boolean value) {
            if (value) return execute("Success");
            return switch (commandType) {
                case deleteVersionLock, installFixed, install -> execute("Nothing to do");
                case upgrade -> execute("No packages marked for update");
                case remove -> execute("No Packages marked for removal");
                default -> throw new IllegalArgumentException("Unknown command type: " + commandType);
            };
        }

        private YumTester execute(String output) {
            if (commandType == CommandType.install)
                terminal.interceptCommand("rpm query", cmd -> new TestChildProcess2(1, "Not installed"));
            if (commandType == CommandType.remove) { // Pretend the first package is installed so we can continue to yum commands
                YumPackageName pkg = packages.get(0);
                terminal.interceptCommand("rpm query", cmd -> new TestChildProcess2(0, String.join("\n",
                        pkg.getName(),
                        pkg.getEpoch().orElse("(none)"),
                        pkg.getVersion().orElse("1.2.3"),
                        pkg.getRelease().orElse("1"),
                        pkg.getArchitecture().orElse("(none)"))));
            }

            StringBuilder cmd = new StringBuilder();
            cmd.append("yum ").append(commandType.command);
            if (commandType != CommandType.deleteVersionLock) {
                cmd.append(" --assumeyes");
                enableRepos.forEach(repo -> cmd.append(" --enablerepo=").append(repo));
            }
            if (commandType == CommandType.install && packages.size() > 1)
                cmd.append(" --setopt skip_missing_names_on_install=False");
            if (commandType == CommandType.upgrade && packages.size() > 1)
                cmd.append(" --setopt skip_missing_names_on_update=False");
            packages.forEach(pkg -> {
                String name = pkg.toName();
                if (name.contains("(") || name.contains(")")) { // Ugly hack to handle implicit quoting done in com.yahoo.vespa.hosted.node.admin.task.util.process.CommandLine
                    name = "\"" + name + "\"";
                }
                cmd.append(" ").append(name);
            });
            cmd.append(" 2>&1");

            terminal.expectCommand(cmd.toString(), 0, output);
            return YumTester.this;
        }
    }

    public class InstallFixedCommandExpectation extends GenericYumCommandExpectation {

        private InstallFixedCommandExpectation(String yumPackage) {
            super(CommandType.installFixed, yumPackage);
        }

        @Override
        public YumTester andReturn(boolean value) {
            terminal.expectCommand("yum versionlock list 2>&1", 0, packages.get(0).toVersionLockName());
            return super.andReturn(value);
        }

    }

    public class DeleteVersionLockCommandExpectation extends GenericYumCommandExpectation {

        private DeleteVersionLockCommandExpectation(String yumPackage) {
            super(CommandType.deleteVersionLock, yumPackage);
        }

    }

    public class QueryInstalledExpectation {
        private final String packageName;

        public QueryInstalledExpectation(String packageName) {
            this.packageName = packageName;
        }

        /** Package name to return or null if package is not installed */
        public YumTester andReturn(YumPackageName yumPackage) {
            TestChildProcess2 process = new TestChildProcess2(
                    yumPackage == null ? 1 : 0,
                    yumPackage == null ? "not installed" : String.join("\n",
                            yumPackage.getName(),
                            yumPackage.getEpoch().orElse("(none)"),
                            yumPackage.getVersion().orElseThrow(() -> new IllegalArgumentException("Version must be set")),
                            yumPackage.getRelease().orElseThrow(() -> new IllegalArgumentException("Release must be set")),
                            yumPackage.getArchitecture().orElse("(none)")));

            terminal.expectCommand("rpm -q " + packageName + " --queryformat \"%{NAME}\\\\n%{EPOCH}\\\\n%{VERSION}\\\\n%{RELEASE}\\\\n%{ARCH}\" 2>&1", process);
            return YumTester.this;
        }
    }

    private enum CommandType {
        install("install"), upgrade("upgrade"), remove("remove"), installFixed("install"), deleteVersionLock("versionlock delete");

        private final String command;
        CommandType(String command) {
            this.command = command;
        }
    }

}

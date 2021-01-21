// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.component.Version;
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
    private final Version yumVersion;

    public YumTester(TestTerminal terminal) {
        this(terminal, YumVersion.rhel7);
    }

    public YumTester(TestTerminal terminal, YumVersion yumVersion) {
        super(terminal);
        this.terminal = terminal;
        this.yumVersion = yumVersion.asVersion();
    }

    public Version yumVersion() {
        return yumVersion;
    }

    public GenericYumCommandExpectation expectInstall(String... packages) {
        return new GenericYumCommandExpectation("install", packages);
    }

    public GenericYumCommandExpectation expectUpdate(String... packages) {
        return new GenericYumCommandExpectation("upgrade", packages);
    }

    public GenericYumCommandExpectation expectRemove(String... packages) {
        return new GenericYumCommandExpectation("remove", packages);
    }

    public InstallFixedCommandExpectation expectInstallFixedVersion(String yumPackage) {
        return new InstallFixedCommandExpectation(yumPackage);
    }

    public QueryInstalledExpectation expectQueryInstalled(String packageName) {
        return new QueryInstalledExpectation(packageName);
    }


    public class GenericYumCommandExpectation {
        private final String command;
        protected final List<YumPackageName> packages;
        private List<String> enableRepos = List.of();

        private GenericYumCommandExpectation(String command, String... packages) {
            this.command = command;
            this.packages = Stream.of(packages).map(YumPackageName::fromString).collect(Collectors.toList());
        }

        public GenericYumCommandExpectation withEnableRepo(String... repo) {
            this.enableRepos = List.of(repo);
            return this;
        }

        /** Mock the return value of the converge(TaskContext) method for this operation (true iff system was modified) */
        public YumTester andReturn(boolean value) {
            if (value) return execute("Success");
            switch (command) {
                case "install": return execute("Nothing to do");
                case "upgrade": return execute("No packages marked for update");
                case "remove": return execute("No Packages marked for removal");
                default: throw new IllegalArgumentException("Unknown command: " + command);
            }
        }

        protected void expectYumVersion() {
            terminal.expectCommand("yum --version 2>&1", 0, yumVersion.toFullString() + "\ntrailing garbage\n");
        }

        private YumTester execute(String output) {
            StringBuilder cmd = new StringBuilder();
            cmd.append("yum ").append(command).append(" --assumeyes");
            enableRepos.forEach(repo -> cmd.append(" --enablerepo=").append(repo));
            if ("install".equals(command) && packages.size() > 1)
                cmd.append(" --setopt skip_missing_names_on_install=False");
            if ("upgrade".equals(command) && packages.size() > 1)
                cmd.append(" --setopt skip_missing_names_on_update=False");
            packages.forEach(pkg -> {
                String name = pkg.toName(yumVersion);
                if (name.contains("(") || name.contains(")")) { // Ugly hack to handle implicit quoting done in com.yahoo.vespa.hosted.node.admin.task.util.process.CommandLine
                    name = "\"" + name + "\"";
                }
                cmd.append(" ").append(name);
            });
            cmd.append(" 2>&1");

            expectYumVersion();
            terminal.expectCommand(cmd.toString(), 0, output);
            return YumTester.this;
        }
    }

    public class InstallFixedCommandExpectation extends GenericYumCommandExpectation {
        private InstallFixedCommandExpectation(String yumPackage) {
            super("install", yumPackage);
        }

        @Override
        protected void expectYumVersion() {}

        @Override
        public YumTester andReturn(boolean value) {
            // Pretend package is already correctly version locked to simplify expectations
            terminal.expectCommand("yum --version 2>&1", 0, yumVersion.toFullString() + "\ntrailing garbage\n");

            String quiet = yumVersion.getMajor() < 4 ? " --quiet" : "";
            terminal.expectCommand("yum" + quiet +" versionlock list 2>&1", 0, packages.get(0).toVersionLockName(yumVersion));
            return super.andReturn(value);
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

}

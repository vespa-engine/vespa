// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.ChildProcessFailureException;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * @author hakonhall
 */
public class YumTest {

    private final TaskContext taskContext = mock(TaskContext.class);
    private final TestTerminal terminal = new TestTerminal();
    private final Yum yum = new Yum(terminal);

    @AfterEach
    public void after() {
        terminal.verifyAllCommandsExecuted();
    }

    @Test
    void testQueryInstalled() {
        terminal.expectCommand(
                "rpm -q docker --queryformat \"%{NAME}\\\\n%{EPOCH}\\\\n%{VERSION}\\\\n%{RELEASE}\\\\n%{ARCH}\" 2>&1",
                0,
                "docker\n2\n1.13.1\n74.git6e3bb8e.el7.centos\nx86_64");

        Optional<YumPackageName> installed = yum.queryInstalled(taskContext, "docker");

        assertTrue(installed.isPresent());
        assertEquals("docker", installed.get().getName());
        assertEquals("2", installed.get().getEpoch().get());
        assertEquals("1.13.1", installed.get().getVersion().get());
        assertEquals("74.git6e3bb8e.el7.centos", installed.get().getRelease().get());
        assertEquals("x86_64", installed.get().getArchitecture().get());
    }

    @Test
    void testQueryInstalledPartial() {
        terminal.expectCommand(
                "rpm -q vespa-node-admin --queryformat \"%{NAME}\\\\n%{EPOCH}\\\\n%{VERSION}\\\\n%{RELEASE}\\\\n%{ARCH}\" 2>&1",
                0,
                "vespa-node-admin\n(none)\n6.283.62\n1.el7\nnoarch");

        Optional<YumPackageName> installed = yum.queryInstalled(taskContext, "vespa-node-admin");

        assertTrue(installed.isPresent());
        assertEquals("vespa-node-admin", installed.get().getName());
        assertEquals("0", installed.get().getEpoch().get());
        assertEquals("6.283.62", installed.get().getVersion().get());
        assertEquals("1.el7", installed.get().getRelease().get());
        assertEquals("noarch", installed.get().getArchitecture().get());
    }

    @Test
    void testQueryNotInstalled() {
        terminal.expectCommand(
                "rpm -q fake-package --queryformat \"%{NAME}\\\\n%{EPOCH}\\\\n%{VERSION}\\\\n%{RELEASE}\\\\n%{ARCH}\" 2>&1",
                1,
                "package fake-package is not installed");

        Optional<YumPackageName> installed = yum.queryInstalled(taskContext, "fake-package");

        assertFalse(installed.isPresent());
    }

    @Test
    void testQueryInstalledMultiplePackages() {
        terminal.expectCommand(
                "rpm -q kernel-devel --queryformat \"%{NAME}\\\\n%{EPOCH}\\\\n%{VERSION}\\\\n%{RELEASE}\\\\n%{ARCH}\" 2>&1",
                0,
                "kernel-devel\n" +
                        "(none)\n" +
                        "4.18.0\n" +
                        "305.7.1.el8_4\n" +
                        "x86_64\n" +
                        "kernel-devel\n" +
                        "(none)\n" +
                        "4.18.0\n" +
                        "240.15.1.el8_3\n" +
                        "x86_64\n");
        try {
            yum.queryInstalled(taskContext, "kernel-devel");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Found multiple installed packages for 'kernel-devel'. Version is required to match package exactly", e.getMessage());
        }
    }

    @Test
    void testAlreadyInstalled() {
        mockRpmQuery("package-1", null);
        terminal.expectCommand(
                "yum install --assumeyes --enablerepo=repo1 --enablerepo=repo2 --setopt skip_missing_names_on_install=False package-1 package-2 2>&1",
                0,
                "foobar\nNothing to do.\n"); // Note trailing dot
        assertFalse(yum.install("package-1", "package-2")
                .enableRepo("repo1", "repo2")
                .converge(taskContext));
    }

    @Test
    void testAlreadyUpgraded() {
        terminal.expectCommand(
                "yum upgrade --assumeyes --setopt skip_missing_names_on_update=False package-1 package-2 2>&1",
                0,
                "foobar\nNothing to do.\n"); // Same message as yum install no-op

        assertFalse(yum.upgrade("package-1", "package-2")
                .converge(taskContext));
    }

    @Test
    void testAlreadyRemoved() {
        mockRpmQuery("package-1", YumPackageName.fromString("package-1-1.2.3-1"));
        terminal.expectCommand(
                "yum remove --assumeyes package-1 package-2 2>&1",
                0,
                "foobar\nNo packages marked for removal.\n"); // Different output

        assertFalse(yum.remove("package-1", "package-2")
                .converge(taskContext));
    }

    @Test
    void skipsYumRemoveNotInRpm() {
        mockRpmQuery("package-1", null);
        mockRpmQuery("package-2", null);
        assertFalse(yum.remove("package-1", "package-2").converge(taskContext));
    }

    @Test
    void testInstall() {
        mockRpmQuery("package-1", null);
        terminal.expectCommand(
                "yum install --assumeyes --setopt skip_missing_names_on_install=False package-1 package-2 2>&1",
                0,
                "installing, installing");

        assertTrue(yum
                .install("package-1", "package-2")
                .converge(taskContext));
    }

    @Test
    void skipsYumInstallIfInRpm() {
        mockRpmQuery("package-1-0:1.2.3-1", YumPackageName.fromString("package-1-1.2.3-1"));
        mockRpmQuery("package-2", YumPackageName.fromString("1:package-2-1.2.3-1.el7.x86_64"));
        assertFalse(yum.install("package-1-1.2.3-1", "package-2").converge(taskContext));
    }

    @Test
    void testInstallWithEnablerepo() {
        mockRpmQuery("package-1", null);
        terminal.expectCommand(
                "yum install --assumeyes --enablerepo=repo-name --setopt skip_missing_names_on_install=False package-1 package-2 2>&1",
                0,
                "installing, installing");

        assertTrue(yum
                .install("package-1", "package-2")
                .enableRepo("repo-name")
                .converge(taskContext));
    }

    @Test
    void testWithVersionLock() {
        terminal.expectCommand("yum versionlock list 2>&1",
                0,
                "Last metadata expiration check: 0:51:26 ago on Thu 14 Jan 2021 09:39:24 AM UTC.\n");
        terminal.expectCommand("yum versionlock add --assumeyes \"openssh-0:8.0p1-4.el8_1.*\" 2>&1");
        terminal.expectCommand(
                "yum install --assumeyes openssh-0:8.0p1-4.el8_1.x86_64 2>&1",
                0,
                "installing");

        YumPackageName pkg = new YumPackageName
                .Builder("openssh")
                .setVersion("8.0p1")
                .setRelease("4.el8_1")
                .setArchitecture("x86_64")
                .build();
        assertTrue(yum.installFixedVersion(pkg).converge(taskContext));
    }

    @Test
    void testWithDifferentVersionLock() {
        terminal.expectCommand("yum versionlock list 2>&1",
                0,
                "Repository chef_rpms-release is listed more than once in the configuration\n" +
                        "chef-0:12.21.1-1.el7.*\n" +
                        "package-0:0.1-8.el7.*\n");

        terminal.expectCommand("yum versionlock delete \"package-0:0.1-8.el7.*\" 2>&1");

        terminal.expectCommand("yum versionlock add --assumeyes --enablerepo=somerepo \"package-0:0.10-654.el7.*\" 2>&1");

        terminal.expectCommand(
                "yum install --assumeyes --enablerepo=somerepo package-0:0.10-654.el7 2>&1",
                0,
                "Nothing to do\n");


        assertTrue(yum
                .installFixedVersion(YumPackageName.fromString("package-0:0.10-654.el7"))
                .enableRepo("somerepo")
                .converge(taskContext));
    }

    @Test
    void testWithExistingVersionLock() {
        terminal.expectCommand("yum versionlock list 2>&1",
                0,
                "Repository chef_rpms-release is listed more than once in the configuration\n" +
                        "chef-0:12.21.1-1.el7.*\n" +
                        "package-0:0.10-654.el7.*\n");
        terminal.expectCommand(
                "yum install --assumeyes package-0:0.10-654.el7 2>&1",
                0,
                "Nothing to do\n");

        assertFalse(yum.installFixedVersion(YumPackageName.fromString("package-0:0.10-654.el7")).converge(taskContext));
    }

    @Test
    void testWithDowngrade() {
        terminal.expectCommand("yum versionlock list 2>&1",
                0,
                "Repository chef_rpms-release is listed more than once in the configuration\n" +
                        "chef-0:12.21.1-1.el7.*\n" +
                        "package-0:0.10-654.el7.*\n");

        terminal.expectCommand(
                "yum install --assumeyes package-0:0.10-654.el7 2>&1",
                0,
                "Package matching package-=.0.10-654.el7 already installed. Checking for update.\n" +
                        "Nothing to do\n");

        terminal.expectCommand("yum downgrade --assumeyes package-0:0.10-654.el7 2>&1");

        assertTrue(yum.installFixedVersion(YumPackageName.fromString("package-0:0.10-654.el7")).converge(taskContext));
    }

    @Test
    void testFailedInstall() {
        assertThrows(ChildProcessFailureException.class, () -> {
            mockRpmQuery("package-1", null);
            terminal.expectCommand(
                    "yum install --assumeyes --enablerepo=repo-name --setopt skip_missing_names_on_install=False package-1 package-2 2>&1",
                    1,
                    "error");

            yum
                    .install("package-1", "package-2")
                    .enableRepo("repo-name")
                    .converge(taskContext);
            fail();
        });
    }

    @Test
    void testUnknownPackages() {
        mockRpmQuery("package-1", null);
        terminal.expectCommand(
                "yum install --assumeyes --setopt skip_missing_names_on_install=False package-1 package-2 package-3 2>&1",
                0,
                "Loaded plugins: fastestmirror, langpacks\n" +
                        "Loading mirror speeds from cached hostfile\n" +
                        "No package package-1 available.\n" +
                        "No package package-2 available.\n" +
                        "Nothing to do\n");

        var command = yum.install("package-1", "package-2", "package-3");
        try {
            command.converge(taskContext);
            fail();
        } catch (Exception e) {
            assertNotNull(e.getCause());
            assertEquals("Unknown package: package-1", e.getCause().getMessage());
        }
    }

    @Test
    void throwIfNoPackagesSpecified() {
        assertThrows(IllegalArgumentException.class, () -> {
            yum.install();
        });
    }

    @Test
    void allowToCallUpgradeWithNoPackages() {
        terminal.expectCommand("yum upgrade --assumeyes 2>&1", 0, "OK");
        yum.upgrade().converge(taskContext);
    }

    @Test
    void testDeleteVersionLock() {
        terminal.expectCommand("yum versionlock delete openssh-0:8.0p1-4.el8_1.x86_64 2>&1");

        YumPackageName pkg = new YumPackageName
                .Builder("openssh")
                .setVersion("8.0p1")
                .setRelease("4.el8_1")
                .setArchitecture("x86_64")
                .build();
        assertTrue(yum.deleteVersionLock(pkg).converge(taskContext));
    }

    private void mockRpmQuery(String packageName, YumPackageName installedOrNull) {
        new YumTester(terminal).expectQueryInstalled(packageName).andReturn(installedOrNull);
    }
}

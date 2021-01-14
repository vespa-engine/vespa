// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.ChildProcessFailureException;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;
import org.junit.After;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

/**
 * @author hakonhall
 */
public class YumTest {

    private final TaskContext taskContext = mock(TaskContext.class);
    private final TestTerminal terminal = new TestTerminal();
    private final Yum yum = new Yum(terminal);

    @After
    public void after() {
        terminal.verifyAllCommandsExecuted();
    }

    @Test
    public void testQueryInstalledNevra() {
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
    public void testQueryInstalledPartial() {
        terminal.expectCommand(
                "rpm -q vespa-node-admin --queryformat \"%{NAME}\\\\n%{EPOCH}\\\\n%{VERSION}\\\\n%{RELEASE}\\\\n%{ARCH}\" 2>&1",
                0,
                "vespa-node-admin\n(none)\n6.283.62\n1.el7\nnoarch");

        Optional<YumPackageName> installed = yum.queryInstalled(taskContext, "vespa-node-admin");

        assertTrue(installed.isPresent());
        assertEquals("vespa-node-admin", installed.get().getName());
        assertFalse(installed.get().getEpoch().isPresent());
        assertEquals("6.283.62", installed.get().getVersion().get());
        assertEquals("1.el7", installed.get().getRelease().get());
        assertEquals("noarch", installed.get().getArchitecture().get());
    }

    @Test
    public void testQueryNotInstalled() {
        terminal.expectCommand(
                "rpm -q fake-package --queryformat \"%{NAME}\\\\n%{EPOCH}\\\\n%{VERSION}\\\\n%{RELEASE}\\\\n%{ARCH}\" 2>&1",
                1,
                "package fake-package is not installed");

        Optional<YumPackageName> installed = yum.queryInstalled(taskContext, "fake-package");

        assertFalse(installed.isPresent());
    }

    @Test
    public void testAlreadyInstalled() {
        mockYumVersion();
        terminal.expectCommand(
                "yum install --assumeyes --enablerepo=repo1 --enablerepo=repo2 --setopt skip_missing_names_on_install=False package-1 package-2 2>&1",
                0,
                "foobar\nNothing to do\n");

        assertFalse(yum
                .install("package-1", "package-2")
                .enableRepo("repo1", "repo2")
                .converge(taskContext));

        // RHEL 8
        mockYumVersion(YumVersion.rhel8);
        terminal.expectCommand(
                "yum install --assumeyes --enablerepo=repo1 --enablerepo=repo2 --setopt skip_missing_names_on_install=False package-1 package-2 2>&1",
                0,
                "foobar\nNothing to do.\n"); // Note trailing dot
        assertFalse(yum.install("package-1", "package-2")
                       .enableRepo("repo1", "repo2")
                       .converge(taskContext));
    }

    @Test
    public void testAlreadyUpgraded() {
        mockYumVersion();
        terminal.expectCommand(
                "yum upgrade --assumeyes --setopt skip_missing_names_on_update=False package-1 package-2 2>&1",
                0,
                "foobar\nNo packages marked for update\n");

        assertFalse(yum
                .upgrade("package-1", "package-2")
                .converge(taskContext));

        // RHEL 8
        mockYumVersion(YumVersion.rhel8);
        terminal.expectCommand(
                "yum upgrade --assumeyes --setopt skip_missing_names_on_update=False package-1 package-2 2>&1",
                0,
                "foobar\nNothing to do.\n"); // Same message as yum install no-op

        assertFalse(yum.upgrade("package-1", "package-2")
                       .converge(taskContext));
    }

    @Test
    public void testAlreadyRemoved() {
        mockYumVersion();
        terminal.expectCommand(
                "yum remove --assumeyes package-1 package-2 2>&1",
                0,
                "foobar\nNo Packages marked for removal\n");

        assertFalse(yum
                .remove("package-1", "package-2")
                .converge(taskContext));

        // RHEL 8
        mockYumVersion(YumVersion.rhel8);
        terminal.expectCommand(
                "yum remove --assumeyes package-1 package-2 2>&1",
                0,
                "foobar\nNo packages marked for removal.\n"); // Different output

        assertFalse(yum.remove("package-1", "package-2")
                       .converge(taskContext));
    }

    @Test
    public void testInstall() {
        mockYumVersion();
        terminal.expectCommand(
                "yum install --assumeyes --setopt skip_missing_names_on_install=False package-1 package-2 2>&1",
                0,
                "installing, installing");

        assertTrue(yum
                .install("package-1", "package-2")
                .converge(taskContext));
    }

    @Test
    public void testInstallWithEnablerepo() {
        mockYumVersion();
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
    public void testWithVersionLock() {
        mockYumVersion();
        terminal.expectCommand("yum --quiet versionlock list 2>&1",
                0,
                "Repository chef_rpms-release is listed more than once in the configuration\n" +
                        "0:chef-12.21.1-1.el7.*\n");
        terminal.expectCommand("yum versionlock add --assumeyes \"0:package-1-0.10-654.el7.*\" 2>&1");
        terminal.expectCommand(
                "yum install --assumeyes 0:package-1-0.10-654.el7.x86_64 2>&1",
                0,
                "installing");

        assertTrue(yum.installFixedVersion(YumPackageName.fromString("0:package-1-0.10-654.el7.x86_64")).converge(taskContext));
    }

    @Test
    public void testWithVersionLockYum4() {
        mockYumVersion(YumVersion.rhel8);
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
    public void testWithDifferentVersionLock() {
        mockYumVersion();
        terminal.expectCommand("yum --quiet versionlock list 2>&1",
                0,
                "Repository chef_rpms-release is listed more than once in the configuration\n" +
                        "0:chef-12.21.1-1.el7.*\n" +
                        "0:package-1-0.1-8.el7.*\n");

        terminal.expectCommand("yum versionlock delete \"0:package-1-0.1-8.el7.*\" 2>&1");

        terminal.expectCommand("yum versionlock add --assumeyes --enablerepo=somerepo \"0:package-1-0.10-654.el7.*\" 2>&1");

        terminal.expectCommand(
                "yum install --assumeyes --enablerepo=somerepo 0:package-1-0.10-654.el7 2>&1",
                0,
                "Nothing to do\n");


        assertTrue(yum
                .installFixedVersion(YumPackageName.fromString("0:package-1-0.10-654.el7"))
                .enableRepo("somerepo")
                .converge(taskContext));
    }

    @Test
    public void testWithExistingVersionLock() {
        mockYumVersion();
        terminal.expectCommand("yum --quiet versionlock list 2>&1",
                0,
                "Repository chef_rpms-release is listed more than once in the configuration\n" +
                        "0:chef-12.21.1-1.el7.*\n" +
                        "0:package-1-0.10-654.el7.*\n");
        terminal.expectCommand(
                "yum install --assumeyes 0:package-1-0.10-654.el7 2>&1",
                0,
                "Nothing to do\n");

        assertFalse(yum.installFixedVersion(YumPackageName.fromString("0:package-1-0.10-654.el7")).converge(taskContext));
    }

    @Test
    public void testWithDowngrade() {
        mockYumVersion();
        terminal.expectCommand("yum --quiet versionlock list 2>&1",
                0,
                "Repository chef_rpms-release is listed more than once in the configuration\n" +
                        "0:chef-12.21.1-1.el7.*\n" +
                        "0:package-1-0.10-654.el7.*\n");

        terminal.expectCommand(
                "yum install --assumeyes 0:package-1-0.10-654.el7 2>&1",
                0,
                "Package matching package-1-0.10-654.el7 already installed. Checking for update.\n" +
                        "Nothing to do\n");

        terminal.expectCommand("yum downgrade --assumeyes 0:package-1-0.10-654.el7 2>&1");

        assertTrue(yum.installFixedVersion(YumPackageName.fromString("0:package-1-0.10-654.el7")).converge(taskContext));
    }

    @Test(expected = ChildProcessFailureException.class)
    public void testFailedInstall() {
        mockYumVersion();
        terminal.expectCommand(
                "yum install --assumeyes --enablerepo=repo-name --setopt skip_missing_names_on_install=False package-1 package-2 2>&1",
                1,
                "error");

        yum
                .install("package-1", "package-2")
                .enableRepo("repo-name")
                .converge(taskContext);
        fail();
    }

    @Test
    public void testUnknownPackages() {
        mockYumVersion();
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

    @Test(expected = IllegalArgumentException.class)
    public void throwIfNoPackagesSpecified() {
        yum.install();
    }

    @Test
    public void allowToCallUpgradeWithNoPackages() {
        mockYumVersion();
        terminal.expectCommand("yum upgrade --assumeyes 2>&1", 0, "OK");
        yum.upgrade().converge(taskContext);
    }

    private void mockYumVersion(YumVersion yumVersion) {
        terminal.expectCommand("yum --version 2>&1", 0, yumVersion.asVersion().toFullString() + "\ntrailing garbage\n");
    }

    private void mockYumVersion() {
        mockYumVersion(YumVersion.rhel7);
    }

}

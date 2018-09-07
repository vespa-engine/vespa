// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.ChildProcessFailureException;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class YumTest {
    private final TaskContext taskContext = mock(TaskContext.class);
    private final TestTerminal terminal = new TestTerminal();
    private final Yum yum = new Yum(terminal);

    @After
    public void tearDown() {
        terminal.verifyAllCommandsExecuted();
    }

    @Test
    public void testArrayConversion() {
        YumPackageName[] expected = new YumPackageName[] { new YumPackageName.Builder("1").build() };
        assertArrayEquals(expected, Yum.toYumPackageNameArray("1"));

        YumPackageName[] expected2 = new YumPackageName[] {
                new YumPackageName.Builder("1").build(),
                new YumPackageName.Builder("2").build()
        };
        assertArrayEquals(expected2, Yum.toYumPackageNameArray("1", "2"));

        YumPackageName[] expected3 = new YumPackageName[] {
                new YumPackageName.Builder("1").build(),
                new YumPackageName.Builder("2").build(),
                new YumPackageName.Builder("3").build()
        };
        assertArrayEquals(expected3, Yum.toYumPackageNameArray("1", "2", "3"));
    }

    @Test
    public void testAlreadyInstalled() {
        terminal.expectCommand(
                "yum install --assumeyes --enablerepo=repo1 --enablerepo=repo2 package-1 package-2 2>&1",
                0,
                "foobar\nNothing to do\n");

        assertFalse(yum
                .install("package-1", "package-2")
                .enableRepos("repo1", "repo2")
                .converge(taskContext));
    }

    @Test
    public void testAlreadyUpgraded() {
        terminal.expectCommand(
                "yum upgrade --assumeyes package-1 package-2 2>&1",
                0,
                "foobar\nNo packages marked for update\n");

        assertFalse(yum
                .upgrade("package-1", "package-2")
                .converge(taskContext));
    }

    @Test
    public void testAlreadyRemoved() {
        terminal.expectCommand(
                "yum remove --assumeyes package-1 package-2 2>&1",
                0,
                "foobar\nNo Packages marked for removal\n");

        assertFalse(yum
                .remove("package-1", "package-2")
                .converge(taskContext));
    }

    @Test
    public void testInstall() {
        terminal.expectCommand(
                "yum install --assumeyes package-1 package-2 2>&1",
                0,
                "installing, installing");

        assertTrue(yum
                .install("package-1", "package-2")
                .converge(taskContext));
    }

    @Test
    public void testInstallWithEnablerepo() {
        terminal.expectCommand(
                "yum install --assumeyes --enablerepo=repo-name package-1 package-2 2>&1",
                0,
                "installing, installing");

        assertTrue(yum
                .install("package-1", "package-2")
                .enableRepos("repo-name")
                .converge(taskContext));
    }

    @Test
    public void testWithVersionLock() {
        terminal.expectCommand("yum --quiet versionlock list 2>&1",
                0,
                "Repository chef_rpms-release is listed more than once in the configuration\n" +
                        "0:chef-12.21.1-1.el7.*\n");
        terminal.expectCommand("yum versionlock add \"0:package-1-0.10-654.el7.*\" 2>&1");
        terminal.expectCommand(
                "yum install --assumeyes 0:package-1-0.10-654.el7.x86_64 2>&1",
                0,
                "installing");

        assertTrue(yum.installFixedVersion(taskContext,
                YumPackageName.fromString("0:package-1-0.10-654.el7.x86_64")));
    }

    @Test
    public void testWithDifferentVersionLock() {
        terminal.expectCommand("yum --quiet versionlock list 2>&1",
                0,
                "Repository chef_rpms-release is listed more than once in the configuration\n" +
                        "0:chef-12.21.1-1.el7.*\n" +
                        "0:package-1-0.1-8.el7.*\n");

        terminal.expectCommand("yum versionlock delete \"0:package-1-0.1-8.el7.*\" 2>&1");

        terminal.expectCommand("yum versionlock add \"0:package-1-0.10-654.el7.*\" 2>&1");

        terminal.expectCommand(
                "yum install --assumeyes 0:package-1-0.10-654.el7 2>&1",
                0,
                "Nothing to do\n");


        assertTrue(yum.installFixedVersion(taskContext, YumPackageName.fromString("0:package-1-0.10-654.el7")));
    }

    @Test
    public void testWithExistingVersionLock() {
        terminal.expectCommand("yum --quiet versionlock list 2>&1",
                0,
                "Repository chef_rpms-release is listed more than once in the configuration\n" +
                        "0:chef-12.21.1-1.el7.*\n" +
                        "0:package-1-0.10-654.el7.*\n");
        terminal.expectCommand(
                "yum install --assumeyes 0:package-1-0.10-654.el7 2>&1",
                0,
                "Nothing to do\n");

        assertFalse(yum.installFixedVersion(taskContext, YumPackageName.fromString("0:package-1-0.10-654.el7")));
    }

    @Test
    public void testWithDowngrade() {
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

        assertTrue(yum.installFixedVersion(taskContext, YumPackageName.fromString("0:package-1-0.10-654.el7")));
    }

    @Test(expected = ChildProcessFailureException.class)
    public void testFailedInstall() {
        terminal.expectCommand(
                "yum install --assumeyes --enablerepo=repo-name package-1 package-2 2>&1",
                1,
                "error");

        yum.install("package-1", "package-2")
                .enableRepos("repo-name")
                .converge(taskContext);
        fail();
    }

    @Test
    public void testUnknownPackages() {
        terminal.expectCommand(
                "yum install --assumeyes package-1 package-2 package-3 2>&1",
                0,
                "Loaded plugins: fastestmirror, langpacks\n" +
                        "Loading mirror speeds from cached hostfile\n" +
                        "No package package-1 available.\n" +
                        "No package package-2 available.\n" +
                        "Nothing to do\n");

        Yum.GenericYumCommand install = yum.install("package-1", "package-2", "package-3");

        try {
            install.converge(taskContext);
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
        terminal.expectCommand("yum upgrade --assumeyes 2>&1", 0, "OK");

        yum.upgrade().converge(taskContext);
    }
}
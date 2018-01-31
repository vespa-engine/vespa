// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandException;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestCommandSupplier;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class YumTest {
    TaskContext taskContext = mock(TaskContext.class);
    TestCommandSupplier commandSupplier = new TestCommandSupplier(taskContext);

    @Before
    public void tearDown() {
        commandSupplier.verifyInvocations();
    }

    @Test
    public void testAlreadyInstalled() {
        commandSupplier.expectCommand(
                "yum install --assumeyes --enablerepo=repo-name package-1 package-2",
                0,
                "foobar\nNothing to do\n");

        Yum yum = new Yum(taskContext, commandSupplier);
        assertFalse(yum
                .install("package-1", "package-2")
                .enableRepo("repo-name")
                .converge());
    }

    @Test
    public void testAlreadyUpgraded() {
        commandSupplier.expectCommand(
                "yum upgrade --assumeyes package-1 package-2",
                0,
                "foobar\nNo packages marked for update\n");

        assertFalse(new Yum(taskContext, commandSupplier)
                .upgrade("package-1", "package-2")
                .converge());
    }

    @Test
    public void testAlreadyRemoved() {
        commandSupplier.expectCommand(
                "yum remove --assumeyes package-1 package-2",
                0,
                "foobar\nNo Packages marked for removal\n");

        assertFalse(new Yum(taskContext, commandSupplier)
                .remove("package-1", "package-2")
                .converge());
    }

    @Test
    public void testInstall() {
        commandSupplier.expectCommand(
                "yum install --assumeyes package-1 package-2",
                0,
                "installing, installing");

        Yum yum = new Yum(taskContext, commandSupplier);
        assertTrue(yum
                .install("package-1", "package-2")
                .converge());
    }

    @Test
    public void testInstallWithEnablerepo() {
        commandSupplier.expectCommand(
                "yum install --assumeyes --enablerepo=repo-name package-1 package-2",
                0,
                "installing, installing");

        Yum yum = new Yum(taskContext, commandSupplier);
        assertTrue(yum
                .install("package-1", "package-2")
                .enableRepo("repo-name")
                .converge());
    }

    @Test(expected = CommandException.class)
    public void testFailedInstall() {
        commandSupplier.expectCommand(
                "yum install --assumeyes --enablerepo=repo-name package-1 package-2",
                1,
                "error");

        Yum yum = new Yum(taskContext, commandSupplier);
        yum.install("package-1", "package-2")
                .enableRepo("repo-name")
                .converge();
        fail();
    }
}
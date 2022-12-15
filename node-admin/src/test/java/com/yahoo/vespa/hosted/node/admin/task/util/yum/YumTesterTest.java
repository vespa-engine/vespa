// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class YumTesterTest {

    private static final String[] packages = {"pkg1", "pkg2"};
    private static final String[] repos = {"repo1", "repo2"};
    private static final YumPackageName minimalPackage = YumPackageName.fromString("pkg-1.13.1-0.el7");
    private static final YumPackageName fullPackage = YumPackageName.fromString("2:pkg-1.13.1-0.el7.x86_64");

    private final TestTerminal terminal = new TestTerminal();
    private final YumTester yum = new YumTester(terminal);
    private final TestTaskContext context = new TestTaskContext();

    @Test
    void generic_yum_methods() {
        assertYumMethod(yum -> yum.expectInstall(packages).withEnableRepo(repos),
                yum -> yum.install(List.of(packages)).enableRepo(repos).converge(context));

        assertYumMethod(yum -> yum.expectUpdate(packages).withEnableRepo(repos),
                yum -> yum.upgrade(List.of(packages)).enableRepo(repos).converge(context));

        assertYumMethod(yum -> yum.expectRemove(packages).withEnableRepo(repos),
                yum -> yum.remove(List.of(packages)).enableRepo(repos).converge(context));

        assertYumMethod(yum -> yum.expectInstallFixedVersion(minimalPackage.toName()).withEnableRepo(repos),
                yum -> yum.installFixedVersion(minimalPackage).enableRepo(repos).converge(context));

        // versionlock always returns success
        assertYumMethodAlwaysSuccess(yum -> yum.expectDeleteVersionLock(minimalPackage.toName()),
                yum -> yum.deleteVersionLock(minimalPackage).converge(context));

    }

    @Test
    void expect_query_installed() {
        yum.expectQueryInstalled(packages[0]).andReturn(fullPackage);
        assertEquals(Optional.of(fullPackage), yum.queryInstalled(context, packages[0]));
        terminal.verifyAllCommandsExecuted();
    }

    private void assertYumMethod(Function<YumTester, YumTester.GenericYumCommandExpectation> yumTesterExpectationFunction,
                                 Function<Yum, Boolean> yumFunction) {
        List.of(true, false).forEach(wantedReturnValue -> {
            yumTesterExpectationFunction.apply(yum).andReturn(wantedReturnValue);
            assertEquals(wantedReturnValue, yumFunction.apply(yum));
            terminal.verifyAllCommandsExecuted();
        });
    }

    private void assertYumMethodAlwaysSuccess(Function<YumTester, YumTester.GenericYumCommandExpectation> yumTesterExpectationFunction,
                                              Function<Yum, Boolean> yumFunction) {
        List.of(true, false).forEach(wantedReturnValue -> {
            yumTesterExpectationFunction.apply(yum).andReturn(wantedReturnValue);
            assertEquals(true, yumFunction.apply(yum));
            terminal.verifyAllCommandsExecuted();
        });
    }

}

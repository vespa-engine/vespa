// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.systemd;

import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class SystemCtlTesterTest {

    private static final String unit = "my-unit";
    private final TestTerminal terminal = new TestTerminal();
    private final SystemCtlTester systemCtl = new SystemCtlTester(terminal);
    private final TestTaskContext context = new TestTaskContext();

    @Test
    void return_expectations() {
        assertSystemCtlMethod(sct -> sct.expectEnable(unit), sc -> sc.enable(unit).converge(context));
        assertSystemCtlMethod(sct -> sct.expectDisable(unit), sc -> sc.disable(unit).converge(context));
        assertSystemCtlMethod(sct -> sct.expectStart(unit), sc -> sc.start(unit).converge(context));
        assertSystemCtlMethod(sct -> sct.expectStop(unit), sc -> sc.stop(unit).converge(context));
        assertSystemCtlMethod(sct -> sct.expectServiceExists(unit), sc -> sc.serviceExists(context, unit));
        assertSystemCtlMethod(sct -> sct.expectIsActive(unit), sc -> sc.isActive(context, unit));
    }

    @Test
    void void_tests() {
        systemCtl.expectRestart(unit);
        systemCtl.restart(unit).converge(context);
        terminal.verifyAllCommandsExecuted();

        systemCtl.expectDaemonReload();
        systemCtl.daemonReload(context);
        terminal.verifyAllCommandsExecuted();
    }

    private void assertSystemCtlMethod(Function<SystemCtlTester, SystemCtlTester.Expectation> systemCtlTesterExpectationFunction,
                                       Function<SystemCtl, Boolean> systemCtlFunction) {
        List.of(true, false).forEach(wantedReturnValue -> {
            systemCtlTesterExpectationFunction.apply(systemCtl).andReturn(wantedReturnValue);
            assertEquals(wantedReturnValue, systemCtlFunction.apply(systemCtl));
            terminal.verifyAllCommandsExecuted();
        });
    }
}
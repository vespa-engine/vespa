// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.systemd;

import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;

import java.util.function.Consumer;

/**
 * A {@link SystemCtl} tester that simplifies testing interaction with systemd units.
 *
 * @author mpolden
 */
public class SystemCtlTester extends SystemCtl {

    private final TestTerminal terminal;

    public SystemCtlTester(TestTerminal terminal) {
        super(terminal);
        this.terminal = terminal;
    }

    public Expectation expectServiceExists(String unit) {
        return new Expectation(wantedReturn ->
                expectCommand("systemctl list-unit-files " + unit + ".service 2>&1", 0, (wantedReturn ? 1 : 0) + " unit files listed."));
    }

    public Expectation expectIsActive(String unit) {
        return new Expectation(wantedReturn -> {
            expectCommand("systemctl --quiet is-active " + unit + ".service 2>&1", wantedReturn ? 0 : 1, "");
        });
    }

    public Expectation expectEnable(String unit) { return forChangeEnabledState(unit, true); }
    public Expectation expectDisable(String unit) { return forChangeEnabledState(unit, false); }
    public Expectation expectStart(String unit) { return forChangeRunningState(unit, true); }
    public Expectation expectStop(String unit) { return forChangeRunningState(unit, false); }

    public SystemCtlTester expectRestart(String unit) {
        expectCommand("systemctl restart " + unit + " 2>&1", 0, "");
        return this;
    }

    public SystemCtlTester expectDaemonReload() {
        expectCommand("systemctl daemon-reload 2>&1", 0, "");
        return this;
    }

    public SystemCtlTester expectGetServiceProperty(String unit, String property, String output) {
        expectCommand("systemctl show --property " + property + " --value " + unit + ".service 2>&1", 0, output);
        return this;
    }

    private void expectCommand(String command, int exitCode, String output) {
        terminal.expectCommand((useSudo() ? "sudo " : "") + command, exitCode, output);
    }

    private Expectation forChangeEnabledState(String unit, boolean enable) {
        return new Expectation(wantedReturn -> {
            expectCommand("systemctl --quiet is-enabled " + unit + " 2>&1", enable != wantedReturn ? 0 : 1, "");
            if (wantedReturn)
                expectCommand("systemctl " + (enable ? "enable" : "disable") + " " + unit + " 2>&1", 0, "");
        });
    }

    private Expectation forChangeRunningState(String unit, boolean start) {
        return new Expectation(wantedReturn -> {
            expectCommand("systemctl show " + unit + " 2>&1", 0, "ActiveState=" + (start != wantedReturn ? "active" : "inactive"));
            if (wantedReturn)
                expectCommand("systemctl " + (start ? "start" : "stop") + " " + unit + " 2>&1", 0, "");
        });
    }

    public class Expectation {
        private final Consumer<Boolean> converger;
        public Expectation(Consumer<Boolean> converger) {
            this.converger = converger;
        }

        /** Mock the return value of the converge(TaskContext) method for this operation (true iff system was modified) */
        public SystemCtlTester andReturn(boolean value) {
            converger.accept(value);
            return SystemCtlTester.this;
        }
    }

}

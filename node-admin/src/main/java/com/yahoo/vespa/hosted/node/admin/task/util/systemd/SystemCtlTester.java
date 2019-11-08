// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.systemd;

import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;

import java.util.HashSet;
import java.util.Set;

/**
 * A {@link SystemCtl} tester that simplifies testing interaction with systemd units.
 *
 * @author mpolden
 */
public class SystemCtlTester extends SystemCtl {

    private final Set<String> runningUnits = new HashSet<>();

    private TestTerminal terminal;

    public SystemCtlTester(TestTerminal terminal) {
        super(terminal);
        this.terminal = terminal;
    }

    /** Create expectation for given unit */
    public Expectation expect(String unit) {
        return new Expectation(unit, this);
    }

    private void startUnit(String unit) {
        runningUnits.add(unit);
    }

    public static class Expectation {

        private final String unit;
        private final SystemCtlTester systemCtl;

        public Expectation(String unit, SystemCtlTester systemCtl) {
            this.unit = unit;
            this.systemCtl = systemCtl;
        }

        /** Create expectation for given unit */
        public Expectation expect(String name) {
            return systemCtl.expect(name);
        }

        /** Expect that this will be started */
        public Expectation toStart() {
            return toStart(true);
        }

        /** Expect that this is already started */
        public Expectation isStarted() {
            return toStart(false);
        }

        /** Expect that given unit will be restarted */
        public Expectation toRestart() {
            systemCtl.terminal.expectCommand("systemctl restart " + unit + " 2>&1", 0, "");
            systemCtl.startUnit(unit);
            return this;
        }

        /** Expect that this will be stopped */
        public Expectation toStop() {
            systemCtl.terminal.expectCommand("systemctl stop " + unit + " 2>&1", 0, "");
            systemCtl.runningUnits.remove(unit);
            return this;
        }

        /** Expect query for state of this */
        public Expectation toQueryState() {
            systemCtl.terminal.expectCommand("systemctl --quiet is-active " + unit + ".service 2>&1",
                                             systemCtl.runningUnits.contains(unit) ? 0 : 1, "");
            return this;
        }

        /** Expect that this will be enabled */
        public Expectation toEnable() {
            return toEnable(true);
        }

        /** Expect that given unit is already enabled */
        public Expectation isEnabled() {
            return toEnable(false);
        }

        private Expectation toStart(boolean start) {
            systemCtl.terminal.expectCommand("systemctl show " + unit + " 2>&1", 0,
                                   "ActiveState=" + (start ? "inactive" : "active"));
            if (start) {
                systemCtl.terminal.expectCommand("systemctl start " + unit + " 2>&1", 0, "");
                systemCtl.startUnit(unit);
            }
            return this;
        }

        private Expectation toEnable(boolean enable) {
            systemCtl.terminal.expectCommand("systemctl --quiet is-enabled " + unit + " 2>&1", enable ? 1 : 0, "");
            if (enable) {
                systemCtl.terminal.expectCommand("systemctl enable " + unit + " 2>&1", 0, "");
            }
            return this;
        }

    }

}

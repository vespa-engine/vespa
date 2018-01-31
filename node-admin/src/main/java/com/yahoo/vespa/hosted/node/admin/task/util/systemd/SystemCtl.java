// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.systemd;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.ChildProcess;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Command;
import com.yahoo.vespa.hosted.node.admin.task.util.process.UnexpectedOutputException;

import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Control the systemd system and service manager
 *
 * @author hakonhall
 */
public class SystemCtl {
    private static final Logger logger = Logger.getLogger(SystemCtl.class.getName());

    // Valid systemd property names from looking at a couple of services.
    private static final Pattern PROPERTY_NAME_PATTERN = Pattern.compile("^[a-zA-Z]+$");

    // Patterns matching properties output by the 'systemctl show' command.
    private static final Pattern UNIT_FILE_STATE_PROPERTY_PATTERN = createPropertyPattern("UnitFileState");
    private static final Pattern ACTIVE_STATE_PROPERTY_PATTERN = createPropertyPattern("ActiveState");

    private final Function<TaskContext, Command> commandSupplier;

    private static Pattern createPropertyPattern(String propertyName) {
        if (!PROPERTY_NAME_PATTERN.matcher(propertyName).matches()) {
            throw new IllegalArgumentException("Property name does not match " + PROPERTY_NAME_PATTERN);
        }

        // Make ^ and $ match beginning and end of lines.
        String regex = String.format("(?md)^%s=(.*)$", propertyName);

        return Pattern.compile(regex);
    }

    public SystemCtl() {
        this(Command::new);
    }

    public SystemCtlEnable enable(String unit) { return new SystemCtlEnable(unit); }
    public SystemCtlDisable disable(String unit) { return new SystemCtlDisable(unit); }
    public SystemCtlStart start(String unit) { return new SystemCtlStart(unit); }
    public SystemCtlStop stop(String unit) { return new SystemCtlStop(unit); }
    public SystemCtlRestart restart(String unit) { return new SystemCtlRestart(unit); }

    public class SystemCtlEnable extends SystemCtlCommand {
        private SystemCtlEnable(String unit) {
            super("enable", unit);
        }

        protected boolean isAlreadyConverged(TaskContext context) {
            ChildProcess showProcess = systemCtlShow(context);
            String unitFileState = extractProperty(showProcess, UNIT_FILE_STATE_PROPERTY_PATTERN);
            return Objects.equals(unitFileState, "enabled");
        }
    }

    public class SystemCtlDisable extends SystemCtlCommand {
        private SystemCtlDisable(String unit) {
            super("disable", unit);
        }

        protected boolean isAlreadyConverged(TaskContext context) {
            ChildProcess showProcess = systemCtlShow(context);
            String unitFileState = extractProperty(showProcess, UNIT_FILE_STATE_PROPERTY_PATTERN);
            return Objects.equals(unitFileState, "disabled");
        }
    }

    public class SystemCtlStart extends SystemCtlCommand {
        private SystemCtlStart(String unit) {
            super("start", unit);
        }

        protected boolean isAlreadyConverged(TaskContext context) {
            ChildProcess showProcess = systemCtlShow(context);
            String activeState = extractProperty(showProcess, ACTIVE_STATE_PROPERTY_PATTERN);
            return Objects.equals(activeState, "active");
        }
    }

    public class SystemCtlStop extends SystemCtlCommand {
        private SystemCtlStop(String unit) {
            super("stop", unit);
        }

        protected boolean isAlreadyConverged(TaskContext context) {
            ChildProcess showProcess = systemCtlShow(context);
            String activeState = extractProperty(showProcess, ACTIVE_STATE_PROPERTY_PATTERN);
            return Objects.equals(activeState, "inactive");
        }
    }

    public class SystemCtlRestart extends SystemCtlCommand {
        private SystemCtlRestart(String unit) {
            super("restart", unit);
        }

        protected boolean isAlreadyConverged(TaskContext context) {
            return false;
        }
    }

    private abstract class SystemCtlCommand {
        private final String command;
        private final String unit;

        private SystemCtlCommand(String command, String unit) {
            this.command = command;
            this.unit = unit;
        }

        protected abstract boolean isAlreadyConverged(TaskContext context);

        public boolean converge(TaskContext context) {
            if (isAlreadyConverged(context)) {
                return false;
            }

            commandSupplier.apply(context)
                    .add("systemctl", command, unit)
                    .spawn(logger)
                    .waitForTermination()
                    .throwIfFailed();

            return true;
        }

        /**
         * Find the systemd property value of the property (given by propertyPattern)
         * matching the 'systemctl show' output (given by showProcess).
         */
        protected String extractProperty(ChildProcess showProcess, Pattern propertyPattern) {
            String output = showProcess.getUtf8Output();
            Matcher matcher = propertyPattern.matcher(output);
            if (!matcher.find()) {
                throw new UnexpectedOutputException(
                        "Output does not match '" + propertyPattern + "'", showProcess);
            } else if (matcher.groupCount() != 1) {
                throw new IllegalArgumentException("Property pattern must have exactly 1 group");
            }

            return matcher.group(1);
        }

        protected ChildProcess systemCtlShow(TaskContext context) {
            ChildProcess process = commandSupplier.apply(context)
                    .add("systemctl", "show", unit)
                    .spawnProgramWithoutSideEffects()
                    .waitForTermination()
                    .throwIfFailed();

            // Make sure we're able to parse UTF-8 output.
            process.getUtf8Output();

            return process;
        }
    }

    // For testing
    SystemCtl(Function<TaskContext, Command> commandSupplier) {
        this.commandSupplier = commandSupplier;
    }
}

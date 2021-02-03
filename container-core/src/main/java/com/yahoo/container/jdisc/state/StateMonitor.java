// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A state monitor keeps track of the current health state of a container.
 *
 * @author Simon Thoresen Hult
 */
public class StateMonitor extends AbstractComponent {

    private final static Logger log = Logger.getLogger(StateMonitor.class.getName());

    public enum Status {up, down, initializing}

    private volatile Status status;

    @Inject
    public StateMonitor(HealthMonitorConfig config) {
        this.status = Status.valueOf(config.initialStatus());
    }

    public static StateMonitor createForTesting() {
        return new StateMonitor(new HealthMonitorConfig.Builder().build());
    }

    public void status(Status status) {
        if (status != this.status) {
            log.log(Level.INFO, "Changing health status code from '" + this.status + "' to '" + status.name() + "'");
            this.status = status;
        }
    }

    public Status status() { return status; }

}

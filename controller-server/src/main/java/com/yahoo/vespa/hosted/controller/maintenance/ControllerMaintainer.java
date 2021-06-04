// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.concurrent.maintenance.JobMetrics;
import com.yahoo.concurrent.maintenance.Maintainer;
import com.yahoo.config.provision.SystemName;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A maintainer is some job which runs at a fixed interval to perform some maintenance task in the controller.
 *
 * @author bratseth
 */
public abstract class ControllerMaintainer extends Maintainer {

    protected static final Logger log = Logger.getLogger(ControllerMaintainer.class.getName());

    private final Controller controller;

    /** The systems in which this maintainer should run */
    private final Set<SystemName> activeSystems;

    public ControllerMaintainer(Controller controller, Duration interval) {
        this(controller, interval, null, EnumSet.allOf(SystemName.class));
    }

    public ControllerMaintainer(Controller controller, Duration interval, String name, Set<SystemName> activeSystems) {
        super(name, interval, controller.clock().instant(), controller.jobControl(),
              jobMetrics(controller.metric()), controller.curator().cluster(), true);
        this.controller = controller;
        this.activeSystems = Set.copyOf(Objects.requireNonNull(activeSystems));
    }

    protected Controller controller() { return controller; }

    @Override
    public void run() {
        if (!activeSystems.contains(controller.system())) return;
        super.run();
    }

    private static JobMetrics jobMetrics(Metric metric) {
        return new JobMetrics((job, consecutiveFailures) -> {
            metric.set("maintenance.consecutiveFailures", consecutiveFailures, metric.createContext(Map.of("job", job)));
        });
    }

}

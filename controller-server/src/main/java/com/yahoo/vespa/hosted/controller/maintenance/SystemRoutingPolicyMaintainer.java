// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;

import java.time.Duration;

/**
 * This maintains {@link RoutingPolicy}'s for {@link SystemApplication}s. In contrast to regular applications, this
 * refreshes policies at an interval, not on deployment.
 *
 * @author mpolden
 */
public class SystemRoutingPolicyMaintainer extends ControllerMaintainer {

    public SystemRoutingPolicyMaintainer(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        for (var zone : controller().zoneRegistry().zones().reachable().ids()) {
            for (var application : SystemApplication.values()) {
                if (!application.hasEndpoint()) continue;
                controller().routing().policies().refresh(application.id(), DeploymentSpec.empty, zone);
            }
        }
        return 1.0;
    }

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;

import java.time.Duration;

/**
 * @author jonmv
 */
public class DataPlaneTokenRedeployer extends ControllerMaintainer {

    public DataPlaneTokenRedeployer(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        controller().dataplaneTokenService().triggerTokenChangeDeployments();
        return 0;
    }


}

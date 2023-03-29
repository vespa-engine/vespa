package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;

public class CloudDatabaseMaintainer extends ControllerMaintainer {

    public CloudDatabaseMaintainer(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        try {
            var tenants = controller().tenants().asList().stream().map(Tenant::name).toList();
            controller().serviceRegistry().billingController().updateCache(tenants);
        } catch (Exception e) {
            log.warning("Could not update cloud database cache: " + Exceptions.toMessageString(e));
            return 1.0;
        }
        return 0.0;
    }
}

// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestClient;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class ChangeRequestMaintainer extends ControllerMaintainer {

    private final Logger logger = Logger.getLogger(ChangeRequestMaintainer.class.getName());
    private final ChangeRequestClient changeRequestClient;

    public ChangeRequestMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.allOf(Predicate.not(SystemName::isPublic)));
        this.changeRequestClient = controller.serviceRegistry().changeRequestClient();
    }


    @Override
    protected boolean maintain() {
        var changeRequests = changeRequestClient.getUpcomingChangeRequests();

        if (!changeRequests.isEmpty()) {
            logger.fine(() -> "Found the following upcoming change requests:");
            changeRequests.forEach(changeRequest -> logger.fine(changeRequest::toString));
        }

        var unapprovedRequests = changeRequests
                .stream()
                .filter(changeRequest -> !changeRequest.isApproved())
                .collect(Collectors.toList());

        changeRequestClient.approveChangeRequests(unapprovedRequests);

        // TODO: Store in curator?
        return true;
    }
}

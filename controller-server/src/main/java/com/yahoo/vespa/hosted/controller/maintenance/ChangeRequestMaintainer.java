// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestClient;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class ChangeRequestMaintainer extends ControllerMaintainer {

    private final Logger logger = Logger.getLogger(ChangeRequestMaintainer.class.getName());
    private final ChangeRequestClient changeRequestClient;
    private final SystemName system;

    public ChangeRequestMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.allOf(Predicate.not(SystemName::isPublic)));
        this.changeRequestClient = controller.serviceRegistry().changeRequestClient();
        this.system = controller.system();
    }


    @Override
    protected boolean maintain() {
        var changeRequests = changeRequestClient.getUpcomingChangeRequests();

        if (!changeRequests.isEmpty()) {
            logger.info(() -> "Found the following upcoming change requests:");
            changeRequests.forEach(changeRequest -> logger.info(changeRequest::toString));
        }

        if (system.equals(SystemName.main))
            approveChanges(changeRequests);

        // TODO: Store in curator?
        return true;
    }

    private void approveChanges(List<ChangeRequest> changeRequests) {
        var unapprovedRequests = changeRequests
                .stream()
                .filter(changeRequest -> changeRequest.getApproval() == ChangeRequest.Approval.REQUESTED)
                .collect(Collectors.toList());

        changeRequestClient.approveChangeRequests(unapprovedRequests);
    }
}

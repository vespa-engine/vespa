// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.vcmr;

import java.util.ArrayList;
import java.util.List;

/**
 * @author olaa
 */
public class MockChangeRequestClient implements ChangeRequestClient {

    private List<ChangeRequest> upcomingChangeRequests = new ArrayList<>();
    private List<ChangeRequest> approvedChangeRequests = new ArrayList<>();

    @Override
    public List<ChangeRequest> getUpcomingChangeRequests() {
        return upcomingChangeRequests;
    }

    @Override
    public void approveChangeRequests(List<ChangeRequest> changeRequests) {
        approvedChangeRequests.addAll(changeRequests);
    }

    public void setUpcomingChangeRequests(List<ChangeRequest> changeRequests) {
        upcomingChangeRequests = changeRequests;
    }

    public List<ChangeRequest> getApprovedChangeRequests() {
        return approvedChangeRequests;
    }

}

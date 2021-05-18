// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.vcmr;

import java.util.List;

/**
 * @author olaa
 */
public interface ChangeRequestClient {

    /** Get upcoming change requests and updated status of previously stored requests */
    List<ChangeRequest> getChangeRequests(List<ChangeRequest> changeRequests);

    void approveChangeRequest(ChangeRequest changeRequest);

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.mock;

import com.yahoo.vespa.hosted.routing.status.HealthStatus;
import com.yahoo.vespa.hosted.routing.status.ServerGroup;

import java.util.List;

/**
 * @author mpolden
 */
public class HealthStatusMock implements HealthStatus {

    private ServerGroup status = new ServerGroup(List.of());

    public HealthStatusMock setStatus(ServerGroup newStatus) {
        this.status = newStatus;
        return this;
    }

    @Override
    public ServerGroup servers() {
        return status;
    }

}

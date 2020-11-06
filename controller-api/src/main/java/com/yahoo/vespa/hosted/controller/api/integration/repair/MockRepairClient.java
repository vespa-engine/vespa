// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.repair;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author olaa
 */
public class MockRepairClient implements HostRepairClient {

    List<Node> updatedNodes = new ArrayList<>();

    @Override
    public void updateRepairStatus(ZoneApi zone, List<Node> nodes) {
        updatedNodes.addAll(nodes);
    }

    @Override
    public String createTicket(HostName hostname, String colo, ZoneId zoneId, String description, String category) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<Node> getUpdatedNodes() {
        return updatedNodes;
    }
}

// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.repair;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;

import java.util.List;
import java.util.Map;

/**
 * @author olaa
 */
public interface HostRepairClient {

    /* Checks current ticket status and takes appropriate action */
    void updateRepairStatus(ZoneApi zone, Map<Node, RepairTicketReport> nodes);

    /* Creates reparation ticket for given host. Returns ticket number */
    String createTicket(HostName hostname, String colo, ZoneId zoneId, String description, String category);

}

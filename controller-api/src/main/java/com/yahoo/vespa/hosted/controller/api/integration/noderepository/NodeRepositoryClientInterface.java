// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;

import java.io.IOException;
import java.util.Collection;

/**
 * A complete client for the node repository REST API.
 *
 * @author smorgrav
 * @author bjorncs
 */
// TODO: Get rid of all the checked exceptions
// TODO: Replace remaining controller-server usages of this with
// com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository and move this package back to internal
// repo
public interface NodeRepositoryClientInterface {

    enum WantTo {
        Retire,
        Deprovision
    }

    void addNodes(ZoneId zone, Collection<NodeRepositoryNode> nodes) throws IOException;

    NodeRepositoryNode getNode(ZoneId zone, String hostname) throws IOException;

    void deleteNode(ZoneId zone, String hostname) throws IOException;

    NodeList listNodes(ZoneId zone, boolean recursive) throws IOException;

    NodeList listNodes(ZoneId zone, String tenant, String applicationId, String instance) throws IOException;

    String resetFailureInformation(ZoneId zone, String nodename) throws IOException;

    String restart(ZoneId zone, String nodename) throws IOException;

    String reboot(ZoneId zone, String nodename) throws IOException;

    String cancelReboot(ZoneId zone, String nodename) throws IOException;

    String wantTo(ZoneId zone, String nodename, WantTo... actions) throws IOException;

    String cancelRestart(ZoneId zone, String nodename) throws IOException;

    String setHardwareFailureDescription(ZoneId zone, String nodename, String hardwareFailureDescription) throws IOException;

    void setState(ZoneId zone, NodeState nodeState, String nodename) throws IOException;

    String enableMaintenanceJob(ZoneId zone, String jobName) throws IOException;

    String disableMaintenanceJob(ZoneId zone, String jobName) throws IOException;

    MaintenanceJobList listMaintenanceJobs(ZoneId zone) throws IOException;

}

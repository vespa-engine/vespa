// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.entity.NodeEntity;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Ensures that the switch information for all hosts is up to date.
 *
 * @author mpolden
 */
public class HostSwitchUpdater extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(HostSwitchUpdater.class.getName());

    private final NodeRepository nodeRepository;

    public HostSwitchUpdater(Controller controller, Duration interval) {
        super(controller, interval, null, EnumSet.of(SystemName.cd, SystemName.main));
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
    }

    @Override
    protected boolean maintain() {
        Map<String, NodeEntity> nodeEntities = controller().serviceRegistry().entityService().listNodes().stream()
                                                           .collect(Collectors.toMap(NodeEntity::hostname,
                                                                                     Function.identity()));
        int nodesUpdated = 0;
        try {
            for (var zone : controller().zoneRegistry().zones().controllerUpgraded().all().ids()) {
                for (var node : nodeRepository.list(zone)) {
                    NodeEntity nodeEntity = nodeEntities.get(node.hostname().value());
                    if (!shouldUpdate(node, nodeEntity)) continue;

                    NodeRepositoryNode updatedNode = new NodeRepositoryNode();
                    updatedNode.setSwitchHostname(nodeEntity.switchHostname().get());
                    nodeRepository.patchNode(zone, node.hostname().value(), updatedNode);
                    nodesUpdated++;
                }
            }
        } finally {
            if (nodesUpdated > 0) {
                log.info("Updated switch hostname for " + nodesUpdated + " node(s)");
            }
        }
        return true;
    }

    private static boolean shouldUpdate(Node node, NodeEntity nodeEntity) {
        if (nodeEntity == null) return false;
        if (nodeEntity.switchHostname().isEmpty()) return false;
        return !node.switchHostname().equals(nodeEntity.switchHostname());
    }

}

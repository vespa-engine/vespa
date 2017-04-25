// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.collections.ListMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Maintenance job which deactivates nodes which has been retired.
 * This should take place after the system has been given sufficient time to migrate data to other nodes.
 * <p>
 * As these nodes are active, and therefore part of the configuration the impacted applications must be
 * reconfigured after inactivation.
 *
 * @author bratseth
 * @version $Id$
 */
public class RetiredExpirer extends Expirer {

    private final NodeRepository nodeRepository;
    private final Deployer deployer;

    public RetiredExpirer(NodeRepository nodeRepository, Deployer deployer, Clock clock, 
                          Duration retiredDuration, JobControl jobControl) {
        super(Node.State.active, History.Event.Type.retired, nodeRepository, clock, retiredDuration, jobControl);
        this.nodeRepository = nodeRepository;
        this.deployer = deployer;
    }

    @Override
    protected void expire(List<Node> expired) {
        // Only expire nodes which are retired. Do one application at the time.
        ListMap<ApplicationId, Node> applicationNodes = new ListMap<>();
        for (Node node : expired) {
            if (node.allocation().isPresent() && node.allocation().get().membership().retired())
                applicationNodes.put(node.allocation().get().owner(), node);
        }

        for (Map.Entry<ApplicationId, List<Node>> entry : applicationNodes.entrySet()) {
            ApplicationId application = entry.getKey();
            List<Node> nodesToRemove = entry.getValue();
            try {
                Optional<Deployment> deployment = deployer.deployFromLocalActive(application, Duration.ofMinutes(30));
                if ( ! deployment.isPresent()) continue; // this will be done at another config server

                nodeRepository.setRemovable(application, nodesToRemove);

                deployment.get().prepare();
                deployment.get().activate();

                log.info("Redeployed " + application + " to deactivate " + nodesToRemove.size() + " retired nodes");
            }
            catch (RuntimeException e) {
                log.log(Level.WARNING, "Exception trying to remove previously retired nodes " + nodesToRemove +
                        "from " + application, e);
            }
        }
    }

}

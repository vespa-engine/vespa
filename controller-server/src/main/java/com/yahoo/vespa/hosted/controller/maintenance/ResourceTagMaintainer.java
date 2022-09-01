// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.aws.ResourceTagger;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;

import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class ResourceTagMaintainer extends ControllerMaintainer {

    static final ApplicationId SHARED_HOST_APPLICATION = ApplicationId.from("hosted-vespa", "shared-host", "default");
    static final ApplicationId INFRASTRUCTURE_APPLICATION = ApplicationId.from("hosted-vespa", "infrastructure", "default");

    private final ResourceTagger resourceTagger;

    public ResourceTagMaintainer(Controller controller, Duration interval, ResourceTagger resourceTagger) {
        super(controller, interval);
        this.resourceTagger = resourceTagger;
    }

    @Override
    public double maintain() {
        controller().zoneRegistry().zones()
                .reachable()
                .in(CloudName.AWS)
                .zones().forEach(zone -> {
                    Map<HostName, ApplicationId> applicationOfHosts = getTenantOfParentHosts(zone.getId());
                    int taggedResources = resourceTagger.tagResources(zone, applicationOfHosts);
                    if (taggedResources > 0)
                        log.log(Level.INFO, "Tagged " + taggedResources + " resources in " + zone.getId());
        });
        return 1.0;
    }

    private Map<HostName, ApplicationId> getTenantOfParentHosts(ZoneId zoneId) {
        return controller().serviceRegistry().configServer().nodeRepository()
                .list(zoneId, NodeFilter.all())
                .stream()
                .filter(node -> node.type().isHost())
                .collect(Collectors.toMap(
                        Node::hostname,
                        this::getApplicationId,
                        (node1, node2) -> node1
                ));
    }

    private ApplicationId getApplicationId(Node node) {
        if (node.type() == NodeType.host)
            return node.exclusiveTo().orElse(SHARED_HOST_APPLICATION);
        return INFRASTRUCTURE_APPLICATION;
    }
}

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
import org.apache.hc.client5.http.ConnectTimeoutException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.api.integration.aws.ResourceTagger.INFRASTRUCTURE_APPLICATION;

/**
 * @author olaa
 */
public class ResourceTagMaintainer extends ControllerMaintainer {

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
        return 0.0;
    }

    private Map<HostName, ApplicationId> getTenantOfParentHosts(ZoneId zoneId) {
        try {
            return controller().serviceRegistry().configServer().nodeRepository()
                    .list(zoneId, NodeFilter.all())
                    .stream()
                    .filter(node -> node.type().isHost())
                    .collect(Collectors.toMap(
                            Node::hostname,
                            node -> ownerApplicationId(node.type(), node.exclusiveTo(), node.exclusiveToClusterType()),
                            (node1, node2) -> node1
                    ));
        } catch (Exception e) {
            if (e.getCause() instanceof ConnectTimeoutException) {
                // Usually transient - try again later
                log.warning("Unable to retrieve hosts from " + zoneId.value());
                return Map.of();
            }
            throw e;
        }
    }

    // Must be the same as CloudHostProvisioner::ownerApplicationId
    private static ApplicationId ownerApplicationId(NodeType hostType, Optional<ApplicationId> exclusiveTo, Optional<Node.ClusterType> exclusiveToClusterType) {
        if (hostType != NodeType.host) return INFRASTRUCTURE_APPLICATION;
        return exclusiveTo.orElseGet(() ->
                ApplicationId.from("hosted-vespa", "shared-host", exclusiveToClusterType.map(Node.ClusterType::name).orElse("default")));
    }
}

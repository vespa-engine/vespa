// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.aws.ResourceTagger;

import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class ResourceTagMaintainer extends Maintainer {

    private final ResourceTagger resourceTagger;

    public ResourceTagMaintainer(Controller controller, Duration interval, JobControl jobControl, ResourceTagger resourceTagger) {
        super(controller, interval, jobControl);
        this.resourceTagger = resourceTagger;
    }

    @Override
    public void maintain() {
        controller().zoneRegistry().zones()
                .ofCloud(CloudName.from("aws"))
                .reachable()
                .zones().forEach(zone -> {
                    Map<HostName, ApplicationId> applicationOfHosts = getTenantOfParentHosts(zone.getId());
                    int taggedResources = resourceTagger.tagResources(zone, applicationOfHosts);
                    if (taggedResources > 0)
                        log.log(Level.INFO, "Tagged " + taggedResources + " resources in " + zone.getId());
        });


    }

    private Map<HostName, ApplicationId> getTenantOfParentHosts(ZoneId zoneId) {
        return controller().serviceRegistry().configServer().nodeRepository()
                .list(zoneId)
                .stream()
                .filter(node -> node.parentHostname().isPresent() && node.owner().isPresent())
                .collect(Collectors.toMap(
                        node -> node.parentHostname().get(),
                        node -> node.owner().get(),
                        (node1, node2) -> node1
                ));
    }
}

// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveService;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Updates archive URIs for tenants in all zones.
 *
 * @author freva
 */
public class ArchiveUriUpdater extends ControllerMaintainer {

    private static final Set<TenantName> INFRASTRUCTURE_TENANTS = Set.of(TenantName.from("hosted-vespa"));

    private final ApplicationController applications;
    private final NodeRepository nodeRepository;
    private final ArchiveService archiveService;

    public ArchiveUriUpdater(Controller controller, Duration duration) {
        super(controller, duration, ArchiveUriUpdater.class.getSimpleName(), SystemName.all());
        this.applications = controller.applications();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.archiveService = controller.serviceRegistry().archiveService();
    }

    @Override
    protected boolean maintain() {
        Map<ZoneId, Set<TenantName>> tenantsByZone = new HashMap<>();
        for (var application : applications.asList()) {
            for (var instance : application.instances().values()) {
                for (var deployment : instance.deployments().values()) {
                    tenantsByZone
                            .computeIfAbsent(deployment.zone(), zone -> new HashSet<>(INFRASTRUCTURE_TENANTS))
                            .add(instance.id().tenant());
                }
            }
        }

        tenantsByZone.forEach((zone, tenants) -> {
            Map<TenantName, URI> zoneArchiveUris = nodeRepository.getArchiveUris(zone);
            for (TenantName tenant : tenants) {
                archiveService.archiveUriFor(zone, tenant)
                        .filter(uri -> !uri.equals(zoneArchiveUris.get(tenant)))
                        .ifPresent(uri -> nodeRepository.setArchiveUri(zone, tenant, uri));
            }

            zoneArchiveUris.keySet().stream()
                    .filter(tenant -> ! tenants.contains(tenant))
                    .forEach(tenant -> nodeRepository.removeArchiveUri(zone, tenant));
        });

        return true;
    }

}

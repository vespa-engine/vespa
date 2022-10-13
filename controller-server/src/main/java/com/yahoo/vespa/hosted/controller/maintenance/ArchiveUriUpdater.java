// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.archive.CuratorArchiveBucketDb;
import com.yahoo.yolean.Exceptions;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Updates archive URIs for tenants in all zones.
 *
 * @author freva
 */
public class ArchiveUriUpdater extends ControllerMaintainer {

    private static final Set<TenantName> INFRASTRUCTURE_TENANTS = Set.of(SystemApplication.TENANT);

    private final ApplicationController applications;
    private final NodeRepository nodeRepository;
    private final CuratorArchiveBucketDb archiveBucketDb;

    public ArchiveUriUpdater(Controller controller, Duration interval) {
        super(controller, interval);
        this.applications = controller.applications();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.archiveBucketDb = controller.archiveBucketDb();
    }

    @Override
    protected double maintain() {
        Map<ZoneId, Set<TenantName>> tenantsByZone = new HashMap<>();

        controller().zoneRegistry().zonesIncludingSystem().reachable().zones().forEach(
                z -> tenantsByZone.put(z.getVirtualId(), new HashSet<>(INFRASTRUCTURE_TENANTS)));

        for (var application : applications.asList()) {
            for (var instance : application.instances().values()) {
                for (var deployment : instance.deployments().values()) {
                    tenantsByZone.get(deployment.zone()).add(instance.id().tenant());
                }
            }
        }

        int failures = 0;
        for (ZoneId zone : tenantsByZone.keySet()) {
            try {
                Map<TenantName, URI> zoneArchiveUris = nodeRepository.getArchiveUris(zone);

                for (TenantName tenant : tenantsByZone.get(zone)) {
                    archiveBucketDb.archiveUriFor(zone, tenant, true)
                            .filter(uri -> !uri.equals(zoneArchiveUris.get(tenant)))
                            .ifPresent(uri -> nodeRepository.setArchiveUri(zone, tenant, uri));
                }

                zoneArchiveUris.keySet().stream()
                        .filter(tenant -> !tenantsByZone.get(zone).contains(tenant))
                        .forEach(tenant -> nodeRepository.removeArchiveUri(zone, tenant));
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed to update archive URI in " + zone + ". Retrying in " + interval() + ". Error: " +
                        Exceptions.toMessageString(e));
                failures++;
            }
        }

        return asSuccessFactor(tenantsByZone.size(), failures);
    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.Host;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostInfo;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * (Only the suspended applications part of this is in use)
 *
 * @author bratseth
 */
public class OrchestratorMock implements Orchestrator {

    private final Map<HostName, HostInfo> hostInfos = new HashMap<>();
    private final Set<ApplicationId> suspendedApplications = new HashSet<>();

    @Override
    public Host getHost(HostName hostName) {
        return null;
    }

    @Override
    public HostStatus getNodeStatus(HostName hostName) {
        HostInfo hostInfo = hostInfos.get(hostName);
        return hostInfo == null ? HostStatus.NO_REMARKS : hostInfo.status();
    }

    @Override
    public HostInfo getHostInfo(ApplicationInstanceReference reference, HostName hostname) {
        HostInfo hostInfo = hostInfos.get(hostname);
        return hostInfo == null ? HostInfo.createNoRemarks() : hostInfo;
    }

    @Override
    public Function<HostName, Optional<HostInfo>> getHostResolver() {
        return hostName -> Optional.of(hostInfos.getOrDefault(hostName, HostInfo.createNoRemarks()));
    }

    @Override
    public void setNodeStatus(HostName hostName, HostStatus state) {}

    @Override
    public void resume(HostName hostName) {
        hostInfos.remove(hostName);
    }

    @Override
    public void suspend(HostName hostName) {
        hostInfos.put(hostName, HostInfo.createSuspended(HostStatus.ALLOWED_TO_BE_DOWN, Instant.EPOCH));
    }

    @Override
    public ApplicationInstanceStatus getApplicationInstanceStatus(ApplicationId appId) {
        return suspendedApplications.contains(appId)
               ? ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN : ApplicationInstanceStatus.NO_REMARKS;
    }

    @Override
    public Set<ApplicationId> getAllSuspendedApplications() {
        return Collections.unmodifiableSet(suspendedApplications);
    }

    @Override
    public void resume(ApplicationId appId) {
        suspendedApplications.remove(appId);
    }

    @Override
    public void suspend(ApplicationId appId) {
        suspendedApplications.add(appId);
    }

    @Override
    public void acquirePermissionToRemove(HostName hostName) {}

    @Override
    public void suspendAll(HostName parentHostname, List<HostName> hostNames) {
        hostNames.forEach(this::suspend);
    }

}

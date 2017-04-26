package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.ApplicationIdNotFoundException;
import com.yahoo.vespa.orchestrator.ApplicationStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.BatchHostNameNotFoundException;
import com.yahoo.vespa.orchestrator.BatchInternalErrorException;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.policy.BatchHostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author bratseth
 */
public class OrchestratorMock implements Orchestrator {

    Set<ApplicationId> suspendedApplications = new HashSet<>();

    @Override
    public HostStatus getNodeStatus(HostName hostName) throws HostNameNotFoundException {
        return null;
    }

    @Override
    public void resume(HostName hostName) throws HostStateChangeDeniedException, HostNameNotFoundException {}

    @Override
    public void suspend(HostName hostName) throws HostStateChangeDeniedException, HostNameNotFoundException {}

    @Override
    public ApplicationInstanceStatus getApplicationInstanceStatus(ApplicationId appId) throws ApplicationIdNotFoundException {
        return suspendedApplications.contains(appId)
               ? ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN : ApplicationInstanceStatus.NO_REMARKS;
    }

    @Override
    public Set<ApplicationId> getAllSuspendedApplications() {
        return Collections.unmodifiableSet(suspendedApplications);
    }

    @Override
    public void resume(ApplicationId appId) throws ApplicationStateChangeDeniedException, ApplicationIdNotFoundException {
        suspendedApplications.remove(appId);
    }

    @Override
    public void suspend(ApplicationId appId) throws ApplicationStateChangeDeniedException, ApplicationIdNotFoundException {
        suspendedApplications.add(appId);
    }

    @Override
    public void suspendAll(HostName parentHostname, List<HostName> hostNames) throws BatchInternalErrorException, BatchHostStateChangeDeniedException, BatchHostNameNotFoundException {
        throw new UnsupportedOperationException("Not implemented");
    }

}

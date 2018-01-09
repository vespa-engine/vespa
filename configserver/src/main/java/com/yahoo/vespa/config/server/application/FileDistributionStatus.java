// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.http.JSONResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * File distribution status for each host in the application
 *
 * @author hmusum
 */
public class FileDistributionStatus extends AbstractComponent {

    enum Status {UNKNOWN, FINISHED, IN_PROGRESS}

    private final Supervisor supervisor = new Supervisor(new Transport());

    public StatusAllHosts status(Application application, Duration timeout) {
        List<HostStatus> hostStatuses = new ArrayList<>();
        application.getModel().getHosts()
                .forEach(host -> host.getServices()
                        .stream()
                        .filter(service -> "configproxy".equals(service.getServiceType()))
                        .forEach(service -> hostStatuses.add(getHostStatus(service.getHostName(), getRpcPort(service), timeout))));
        return createStatusForAllHosts(hostStatuses);
    }

    HostStatus getHostStatus(String hostname, int port, Duration timeout) {
        Target target = supervisor.connect(new Spec(hostname, port));
        Request request = new Request("filedistribution.getActiveFileReferencesStatus");
        target.invokeSync(request, timeout.toMillis() / 1000);
        HostStatus hostStatus = createHostStatusFromResponse(hostname, request);
        target.close();
        return hostStatus;
    }

    private HostStatus createHostStatusFromResponse(String hostname, Request request) {
        if (request.isError()) {
            return new HostStatus(hostname,
                                  Status.UNKNOWN,
                                  Collections.emptyMap(),
                                  "error: " + request.errorMessage() + "(" + request.errorCode() + ")");
        } else {
            Map<String, Double> fileReferenceStatuses = new HashMap<>();
            String[] fileReferences = request.returnValues().get(0).asStringArray();
            double[] downloadStatus = request.returnValues().get(1).asDoubleArray();

            boolean allDownloaded = true;
            for (int i = 0; i < fileReferences.length; i++) {
                fileReferenceStatuses.put(fileReferences[i], downloadStatus[i]);
                if (downloadStatus[i] < 1.0) {
                    allDownloaded = false;
                }
            }

            return new HostStatus(hostname, allDownloaded ? Status.FINISHED : Status.IN_PROGRESS, fileReferenceStatuses, "");
        }
    }

    private StatusAllHosts createStatusForAllHosts(List<HostStatus> hostStatuses) {
        boolean allFinished = true;
        for (HostStatus hostStatus : hostStatuses) {
            if (hostStatus.status != Status.FINISHED) {
                allFinished = false;
                break;
            }
        }
        return new StatusAllHosts(allFinished ? Status.FINISHED : Status.IN_PROGRESS, hostStatuses);
    }

    private static Integer getRpcPort(ServiceInfo service) {
        return service.getPorts().stream()
                .filter(port -> port.getTags().contains("rpc"))
                .map(PortInfo::getPort)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find rpc port for config proxy for " + service.getHostName()));
    }

    static class StatusAllHosts extends JSONResponse {

        private StatusAllHosts(Status status, List<HostStatus> hostStatuses) {
            super(200);
            Cursor hostsArray = object.setArray("hosts");
            for (HostStatus hostStatus : hostStatuses) {
                Cursor host = hostsArray.addObject();
                host.setString("hostname", hostStatus.hostname);
                host.setString("status", hostStatus.status.name());
                hostStatus.errorMessage.ifPresent(message -> host.setString("message", message));
                Cursor fileReferences = host.setArray("fileReferences");
                hostStatus.fileReferenceStatuses.forEach((key, value) -> fileReferences.addObject().setDouble(key, value));
            }

            object.setString("status", status.name());
        }
    }

    static class HostStatus {

        private final String hostname;
        private final Status status;
        private final Map<String, Double> fileReferenceStatuses;
        private final Optional<String> errorMessage;

        HostStatus(String hostname, Status status, Map<String, Double> fileReferenceStatuses) {
            this.hostname = hostname;
            this.status = status;
            this.fileReferenceStatuses = fileReferenceStatuses;
            this.errorMessage = Optional.empty();
        }

        HostStatus(String hostname, Status status, Map<String, Double> fileReferenceStatuses, String errorMessage) {
            this.hostname = hostname;
            this.status = status;
            this.fileReferenceStatuses = fileReferenceStatuses;
            this.errorMessage = Optional.of(errorMessage);
        }

        public String hostname() {
            return hostname;
        }

        @Override
        public String toString() {
            return hostname + ": " + status + ", " + fileReferenceStatuses + " " + errorMessage.orElse("");
        }
    }

}

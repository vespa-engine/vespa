// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.http.JSONResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * File distribution status for each host in the application
 *
 * @author hmusum
 */
public class FileDistributionStatus extends AbstractComponent {

    private static final Logger log = Logger.getLogger(FileDistributionStatus.class.getName());

    enum Status {UNKNOWN, FINISHED, IN_PROGRESS}

    private final ExecutorService rpcExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory("filedistribution status"));
    private final Supervisor supervisor = new Supervisor(new Transport());

    public StatusAllHosts status(Application application, Duration timeout) {
        List<HostStatus> hostStatuses = new ArrayList<>();
        List <Future<HostStatus>> results = new ArrayList<>();
        application.getModel().getHosts()
                .forEach(host -> host.getServices()
                        .stream()
                        .filter(service -> "configproxy".equals(service.getServiceType()))
                        .forEach(service -> {
                            results.add(rpcExecutor.submit(() -> getHostStatus(service.getHostName(), getRpcPort(service), timeout)));
                        }));
        // wait for all
        results.forEach(future -> {
            try {
                hostStatuses.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                log.log(LogLevel.WARNING, "Failed getting file distribution status", e);
            }
        });
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
        int countUnknown = 0;
        int countInProgress = 0;
        int countFinished = 0;
        for (HostStatus hostStatus : hostStatuses) {
            switch (hostStatus.status) {
                case IN_PROGRESS:
                    countInProgress++;
                    break;
                case FINISHED:
                    countFinished++;
                    break;
                case UNKNOWN:
                    countUnknown++;
                    break;
                default:
            }
        }

        if (countInProgress == 0 && countUnknown == 0)
            return new StatusAllHosts(Status.FINISHED, hostStatuses);
        else if (countInProgress == 0 && countFinished == 0)
            return new StatusAllHosts(Status.UNKNOWN, hostStatuses);
        else
            return new StatusAllHosts(Status.IN_PROGRESS, hostStatuses);
    }

    private static Integer getRpcPort(ServiceInfo service) {
        return service.getPorts().stream()
                .filter(port -> port.getTags().contains("rpc"))
                .map(PortInfo::getPort)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find rpc port for " + service.getServiceType() + " on " + service.getHostName()));
    }

    private static class StatusAllHosts extends JSONResponse {

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

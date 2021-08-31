// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncClient;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Generates dumps for Vespa services and uploads resulting files to S3.
 *
 * @author bjorncs
 */
public class VespaServiceDumperImpl implements VespaServiceDumper {

    private static final Logger log = Logger.getLogger(VespaServiceDumperImpl.class.getName());
    private static final String DIRECTORY_IN_NODE = "/opt/vespa/tmp/vespa-service-dump";

    private final ContainerOperations container;
    private final SyncClient syncClient;
    private final NodeRepository nodeRepository;

    public VespaServiceDumperImpl(ContainerOperations container, SyncClient syncClient, NodeRepository nodeRepository) {
        this.container = container;
        this.syncClient = syncClient;
        this.nodeRepository = nodeRepository;
    }

    @Override
    public void processServiceDumpRequest(NodeAgentContext context) {
        Instant startedAt = Instant.now();
        NodeSpec nodeSpec = context.node();
        ServiceDumpReport request = nodeSpec.reports().getReport(ServiceDumpReport.REPORT_ID, ServiceDumpReport.class)
                .orElse(null);
        if (request == null || !isNullTimestamp(request.failedAt()) || !isNullTimestamp(request.completedAt())) {
            context.log(log, Level.FINE, "No service dump requested or dump already completed/failed");
            return;
        }
        if (isNullTimestamp(request.getCreatedMillisOrNull())) {
            handleFailure(context, request, startedAt, null, "'createdMillis' is missing or null");
            return;
        }
        String configId = request.configId();
        if (configId == null) {
            handleFailure(context, request, startedAt, null, "Service config id is missing from request");
            return;
        }
        Instant expiry = expireAt(startedAt, request);
        if (expiry.isBefore(startedAt)) {
            handleFailure(context, request, startedAt, null, "Request already expired");
            return;
        }
        try {
            context.log(log, Level.FINE,
                    "Creating dump for " + configId + " requested at " + Instant.ofEpochMilli(request.getCreatedMillisOrNull()));
            storeReport(context, createStartedReport(request, startedAt));
            Path directoryOnHost = context.pathOnHostFromPathInNode(DIRECTORY_IN_NODE);
            Files.deleteIfExists(directoryOnHost);
            Files.createDirectory(directoryOnHost);
            CommandResult result = container.executeCommandInContainerAsRoot(
                    context, "/opt/vespa/bin/vespa-jvm-dumper", configId, DIRECTORY_IN_NODE);
            context.log(log, Level.FINE, "vespa-jvm-dumper exit code: " + result.getExitCode());
            context.log(log, Level.FINE, "vespa-jvm-dumper output: " + result.getOutput());
            if (result.getExitCode() > 0) {
                handleFailure(context, request, startedAt, null, "Failed to create dump: " + result.getOutput());
                return;
            }
            URI destination = serviceDumpDestination(nodeSpec, createDumpId(request));
            context.log(log, Level.FINE, "Uploading files with destination " + destination + " and expiry " + expiry);
            List<SyncFileInfo> files = dumpFiles(directoryOnHost, destination, expiry);
            logFilesToUpload(context, files);
            if (!syncClient.sync(context, files, Integer.MAX_VALUE)) {
                handleFailure(context, request, startedAt, null, "Unable to upload all files");
                return;
            }
            context.log(log, Level.FINE, "Upload complete");
            storeReport(context, createSuccessReport(request, startedAt, destination));
        } catch (Exception e) {
            handleFailure(context, request, startedAt, e, e.getMessage());
        }
    }

    private List<SyncFileInfo> dumpFiles(Path directoryOnHost, URI destination, Instant expiry) {
        return FileFinder.files(directoryOnHost).stream()
                .flatMap(file -> SyncFileInfo.forServiceDump(destination, file.path(), expiry).stream())
                .collect(Collectors.toList());
    }

    private void logFilesToUpload(NodeAgentContext context, List<SyncFileInfo> files) {
        if (log.isLoggable(Level.FINE)) {
            String message = files.stream()
                    .map(file -> file.source().toString())
                    .collect(Collectors.joining());
            context.log(log, Level.FINE, message);
        }
    }

    private static Instant expireAt(Instant startedAt, ServiceDumpReport request) {
        return isNullTimestamp(request.expireAt())
                ? startedAt.plus(7, ChronoUnit.DAYS)
                : Instant.ofEpochMilli(request.expireAt());
    }

    private void handleFailure(NodeAgentContext context, ServiceDumpReport request, Instant startedAt,
                               Exception failure, String message) {
        if (failure != null) {
            context.log(log, Level.WARNING, message, failure);
        } else {
            context.log(log, Level.WARNING, message);
        }
        ServiceDumpReport report = createErrorReport(request, startedAt, message);
        storeReport(context, report);
    }

    private void storeReport(NodeAgentContext context, ServiceDumpReport report) {
        NodeAttributes nodeAttributes = new NodeAttributes();
        nodeAttributes.withReport(ServiceDumpReport.REPORT_ID, report.toJsonNode());
        nodeRepository.updateNodeAttributes(context.hostname().value(), nodeAttributes);
    }

    private static ServiceDumpReport createStartedReport(ServiceDumpReport request, Instant startedAt) {
        return new ServiceDumpReport(
                request.getCreatedMillisOrNull(), startedAt.toEpochMilli(), null, null, null, request.configId(),
                request.expireAt(), null);
    }

    private static ServiceDumpReport createSuccessReport(ServiceDumpReport request, Instant startedAt, URI location) {
        return new ServiceDumpReport(
                request.getCreatedMillisOrNull(), startedAt.toEpochMilli(), Instant.now().toEpochMilli(), null,
                location.toString(), request.configId(), request.expireAt(), null);
    }

    private static ServiceDumpReport createErrorReport(ServiceDumpReport request, Instant startedAt, String message) {
        return new ServiceDumpReport(
                request.getCreatedMillisOrNull(), startedAt.toEpochMilli(), null, Instant.now().toEpochMilli(), null,
                request.configId(), request.expireAt(), message);
    }

    private static String createDumpId(ServiceDumpReport report) {
        String sanitizedConfigId = report.configId()
                .replace('/', '-')
                .replace('@', '-');
        return sanitizedConfigId + "-" + report.getCreatedMillisOrNull().toString();
    }

    private static URI serviceDumpDestination(NodeSpec spec, String dumpId) {
        URI archiveUri = spec.archiveUri().get();
        String targetDirectory = "service-dump/" + dumpId;
        return archiveUri.resolve(targetDirectory);
    }

    private static boolean isNullTimestamp(Long timestamp) {
        return timestamp == null || timestamp == 0;
    }

}

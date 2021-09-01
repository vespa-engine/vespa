// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.text.Lowercase;
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
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.ServiceDumpReport.isNullTimestamp;

/**
 * Generates dumps for Vespa services and uploads resulting files to S3.
 *
 * @author bjorncs
 */
public class VespaServiceDumperImpl implements VespaServiceDumper {

    private static final Logger log = Logger.getLogger(VespaServiceDumperImpl.class.getName());

    private final ContainerOperations container;
    private final SyncClient syncClient;
    private final NodeRepository nodeRepository;
    private final Clock clock;

    public VespaServiceDumperImpl(ContainerOperations container, SyncClient syncClient, NodeRepository nodeRepository) {
        this(container, syncClient, nodeRepository, Clock.systemUTC());
    }

    // For unit testing
    VespaServiceDumperImpl(ContainerOperations container, SyncClient syncClient, NodeRepository nodeRepository,
                           Clock clock) {
        this.container = container;
        this.syncClient = syncClient;
        this.nodeRepository = nodeRepository;
        this.clock = clock;
    }

    @Override
    public void processServiceDumpRequest(NodeAgentContext context) {
        Instant startedAt = clock.instant();
        NodeSpec nodeSpec = context.node();
        ServiceDumpReport request = nodeSpec.reports().getReport(ServiceDumpReport.REPORT_ID, ServiceDumpReport.class)
                .orElse(null);
        if (request == null || request.isCompletedOrFailed()) {
            context.log(log, Level.FINE, "No service dump requested or dump already completed/failed");
            return;
        }
        if (isNullTimestamp(request.getCreatedMillisOrNull())) {
            handleFailure(context, request, startedAt, "'createdMillis' is missing or null");
            return;
        }
        String configId = request.configId();
        if (configId == null) {
            handleFailure(context, request, startedAt, "Service config id is missing from request");
            return;
        }
        Instant expiry = expireAt(startedAt, request);
        if (expiry.isBefore(startedAt)) {
            handleFailure(context, request, startedAt, "Request already expired");
            return;
        }
        try {
            context.log(log, Level.INFO,
                    "Creating dump for " + configId + " requested at " + Instant.ofEpochMilli(request.getCreatedMillisOrNull()));
            storeReport(context, createStartedReport(request, startedAt));
            Path directoryInNode = context.pathInNodeUnderVespaHome("tmp/vespa-service-dump");
            Path directoryOnHost = context.pathOnHostFromPathInNode(directoryInNode);
            context.log(log, Level.INFO, "Clearing directory on host: " + directoryOnHost);
            Files.deleteIfExists(directoryOnHost);
            Files.createDirectory(directoryOnHost);
            Path vespaJvmDumper = context.pathInNodeUnderVespaHome("bin/vespa-jvm-dumper");
            context.log(log, Level.INFO, "Executing '" + vespaJvmDumper + "' with arguments '" + configId + "' and '" + directoryInNode + "'");
            CommandResult result = container.executeCommandInContainerAsRoot(
                    context, vespaJvmDumper.toString(), configId, directoryInNode.toString());
            context.log(log, Level.FINE, "vespa-jvm-dumper exit code: " + result.getExitCode() + ", output: " + result.getOutput());
            if (result.getExitCode() > 0) {
                handleFailure(context, request, startedAt, "Failed to create dump: " + result.getOutput());
                return;
            }
            URI destination = serviceDumpDestination(nodeSpec, createDumpId(request));
            context.log(log, Level.INFO, "Uploading files with destination " + destination + " and expiry " + expiry);
            List<SyncFileInfo> files = dumpFiles(directoryOnHost, destination, expiry);
            if (!syncClient.sync(context, files, Integer.MAX_VALUE)) {
                handleFailure(context, request, startedAt, "Unable to upload all files");
                return;
            }
            context.log(log, Level.INFO, "Upload complete");
            storeReport(context, createSuccessReport(clock, request, startedAt, destination));
        } catch (Exception e) {
            handleFailure(context, request, startedAt, e);
        }
    }

    private List<SyncFileInfo> dumpFiles(Path directoryOnHost, URI destination, Instant expiry) {
        return FileFinder.files(directoryOnHost).stream()
                .flatMap(file -> SyncFileInfo.forServiceDump(destination, file.path(), expiry).stream())
                .collect(Collectors.toList());
    }

    private static Instant expireAt(Instant startedAt, ServiceDumpReport request) {
        return isNullTimestamp(request.expireAt())
                ? startedAt.plus(7, ChronoUnit.DAYS)
                : Instant.ofEpochMilli(request.expireAt());
    }

    private void handleFailure(NodeAgentContext context, ServiceDumpReport request, Instant startedAt, Exception failure) {
        context.log(log, Level.WARNING, failure.toString(), failure);
        ServiceDumpReport report = createErrorReport(clock, request, startedAt, failure.toString());
        storeReport(context, report);
    }

    private void handleFailure(NodeAgentContext context, ServiceDumpReport request, Instant startedAt, String message) {
        context.log(log, Level.WARNING, message);
        ServiceDumpReport report = createErrorReport(clock, request, startedAt, message);
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

    private static ServiceDumpReport createSuccessReport(
            Clock clock, ServiceDumpReport request, Instant startedAt, URI location) {
        return new ServiceDumpReport(
                request.getCreatedMillisOrNull(), startedAt.toEpochMilli(), clock.instant().toEpochMilli(), null,
                location.toString(), request.configId(), request.expireAt(), null);
    }

    private static ServiceDumpReport createErrorReport(
            Clock clock, ServiceDumpReport request, Instant startedAt, String message) {
        return new ServiceDumpReport(
                request.getCreatedMillisOrNull(), startedAt.toEpochMilli(), null, clock.instant().toEpochMilli(), null,
                request.configId(), request.expireAt(), message);
    }

    static String createDumpId(ServiceDumpReport request) {
        String sanitizedConfigId = Lowercase.toLowerCase(request.configId()).replaceAll("[^a-z_0-9]", "-");
        return sanitizedConfigId + "-" + request.getCreatedMillisOrNull().toString();
    }

    private static URI serviceDumpDestination(NodeSpec spec, String dumpId) {
        URI archiveUri = spec.archiveUri().get();
        String targetDirectory = "service-dump/" + dumpId;
        return archiveUri.resolve(targetDirectory);
    }


}

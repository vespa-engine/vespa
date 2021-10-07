// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.yolean.concurrent.Sleeper;
import com.yahoo.text.Lowercase;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncClient;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
    private final Map<String, ArtifactProducer> artifactProducers;

    public VespaServiceDumperImpl(ContainerOperations container, SyncClient syncClient, NodeRepository nodeRepository) {
        this(container, syncClient, nodeRepository, Clock.systemUTC(), Sleeper.DEFAULT);
    }

    // For unit testing
    VespaServiceDumperImpl(ContainerOperations container, SyncClient syncClient, NodeRepository nodeRepository,
                           Clock clock, Sleeper sleeper) {
        this.container = container;
        this.syncClient = syncClient;
        this.nodeRepository = nodeRepository;
        this.clock = clock;
        List<AbstractProducer> producers = List.of(
                new JvmDumpProducer(container),
                new PerfReportProducer(container),
                new JavaFlightRecorder(container, sleeper));
        this.artifactProducers = producers.stream()
                .collect(Collectors.toMap(ArtifactProducer::name, Function.identity()));
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
        List<String> artifactTypes = request.artifacts();
        if (artifactTypes == null || artifactTypes.isEmpty()) {
            handleFailure(context, request, startedAt, "No artifacts requested");
            return;
        }
        UnixPath directoryInNode = new UnixPath(context.pathInNodeUnderVespaHome("tmp/vespa-service-dump"));
        UnixPath directoryOnHost = new UnixPath(context.pathOnHostFromPathInNode(directoryInNode.toPath()));
        try {
            context.log(log, Level.INFO,
                    "Creating service dump for " + configId + " requested at "
                            + Instant.ofEpochMilli(request.getCreatedMillisOrNull()));
            storeReport(context, ServiceDumpReport.createStartedReport(request, startedAt));
            if (directoryOnHost.exists()) {
                context.log(log, Level.INFO, "Removing existing directory '" + directoryOnHost +"'.");
                directoryOnHost.deleteRecursively();
            }
            context.log(log, Level.INFO, "Creating '" + directoryOnHost +"'.");
            directoryOnHost.createDirectory();
            directoryOnHost.setPermissions("rwxrwxrwx");
            List<SyncFileInfo> files = new ArrayList<>();
            URI destination = serviceDumpDestination(nodeSpec, createDumpId(request));
            for (String artifactType : artifactTypes) {
                ArtifactProducer producer = artifactProducers.get(artifactType);
                if (producer == null) {
                    String supportedValues = String.join(",", artifactProducers.keySet());
                    handleFailure(context, request, startedAt, "No artifact producer exists for '" + artifactType + "'. " +
                            "Following values are allowed: " + supportedValues);
                    return;
                }
                context.log(log, "Producing artifact of type '" + artifactType + "'");
                UnixPath producerDirectoryOnHost = directoryOnHost.resolve(artifactType);
                producerDirectoryOnHost.createDirectory();
                producerDirectoryOnHost.setPermissions("rwxrwxrwx");
                UnixPath producerDirectoryInNode = directoryInNode.resolve(artifactType);
                producer.produceArtifact(context, configId, request.dumpOptions(), producerDirectoryInNode);
                collectArtifactFilesToUpload(files, producerDirectoryOnHost, destination.resolve(artifactType + '/'), expiry);
            }
            context.log(log, Level.INFO, "Uploading files with destination " + destination + " and expiry " + expiry);
            if (!syncClient.sync(context, files, Integer.MAX_VALUE)) {
                handleFailure(context, request, startedAt, "Unable to upload all files");
                return;
            }
            context.log(log, Level.INFO, "Upload complete");
            storeReport(context, ServiceDumpReport.createSuccessReport(request, startedAt, clock.instant(), destination));
        } catch (Exception e) {
            handleFailure(context, request, startedAt, e);
        } finally {
            if (directoryOnHost.exists()) {
                context.log(log, Level.INFO, "Deleting directory '" + directoryOnHost +"'.");
                directoryOnHost.deleteRecursively();
            }
        }
    }

    private void collectArtifactFilesToUpload(List<SyncFileInfo> files, UnixPath directoryOnHost, URI destination, Instant expiry) {
        FileFinder.files(directoryOnHost.toPath()).stream()
                .flatMap(file -> SyncFileInfo.forServiceDump(destination, file.path(), expiry).stream())
                .forEach(files::add);
    }

    private static Instant expireAt(Instant startedAt, ServiceDumpReport request) {
        return isNullTimestamp(request.expireAt())
                ? startedAt.plus(7, ChronoUnit.DAYS)
                : Instant.ofEpochMilli(request.expireAt());
    }

    private void handleFailure(NodeAgentContext context, ServiceDumpReport request, Instant startedAt, Exception failure) {
        context.log(log, Level.WARNING, failure.toString(), failure);
        ServiceDumpReport report = ServiceDumpReport.createErrorReport(request, startedAt, clock.instant(), failure.toString());
        storeReport(context, report);
    }

    private void handleFailure(NodeAgentContext context, ServiceDumpReport request, Instant startedAt, String message) {
        context.log(log, Level.WARNING, message);
        ServiceDumpReport report = ServiceDumpReport.createErrorReport(request, startedAt, clock.instant(), message);
        storeReport(context, report);
    }

    private void storeReport(NodeAgentContext context, ServiceDumpReport report) {
        NodeAttributes nodeAttributes = new NodeAttributes();
        nodeAttributes.withReport(ServiceDumpReport.REPORT_ID, report.toJsonNode());
        nodeRepository.updateNodeAttributes(context.hostname().value(), nodeAttributes);
    }

    static String createDumpId(ServiceDumpReport request) {
        String sanitizedConfigId = Lowercase.toLowerCase(request.configId()).replaceAll("[^a-z_0-9]", "-");
        return sanitizedConfigId + "-" + request.getCreatedMillisOrNull().toString();
    }

    private static URI serviceDumpDestination(NodeSpec spec, String dumpId) {
        URI archiveUri = spec.archiveUri()
                .orElseThrow(() -> new IllegalStateException("Archive URI is missing for " + spec.hostname()));
        String targetDirectory = "service-dump/" + dumpId + "/";
        return archiveUri.resolve(targetDirectory);
    }


}

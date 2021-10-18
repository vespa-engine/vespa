// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.text.Lowercase;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncClient;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo.Compression;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;
import com.yahoo.yolean.concurrent.Sleeper;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
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
    private final ArtifactProducers artifactProducers;

    public VespaServiceDumperImpl(ContainerOperations container, SyncClient syncClient, NodeRepository nodeRepository) {
        this(ArtifactProducers.createDefault(Sleeper.DEFAULT), container, syncClient, nodeRepository, Clock.systemUTC());
    }

    // For unit testing
    VespaServiceDumperImpl(ArtifactProducers producers, ContainerOperations container, SyncClient syncClient,
                           NodeRepository nodeRepository, Clock clock) {
        this.container = container;
        this.syncClient = syncClient;
        this.nodeRepository = nodeRepository;
        this.clock = clock;
        this.artifactProducers = producers;
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
        List<String> requestedArtifacts = request.artifacts();
        if (requestedArtifacts == null || requestedArtifacts.isEmpty()) {
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
            directoryOnHost.createDirectory("rwxrwxrwx");
            URI destination = serviceDumpDestination(nodeSpec, createDumpId(request));
            ProducerContext producerCtx = new ProducerContext(context, directoryInNode.toPath(), request);
            List<Artifact> producedArtifacts = new ArrayList<>();
            for (ArtifactProducer producer : artifactProducers.resolve(requestedArtifacts)) {
                context.log(log, "Producing artifact of type '" + producer.artifactName() + "'");
                producedArtifacts.addAll(producer.produceArtifacts(producerCtx));
            }
            uploadArtifacts(context, destination, producedArtifacts, expiry);
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

    private void uploadArtifacts(NodeAgentContext ctx, URI destination,
                                 List<Artifact> producedArtifacts, Instant expiry) {
        ApplicationId owner = ctx.node().owner().orElseThrow();
        List<SyncFileInfo> filesToUpload = producedArtifacts.stream()
                .map(a -> {
                    Compression compression = a.compressOnUpload() ? Compression.ZSTD : Compression.NONE;
                    Path fileInNode = a.fileInNode().orElse(null);
                    Path fileOnHost = fileInNode != null ? ctx.pathOnHostFromPathInNode(fileInNode) : a.fileOnHost().orElseThrow();
                    String classification = a.classification().map(Artifact.Classification::value).orElse(null);
                    return SyncFileInfo.forServiceDump(destination, fileOnHost, expiry, compression, owner, classification);
                })
                .collect(Collectors.toList());
        ctx.log(log, Level.INFO,
                "Uploading " + filesToUpload.size() + " file(s) with destination " + destination + " and expiry " + expiry);
        if (!syncClient.sync(ctx, filesToUpload, Integer.MAX_VALUE)) {
            throw new RuntimeException("Unable to upload all files");
        }
        ctx.log(log, Level.INFO, "Upload complete");
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

    private class ProducerContext implements ArtifactProducer.Context, ArtifactProducer.Context.Options {

        final NodeAgentContext nodeAgentCtx;
        final Path outputDirectoryInNode;
        final ServiceDumpReport request;
        volatile int pid = -1;

        ProducerContext(NodeAgentContext nodeAgentCtx, Path outputDirectoryInNode, ServiceDumpReport request) {
            this.nodeAgentCtx = nodeAgentCtx;
            this.outputDirectoryInNode = outputDirectoryInNode;
            this.request = request;
        }

        @Override public String serviceId() { return request.configId(); }

        @Override
        public int servicePid() {
            if (pid == -1) {
                Path findPidBinary = nodeAgentCtx.pathInNodeUnderVespaHome("libexec/vespa/find-pid");
                CommandResult findPidResult = executeCommandInNode(List.of(findPidBinary.toString(), serviceId()), true);
                this.pid = Integer.parseInt(findPidResult.getOutput());
            }
            return pid;
        }

        @Override
        public CommandResult executeCommandInNode(List<String> command, boolean logOutput) {
            CommandResult result = container.executeCommandInContainerAsRoot(nodeAgentCtx, command.toArray(new String[0]));
            String cmdString = command.stream().map(s -> "'" + s + "'").collect(Collectors.joining(" ", "\"", "\""));
            int exitCode = result.getExitCode();
            String output = result.getOutput().trim();
            String prefixedOutput = output.contains("\n")
                    ? "\n" + output
                    : (output.isEmpty() ? "<no output>" : output);
            if (exitCode > 0) {
                String errorMsg = logOutput
                        ? String.format("Failed to execute %s (exited with code %d): %s", cmdString, exitCode, prefixedOutput)
                        : String.format("Failed to execute %s (exited with code %d)", cmdString, exitCode);
                throw new RuntimeException(errorMsg);
            } else {
                String logMsg = logOutput
                        ? String.format("Executed command %s. Exited with code %d and output: %s", cmdString, exitCode, prefixedOutput)
                        : String.format("Executed command %s. Exited with code %d.", cmdString, exitCode);
                nodeAgentCtx.log(log, logMsg);
            }
            return result;
        }

        @Override public Path outputDirectoryInNode() { return outputDirectoryInNode; }

        @Override
        public Path pathInNodeUnderVespaHome(String relativePath) {
            return nodeAgentCtx.pathInNodeUnderVespaHome(relativePath);
        }

        @Override
        public Path pathOnHostFromPathInNode(Path pathInNode) {
            return nodeAgentCtx.pathOnHostFromPathInNode(pathInNode);
        }

        @Override public Options options() { return this; }

        @Override
        public OptionalDouble duration() {
            Double duration = dumpOptions()
                    .map(ServiceDumpReport.DumpOptions::duration)
                    .orElse(null);
            return duration != null ? OptionalDouble.of(duration) : OptionalDouble.empty();
        }

        @Override
        public boolean callGraphRecording() {
            return dumpOptions().map(ServiceDumpReport.DumpOptions::callGraphRecording).orElse(false);
        }

        @Override
        public boolean sendProfilingSignal() {
            return dumpOptions().map(ServiceDumpReport.DumpOptions::sendProfilingSignal).orElse(false);
        }

        Optional<ServiceDumpReport.DumpOptions> dumpOptions() { return Optional.ofNullable(request.dumpOptions()); }
    }
}

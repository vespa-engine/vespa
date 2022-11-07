// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
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
import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;
import com.yahoo.yolean.concurrent.Sleeper;

import java.io.UncheckedIOException;
import java.net.URI;
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
        if (context.zone().getCloudName().equals(CloudName.GCP)) return;

        Instant startedAt = clock.instant();
        NodeSpec nodeSpec = context.node();
        ServiceDumpReport request;
        try {
            request = nodeSpec.reports().getReport(ServiceDumpReport.REPORT_ID, ServiceDumpReport.class)
                    .orElse(null);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            handleFailure(context, null, startedAt, e, "Invalid JSON in service dump request");
            return;
        }
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
        ContainerPath directory = context.paths().underVespaHome("var/tmp/vespa-service-dump-" + request.getCreatedMillisOrNull());
        UnixPath unixPathDirectory = new UnixPath(directory);
        try {
            context.log(log, Level.INFO,
                    "Creating service dump for " + configId + " requested at "
                            + Instant.ofEpochMilli(request.getCreatedMillisOrNull()));
            storeReport(context, ServiceDumpReport.createStartedReport(request, startedAt));
            if (unixPathDirectory.exists()) {
                context.log(log, Level.INFO, "Removing existing directory '" + unixPathDirectory +"'.");
                unixPathDirectory.deleteRecursively();
            }
            context.log(log, Level.INFO, "Creating '" + unixPathDirectory +"'.");
            unixPathDirectory.createDirectory("rwxr-x---");
            URI destination = serviceDumpDestination(nodeSpec, createDumpId(request));
            ProducerContext producerCtx = new ProducerContext(context, directory, request);
            List<Artifact> producedArtifacts = new ArrayList<>();
            for (ArtifactProducer producer : artifactProducers.resolve(requestedArtifacts)) {
                context.log(log, "Producing artifact of type '" + producer.artifactName() + "'");
                producedArtifacts.addAll(producer.produceArtifacts(producerCtx));
            }
            uploadArtifacts(context, destination, producedArtifacts);
            storeReport(context, ServiceDumpReport.createSuccessReport(request, startedAt, clock.instant(), destination));
        } catch (Exception e) {
            handleFailure(context, request, startedAt, e, e.getMessage());
        } finally {
            if (unixPathDirectory.exists()) {
                context.log(log, Level.INFO, "Deleting directory '" + unixPathDirectory +"'.");
                unixPathDirectory.deleteRecursively();
            }
        }
    }

    private void uploadArtifacts(NodeAgentContext ctx, URI destination,
                                 List<Artifact> producedArtifacts) {
        ApplicationId owner = ctx.node().owner().orElseThrow();
        List<SyncFileInfo> filesToUpload = producedArtifacts.stream()
                .map(a -> {
                    Compression compression = a.compressOnUpload() ? Compression.ZSTD : Compression.NONE;
                    String classification = a.classification().map(Artifact.Classification::value).orElse(null);
                    return SyncFileInfo.forServiceDump(destination, a.file(), compression, owner, classification);
                })
                .collect(Collectors.toList());
        ctx.log(log, Level.INFO,
                "Uploading " + filesToUpload.size() + " file(s) with destination " + destination);
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

    private void handleFailure(NodeAgentContext context, ServiceDumpReport requestOrNull, Instant startedAt,
                               Exception failure, String message) {
        context.log(log, Level.WARNING, failure.toString(), failure);
        ServiceDumpReport report = ServiceDumpReport.createErrorReport(requestOrNull, startedAt, clock.instant(), message);
        storeReport(context, report);
    }

    private void handleFailure(NodeAgentContext context, ServiceDumpReport requestOrNull, Instant startedAt, String message) {
        context.log(log, Level.WARNING, message);
        ServiceDumpReport report = ServiceDumpReport.createErrorReport(requestOrNull, startedAt, clock.instant(), message);
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
        final ContainerPath path;
        final ServiceDumpReport request;
        volatile int pid = -1;

        ProducerContext(NodeAgentContext nodeAgentCtx, ContainerPath path, ServiceDumpReport request) {
            this.nodeAgentCtx = nodeAgentCtx;
            this.path = path;
            this.request = request;
        }

        @Override public String serviceId() { return request.configId(); }

        @Override
        public int servicePid() {
            if (pid == -1) {
                try {
                    pid = findServicePid(serviceId());
                } catch (RuntimeException e1) {
                    try {
                        // Workaround for Vespa 7 container clusters having service name 'qrserver'
                        if (serviceId().equals("container")) pid = findServicePid("qrserver");
                        else throw e1;
                    } catch (RuntimeException e2) {
                        e1.addSuppressed(e2);
                        throw e1;
                    }
                }
            }
            return pid;
        }

        private int findServicePid(String serviceId) {
            ContainerPath findPidBinary = nodeAgentCtx.paths().underVespaHome("libexec/vespa/find-pid");
            CommandResult findPidResult = executeCommandInNode(List.of(findPidBinary.pathInContainer(), serviceId), true);
            return Integer.parseInt(findPidResult.getOutput());
        }

        @Override
        public CommandResult executeCommandInNode(List<String> command, boolean logOutput) {
            CommandResult result = container.executeCommandInContainer(nodeAgentCtx, nodeAgentCtx.users().vespa(), command.toArray(new String[0]));
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

        @Override public ContainerPath outputContainerPath() { return path; }

        @Override
        public ContainerPath containerPathUnderVespaHome(String relativePath) {
            return nodeAgentCtx.paths().underVespaHome(relativePath);
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

// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.coredump;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.Metrics;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.nameEndsWith;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.nameMatches;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.nameStartsWith;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Finds coredumps, collects metadata and reports them
 *
 * @author freva
 */
public class CoredumpHandler {

    private static final Pattern HS_ERR_PATTERN = Pattern.compile("hs_err_pid[0-9]+\\.log");
    private static final String LZ4_PATH = "/usr/bin/lz4";
    private static final String PROCESSING_DIRECTORY_NAME = "processing";
    private static final String METADATA_FILE_NAME = "metadata.json";
    public static final String COREDUMP_FILENAME_PREFIX = "dump_";

    private final Logger logger = Logger.getLogger(CoredumpHandler.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Terminal terminal;
    private final CoreCollector coreCollector;
    private final CoredumpReporter coredumpReporter;
    private final Path crashPatchInContainer;
    private final Path doneCoredumpsPath;
    private final String operatorGroupName;
    private final Supplier<String> coredumpIdSupplier;
    private final Metrics metrics;

    /**
     * @param crashPathInContainer path inside the container where core dump are dumped
     * @param doneCoredumpsPath path on host where processed core dumps are stored
     * @param operatorGroupName name of the group that will be set as the owner of the processed coredump
     */
    public CoredumpHandler(Terminal terminal, CoreCollector coreCollector, CoredumpReporter coredumpReporter,
                           Path crashPathInContainer, Path doneCoredumpsPath, String operatorGroupName, Metrics metrics) {
        this(terminal, coreCollector, coredumpReporter, crashPathInContainer, doneCoredumpsPath,
                operatorGroupName, metrics, () -> UUID.randomUUID().toString());
    }

    CoredumpHandler(Terminal terminal, CoreCollector coreCollector, CoredumpReporter coredumpReporter,
                           Path crashPathInContainer, Path doneCoredumpsPath, String operatorGroupName, Metrics metrics, Supplier<String> coredumpIdSupplier) {
        this.terminal = terminal;
        this.coreCollector = coreCollector;
        this.coredumpReporter = coredumpReporter;
        this.crashPatchInContainer = crashPathInContainer;
        this.doneCoredumpsPath = doneCoredumpsPath;
        this.operatorGroupName = operatorGroupName;
        this.metrics = metrics;
        this.coredumpIdSupplier = coredumpIdSupplier;
    }


    public void converge(NodeAgentContext context, Supplier<Map<String, Object>> nodeAttributesSupplier) {
        Path containerCrashPathOnHost = context.pathOnHostFromPathInNode(crashPatchInContainer);
        Path containerProcessingPathOnHost = containerCrashPathOnHost.resolve(PROCESSING_DIRECTORY_NAME);

        updateMetrics(context, containerCrashPathOnHost);

        // Check if we have already started to process a core dump or we can enqueue a new core one
        getCoredumpToProcess(containerCrashPathOnHost, containerProcessingPathOnHost)
                .ifPresent(path -> processAndReportSingleCoredump(context, path, nodeAttributesSupplier));
    }

    /** @return path to directory inside processing directory that contains a core dump file to process */
    Optional<Path> getCoredumpToProcess(Path containerCrashPathOnHost, Path containerProcessingPathOnHost) {
        return FileFinder.directories(containerProcessingPathOnHost).stream()
                .map(FileFinder.FileAttributes::path)
                .findAny()
                .or(() -> enqueueCoredump(containerCrashPathOnHost, containerProcessingPathOnHost));
    }

    /**
     * Moves a coredump and related hs_err file(s) to a new directory under the processing/ directory.
     * Limit to only processing one coredump at the time, starting with the oldest.
     *
     * Assumption: hs_err files are much smaller than core files and are written (last modified time)
     * before the core file.
     *
     * @return path to directory inside processing directory which contains the enqueued core dump file
     */
    Optional<Path> enqueueCoredump(Path containerCrashPathOnHost, Path containerProcessingPathOnHost) {
        List<Path> toProcess = FileFinder.files(containerCrashPathOnHost)
                .match(nameStartsWith(".").negate()) // Skip core dump files currently being written
                .maxDepth(1)
                .stream()
                .sorted(Comparator.comparing(FileFinder.FileAttributes::lastModifiedTime))
                .map(FileFinder.FileAttributes::path)
                .collect(Collectors.toList());

        int coredumpIndex = IntStream.range(0, toProcess.size())
                .filter(i -> !HS_ERR_PATTERN.matcher(toProcess.get(i).getFileName().toString()).matches())
                .findFirst()
                .orElse(-1);

        // Either there are no files in crash directory, or all the files are hs_err files.
        if (coredumpIndex == -1) return Optional.empty();

        Path enqueuedDir = uncheck(() -> Files.createDirectories(containerProcessingPathOnHost.resolve(coredumpIdSupplier.get())));
        IntStream.range(0, coredumpIndex + 1)
                .forEach(i -> {
                    Path path = toProcess.get(i);
                    String prefix = i == coredumpIndex ? COREDUMP_FILENAME_PREFIX : "";
                    uncheck(() -> Files.move(path, enqueuedDir.resolve(prefix + path.getFileName())));
                });
        return Optional.of(enqueuedDir);
    }

    void processAndReportSingleCoredump(NodeAgentContext context, Path coredumpDirectory, Supplier<Map<String, Object>> nodeAttributesSupplier) {
        try {
            String metadata = getMetadata(context, coredumpDirectory, nodeAttributesSupplier);
            String coredumpId = coredumpDirectory.getFileName().toString();
            coredumpReporter.reportCoredump(coredumpId, metadata);
            finishProcessing(context, coredumpDirectory);
            context.log(logger, "Successfully reported coredump " + coredumpId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process coredump " + coredumpDirectory, e);
        }
    }

    /**
     * @return coredump metadata from metadata.json if present, otherwise attempts to get metadata using
     * {@link CoreCollector} and stores it to metadata.json
     */
    String getMetadata(NodeAgentContext context, Path coredumpDirectory, Supplier<Map<String, Object>> nodeAttributesSupplier) throws IOException {
        UnixPath metadataPath = new UnixPath(coredumpDirectory.resolve(METADATA_FILE_NAME));
        if (!Files.exists(metadataPath.toPath())) {
            Path coredumpFilePathOnHost = findCoredumpFileInProcessingDirectory(coredumpDirectory);
            Path coredumpFilePathInContainer = context.pathInNodeFromPathOnHost(coredumpFilePathOnHost);
            Map<String, Object> metadata = new HashMap<>(coreCollector.collect(context, coredumpFilePathInContainer));
            metadata.putAll(nodeAttributesSupplier.get());
            metadata.put("coredump_path", doneCoredumpsPath.resolve(context.containerName().asString()).resolve(coredumpDirectory.getFileName()).toString());

            String metadataFields = objectMapper.writeValueAsString(Map.of("fields", metadata));
            metadataPath.writeUtf8File(metadataFields);
            return metadataFields;
        } else {
            return metadataPath.readUtf8File();
        }
    }

    /**
     * Compresses core file (and deletes the uncompressed core), then moves the entire core dump processing
     * directory to {@link #doneCoredumpsPath} for archive
     */
    private void finishProcessing(NodeAgentContext context, Path coredumpDirectory) throws IOException {
        Path coreFile = findCoredumpFileInProcessingDirectory(coredumpDirectory);
        Path compressedCoreFile = coreFile.getParent().resolve(coreFile.getFileName() + ".lz4");
        terminal.newCommandLine(context)
                .add(LZ4_PATH, "-f", coreFile.toString(), compressedCoreFile.toString())
                .setTimeout(Duration.ofMinutes(30))
                .execute();
        new UnixPath(compressedCoreFile).setGroup(operatorGroupName).setPermissions("rw-r-----");
        Files.delete(coreFile);

        Path newCoredumpDirectory = doneCoredumpsPath.resolve(context.containerName().asString());
        uncheck(() -> Files.createDirectories(newCoredumpDirectory));
        Files.move(coredumpDirectory, newCoredumpDirectory.resolve(coredumpDirectory.getFileName()));
    }

    Path findCoredumpFileInProcessingDirectory(Path coredumpProccessingDirectory) {
        return FileFinder.files(coredumpProccessingDirectory)
                .match(nameStartsWith(COREDUMP_FILENAME_PREFIX).and(nameEndsWith(".lz4").negate()))
                .maxDepth(1)
                .stream()
                .map(FileFinder.FileAttributes::path)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No coredump file found in processing directory " + coredumpProccessingDirectory));
    }

    void updateMetrics(NodeAgentContext context, Path containerCrashPathOnHost) {
        Dimensions dimensions = generateDimensions(context);

        // Unprocessed coredumps
        int numberOfUnprocessedCoredumps = FileFinder.files(containerCrashPathOnHost)
                .match(nameStartsWith(".").negate())
                .match(nameMatches(HS_ERR_PATTERN).negate())
                .match(nameEndsWith(".lz4").negate())
                .match(nameStartsWith("metadata").negate())
                .list().size();

        metrics.declareGauge(Metrics.APPLICATION_NODE, "coredumps.enqueued", dimensions, Metrics.DimensionType.PRETAGGED).sample(numberOfUnprocessedCoredumps);

        // Processed coredumps
        Path processedCoredumpsPath = doneCoredumpsPath.resolve(context.containerName().asString());
        int numberOfProcessedCoredumps = FileFinder.directories(processedCoredumpsPath)
                .maxDepth(1)
                .list().size();

        metrics.declareGauge(Metrics.APPLICATION_NODE, "coredumps.processed", dimensions, Metrics.DimensionType.PRETAGGED).sample(numberOfProcessedCoredumps);
    }

    private Dimensions generateDimensions(NodeAgentContext context) {
        NodeSpec node = context.node();
        Dimensions.Builder dimensionsBuilder = new Dimensions.Builder()
                .add("host", node.hostname())
                .add("flavor", node.flavor())
                .add("state", node.state().toString())
                .add("zone", context.zone().getId().value());

        node.owner().ifPresent(owner ->
            dimensionsBuilder
                    .add("tenantName", owner.tenant().value())
                    .add("applicationName", owner.application().value())
                    .add("instanceName", owner.instance().value())
                    .add("app", String.join(".", owner.application().value(), owner.instance().value()))
                    .add("applicationId", owner.toFullString())
        );

        node.membership().ifPresent(membership ->
            dimensionsBuilder
                    .add("clustertype", membership.type().value())
                    .add("clusterid", membership.clusterId())
        );

        node.parentHostname().ifPresent(parent -> dimensionsBuilder.add("parentHostname", parent));
        dimensionsBuilder.add("orchestratorState", node.orchestratorStatus().asString());
        node.currentVespaVersion().ifPresent(vespaVersion -> dimensionsBuilder.add("vespaVersion", vespaVersion.toFullString()));

        return dimensionsBuilder.build();
    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.coredump;

import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.node.admin.container.metrics.DimensionMetrics;
import com.yahoo.vespa.hosted.node.admin.container.metrics.Metrics;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class CoredumpHandlerTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final NodeAgentContext context = NodeAgentContextImpl.builder("container-123.domain.tld")
            .fileSystem(fileSystem).build();
    private final ContainerPath containerCrashPath = context.paths().of("/var/crash");
    private final Path doneCoredumpsPath = fileSystem.getPath("/home/docker/dumps");

    private final CoreCollector coreCollector = mock(CoreCollector.class);
    private final CoredumpReporter coredumpReporter = mock(CoredumpReporter.class);
    private final Metrics metrics = new Metrics();
    private final ManualClock clock = new ManualClock();
    @SuppressWarnings("unchecked")
    private final Supplier<String> coredumpIdSupplier = mock(Supplier.class);
    private final CoredumpHandler coredumpHandler = new CoredumpHandler(coreCollector, coredumpReporter,
            containerCrashPath.pathInContainer(), doneCoredumpsPath, metrics, clock, coredumpIdSupplier);


    @Test
    public void coredump_enqueue_test() throws IOException {
        ContainerPath crashPath = context.paths().of("/some/crash/path");
        ContainerPath processingDir = context.paths().of("/some/other/processing");

        Files.createDirectories(crashPath);
        createFileAged(crashPath.resolve("bash.core.431"), Duration.ZERO);

        assertFolderContents(crashPath, "bash.core.431");
        Optional<ContainerPath> enqueuedPath = coredumpHandler.enqueueCoredump(crashPath, processingDir);
        assertEquals(Optional.empty(), enqueuedPath);

        // bash.core.431 finished writing... and 2 more have since been written
        clock.advance(Duration.ofMinutes(3));
        createFileAged(crashPath.resolve("vespa-proton.core.119"), Duration.ofMinutes(10));
        createFileAged(crashPath.resolve("vespa-slobrok.core.673"), Duration.ofMinutes(5));

        when(coredumpIdSupplier.get()).thenReturn("id-123").thenReturn("id-321");
        enqueuedPath = coredumpHandler.enqueueCoredump(crashPath, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-123")), enqueuedPath);
        assertFolderContents(crashPath, "bash.core.431", "vespa-slobrok.core.673");
        assertFolderContents(processingDir, "id-123");
        assertFolderContents(processingDir.resolve("id-123"), "dump_vespa-proton.core.119");
        verify(coredumpIdSupplier, times(1)).get();

        // Enqueue another
        enqueuedPath = coredumpHandler.enqueueCoredump(crashPath, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-321")), enqueuedPath);
        assertFolderContents(crashPath, "bash.core.431");
        assertFolderContents(processingDir, "id-123", "id-321");
        assertFolderContents(processingDir.resolve("id-321"), "dump_vespa-slobrok.core.673");
        verify(coredumpIdSupplier, times(2)).get();
    }

    @Test
    public void enqueue_with_hs_err_files() throws IOException {
        ContainerPath crashPath = context.paths().of("/some/crash/path");
        ContainerPath processingDir = context.paths().of("/some/other/processing");
        Files.createDirectories(crashPath);

        createFileAged(crashPath.resolve("java.core.69"), Duration.ofSeconds(515));
        createFileAged(crashPath.resolve("hs_err_pid69.log"), Duration.ofSeconds(520));

        createFileAged(crashPath.resolve("java.core.2420"), Duration.ofSeconds(540));
        createFileAged(crashPath.resolve("hs_err_pid2420.log"), Duration.ofSeconds(549));
        createFileAged(crashPath.resolve("hs_err_pid2421.log"), Duration.ofSeconds(550));

        when(coredumpIdSupplier.get()).thenReturn("id-123").thenReturn("id-321");
        Optional<ContainerPath> enqueuedPath = coredumpHandler.enqueueCoredump(crashPath, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-123")), enqueuedPath);
        assertFolderContents(crashPath, "hs_err_pid69.log", "java.core.69");
        assertFolderContents(processingDir, "id-123");
        assertFolderContents(processingDir.resolve("id-123"), "hs_err_pid2420.log", "hs_err_pid2421.log", "dump_java.core.2420");
    }

    @Test
    public void coredump_to_process_test() throws IOException {
        ContainerPath processingDir = context.paths().of("/some/other/processing");

        // Initially there are no core dumps
        Optional<ContainerPath> enqueuedPath = coredumpHandler.enqueueCoredump(containerCrashPath, processingDir);
        assertEquals(Optional.empty(), enqueuedPath);

        // 3 core dumps occur
        Files.createDirectories(containerCrashPath);
        createFileAged(containerCrashPath.resolve("bash.core.431"), Duration.ZERO);
        createFileAged(containerCrashPath.resolve("vespa-proton.core.119"), Duration.ofMinutes(10));
        createFileAged(containerCrashPath.resolve("vespa-slobrok.core.673"), Duration.ofMinutes(5));

        when(coredumpIdSupplier.get()).thenReturn("id-123");
        enqueuedPath = coredumpHandler.getCoredumpToProcess(containerCrashPath, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-123")), enqueuedPath);

        // Running this again wont enqueue new core dumps as we are still processing the one enqueued previously
        enqueuedPath = coredumpHandler.getCoredumpToProcess(containerCrashPath, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-123")), enqueuedPath);
        verify(coredumpIdSupplier, times(1)).get();
    }

    @Test
    public void get_metadata_test() throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("bin_path", "/bin/bash");
        metadata.put("backtrace", List.of("call 1", "function 2", "something something"));

        Map<String, Object> attributes = Map.of(
                "hostname", "host123.yahoo.com",
                "vespa_version", "6.48.4",
                "kernel_version", "3.10.0-862.9.1.el7.x86_64",
                "docker_image", "vespa/ci:6.48.4");

        String expectedMetadataStr = "{\"fields\":{" +
                "\"hostname\":\"host123.yahoo.com\"," +
                "\"kernel_version\":\"3.10.0-862.9.1.el7.x86_64\"," +
                "\"backtrace\":[\"call 1\",\"function 2\",\"something something\"]," +
                "\"vespa_version\":\"6.48.4\"," +
                "\"bin_path\":\"/bin/bash\"," +
                "\"coredump_path\":\"/home/docker/dumps/container-123/id-123/dump_core.456\"," +
                "\"docker_image\":\"vespa/ci:6.48.4\"" +
                "}}";


        ContainerPath coredumpDirectory = context.paths().of("/var/crash/id-123");
        Files.createDirectories(coredumpDirectory.pathOnHost());
        Files.createFile(coredumpDirectory.resolve("dump_core.456"));
        when(coreCollector.collect(eq(context), eq(coredumpDirectory.resolve("dump_core.456"))))
                .thenReturn(metadata);

        assertEquals(expectedMetadataStr, coredumpHandler.getMetadata(context, coredumpDirectory, () -> attributes));
        verify(coreCollector, times(1)).collect(any(), any());

        // Calling it again will simply read the previously generated metadata from disk
        assertEquals(expectedMetadataStr, coredumpHandler.getMetadata(context, coredumpDirectory, () -> attributes));
        verify(coreCollector, times(1)).collect(any(), any());
    }

    @Test(expected = IllegalStateException.class)
    public void cant_get_metadata_if_no_core_file() throws IOException {
        coredumpHandler.getMetadata(context, context.paths().of("/fake/path"), Map::of);
    }

    @Test(expected = IllegalStateException.class)
    public void fails_to_get_core_file_if_only_compressed() throws IOException {
        ContainerPath coredumpDirectory = context.paths().of("/path/to/coredump/proccessing/id-123");
        Files.createDirectories(coredumpDirectory);
        Files.createFile(coredumpDirectory.resolve("dump_bash.core.431.zstd"));
        coredumpHandler.findCoredumpFileInProcessingDirectory(coredumpDirectory);
    }

    @Test
    public void process_single_coredump_test() throws IOException {
        ContainerPath coredumpDirectory = context.paths().of("/path/to/coredump/proccessing/id-123");
        Files.createDirectories(coredumpDirectory);
        Files.write(coredumpDirectory.resolve("metadata.json"), "metadata".getBytes());
        Files.createFile(coredumpDirectory.resolve("dump_bash.core.431"));
        assertFolderContents(coredumpDirectory, "metadata.json", "dump_bash.core.431");

        coredumpHandler.processAndReportSingleCoredump(context, coredumpDirectory, Map::of);
        verify(coreCollector, never()).collect(any(), any());
        verify(coredumpReporter, times(1)).reportCoredump(eq("id-123"), eq("metadata"));
        assertFalse(Files.exists(coredumpDirectory));
        assertFolderContents(doneCoredumpsPath.resolve("container-123"), "id-123");
        assertFolderContents(doneCoredumpsPath.resolve("container-123").resolve("id-123"), "metadata.json", "dump_bash.core.431.zstd");
    }

    @Test
    public void report_enqueued_and_processed_metrics() throws IOException {
        Path processingPath = containerCrashPath.resolve("processing");
        Files.createFile(containerCrashPath.resolve("dump-1"));
        Files.createFile(containerCrashPath.resolve("dump-2"));
        Files.createFile(containerCrashPath.resolve("hs_err_pid2.log"));
        Files.createDirectory(processingPath);
        Files.createFile(processingPath.resolve("metadata.json"));
        Files.createFile(processingPath.resolve("dump-3"));

        new UnixPath(doneCoredumpsPath.resolve("container-123").resolve("dump-3-folder").resolve("dump-3"))
                .createParents()
                .createNewFile();

        coredumpHandler.updateMetrics(context, containerCrashPath);
        List<DimensionMetrics> updatedMetrics = metrics.getMetricsByType(Metrics.DimensionType.PRETAGGED);
        assertEquals(1, updatedMetrics.size());
        Map<String, Number> values = updatedMetrics.get(0).getMetrics();
        assertEquals(3, values.get("coredumps.enqueued").intValue());
        assertEquals(1, values.get("coredumps.processed").intValue());
    }

    @Before
    public void setup() throws IOException {
        Files.createDirectories(containerCrashPath.pathOnHost());
    }

    private static void assertFolderContents(Path pathToFolder, String... filenames) {
        Set<String> expectedContentsOfFolder = Set.of(filenames);
        Set<String> actualContentsOfFolder;
        try (Stream<UnixPath> paths = new UnixPath(pathToFolder).listContentsOfDirectory()) {
            actualContentsOfFolder = paths.map(unixPath -> unixPath.toPath().getFileName().toString())
                                          .collect(Collectors.toSet());
        }
        assertEquals(expectedContentsOfFolder, actualContentsOfFolder);
    }

    private Path createFileAged(Path path, Duration age) {
        return uncheck(() -> Files.setLastModifiedTime(
                Files.createFile(path),
                FileTime.from(clock.instant().minus(age))));
    }
}

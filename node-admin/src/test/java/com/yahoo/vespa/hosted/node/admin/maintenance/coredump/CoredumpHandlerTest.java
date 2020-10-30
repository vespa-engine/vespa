// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.coredump;

import com.yahoo.vespa.hosted.dockerapi.metrics.DimensionMetrics;
import com.yahoo.vespa.hosted.dockerapi.metrics.Metrics;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestChildProcess2;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
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
    private final NodeAgentContext context = new NodeAgentContextImpl.Builder("container-123.domain.tld")
            .fileSystem(fileSystem).build();
    private final Path crashPathInContainer = fileSystem.getPath("/var/crash");
    private final Path doneCoredumpsPath = fileSystem.getPath("/home/docker/dumps");

    private final TestTerminal terminal = new TestTerminal();
    private final CoreCollector coreCollector = mock(CoreCollector.class);
    private final CoredumpReporter coredumpReporter = mock(CoredumpReporter.class);
    private final Metrics metrics = new Metrics();
    @SuppressWarnings("unchecked")
    private final Supplier<String> coredumpIdSupplier = mock(Supplier.class);
    private final CoredumpHandler coredumpHandler = new CoredumpHandler(terminal, coreCollector, coredumpReporter,
            crashPathInContainer, doneCoredumpsPath, "users", metrics, coredumpIdSupplier);


    @Test
    public void coredump_enqueue_test() throws IOException {
        final Path crashPathOnHost = fileSystem.getPath("/home/docker/container-1/some/crash/path");
        final Path processingDir = fileSystem.getPath("/home/docker/container-1/some/other/processing");

        Files.createDirectories(crashPathOnHost);
        createFileAged(crashPathOnHost.resolve(".bash.core.431"), Duration.ZERO);

        assertFolderContents(crashPathOnHost, ".bash.core.431");
        Optional<Path> enqueuedPath = coredumpHandler.enqueueCoredump(crashPathOnHost, processingDir);
        assertEquals(Optional.empty(), enqueuedPath);

        // bash.core.431 finished writing... and 2 more have since been written
        Files.move(crashPathOnHost.resolve(".bash.core.431"), crashPathOnHost.resolve("bash.core.431"));
        createFileAged(crashPathOnHost.resolve("vespa-proton.core.119"), Duration.ofMinutes(10));
        createFileAged(crashPathOnHost.resolve("vespa-slobrok.core.673"), Duration.ofMinutes(5));

        when(coredumpIdSupplier.get()).thenReturn("id-123").thenReturn("id-321");
        enqueuedPath = coredumpHandler.enqueueCoredump(crashPathOnHost, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-123")), enqueuedPath);
        assertFolderContents(crashPathOnHost, "bash.core.431", "vespa-slobrok.core.673");
        assertFolderContents(processingDir, "id-123");
        assertFolderContents(processingDir.resolve("id-123"), "dump_vespa-proton.core.119");
        verify(coredumpIdSupplier, times(1)).get();

        // Enqueue another
        enqueuedPath = coredumpHandler.enqueueCoredump(crashPathOnHost, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-321")), enqueuedPath);
        assertFolderContents(crashPathOnHost, "bash.core.431");
        assertFolderContents(processingDir, "id-123", "id-321");
        assertFolderContents(processingDir.resolve("id-321"), "dump_vespa-slobrok.core.673");
        verify(coredumpIdSupplier, times(2)).get();
    }

    @Test
    public void enqueue_with_hs_err_files() throws IOException {
        final Path crashPathOnHost = fileSystem.getPath("/home/docker/container-1/some/crash/path");
        final Path processingDir = fileSystem.getPath("/home/docker/container-1/some/other/processing");
        Files.createDirectories(crashPathOnHost);

        createFileAged(crashPathOnHost.resolve("java.core.69"), Duration.ofSeconds(15));
        createFileAged(crashPathOnHost.resolve("hs_err_pid69.log"), Duration.ofSeconds(20));

        createFileAged(crashPathOnHost.resolve("java.core.2420"), Duration.ofSeconds(40));
        createFileAged(crashPathOnHost.resolve("hs_err_pid2420.log"), Duration.ofSeconds(49));
        createFileAged(crashPathOnHost.resolve("hs_err_pid2421.log"), Duration.ofSeconds(50));

        when(coredumpIdSupplier.get()).thenReturn("id-123").thenReturn("id-321");
        Optional<Path> enqueuedPath = coredumpHandler.enqueueCoredump(crashPathOnHost, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-123")), enqueuedPath);
        assertFolderContents(crashPathOnHost, "hs_err_pid69.log", "java.core.69");
        assertFolderContents(processingDir, "id-123");
        assertFolderContents(processingDir.resolve("id-123"), "hs_err_pid2420.log", "hs_err_pid2421.log", "dump_java.core.2420");
    }

    @Test
    public void coredump_to_process_test() throws IOException {
        final Path crashPathOnHost = fileSystem.getPath("/home/docker/container-1/some/crash/path");
        final Path processingDir = fileSystem.getPath("/home/docker/container-1/some/other/processing");

        // Initially there are no core dumps
        Optional<Path> enqueuedPath = coredumpHandler.enqueueCoredump(crashPathOnHost, processingDir);
        assertEquals(Optional.empty(), enqueuedPath);

        // 3 core dumps occur
        Files.createDirectories(crashPathOnHost);
        createFileAged(crashPathOnHost.resolve("bash.core.431"), Duration.ZERO);
        createFileAged(crashPathOnHost.resolve("vespa-proton.core.119"), Duration.ofMinutes(10));
        createFileAged(crashPathOnHost.resolve("vespa-slobrok.core.673"), Duration.ofMinutes(5));

        when(coredumpIdSupplier.get()).thenReturn("id-123");
        enqueuedPath = coredumpHandler.getCoredumpToProcess(crashPathOnHost, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-123")), enqueuedPath);

        // Running this again wont enqueue new core dumps as we are still processing the one enqueued previously
        enqueuedPath = coredumpHandler.getCoredumpToProcess(crashPathOnHost, processingDir);
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
                "\"coredump_path\":\"/home/docker/dumps/container-123/id-123\"," +
                "\"docker_image\":\"vespa/ci:6.48.4\"" +
                "}}";


        Path coredumpDirectoryInContainer = fileSystem.getPath("/var/crash/id-123");
        Path coredumpDirectory = context.pathOnHostFromPathInNode(coredumpDirectoryInContainer);
        Files.createDirectories(coredumpDirectory);
        Files.createFile(coredumpDirectory.resolve("dump_core.456"));
        when(coreCollector.collect(eq(context), eq(coredumpDirectoryInContainer.resolve("dump_core.456"))))
                .thenReturn(metadata);

        assertEquals(expectedMetadataStr, coredumpHandler.getMetadata(context, coredumpDirectory, () -> attributes));
        verify(coreCollector, times(1)).collect(any(), any());

        // Calling it again will simply read the previously generated metadata from disk
        assertEquals(expectedMetadataStr, coredumpHandler.getMetadata(context, coredumpDirectory, () -> attributes));
        verify(coreCollector, times(1)).collect(any(), any());
    }

    @Test(expected = IllegalStateException.class)
    public void cant_get_metadata_if_no_core_file() throws IOException {
        coredumpHandler.getMetadata(context, fileSystem.getPath("/fake/path"), Map::of);
    }

    @Test(expected = IllegalStateException.class)
    public void fails_to_get_core_file_if_only_compressed() throws IOException {
        Path coredumpDirectory = fileSystem.getPath("/path/to/coredump/proccessing/id-123");
        Files.createDirectories(coredumpDirectory);
        Files.createFile(coredumpDirectory.resolve("dump_bash.core.431.lz4"));
        coredumpHandler.findCoredumpFileInProcessingDirectory(coredumpDirectory);
    }

    @Test
    public void process_single_coredump_test() throws IOException {
        Path coredumpDirectory = fileSystem.getPath("/path/to/coredump/proccessing/id-123");
        Files.createDirectories(coredumpDirectory);
        Files.write(coredumpDirectory.resolve("metadata.json"), "metadata".getBytes());
        Files.createFile(coredumpDirectory.resolve("dump_bash.core.431"));
        assertFolderContents(coredumpDirectory, "metadata.json", "dump_bash.core.431");

        terminal.interceptCommand("/usr/bin/lz4 -f /path/to/coredump/proccessing/id-123/dump_bash.core.431 " +
                "/path/to/coredump/proccessing/id-123/dump_bash.core.431.lz4 2>&1",
                commandLine -> {
                    uncheck(() -> Files.createFile(fileSystem.getPath(commandLine.getArguments().get(3))));
                    return new TestChildProcess2(0, "");
                });
        coredumpHandler.processAndReportSingleCoredump(context, coredumpDirectory, Map::of);
        verify(coreCollector, never()).collect(any(), any());
        verify(coredumpReporter, times(1)).reportCoredump(eq("id-123"), eq("metadata"));
        assertFalse(Files.exists(coredumpDirectory));
        assertFolderContents(doneCoredumpsPath.resolve("container-123"), "id-123");
        assertFolderContents(doneCoredumpsPath.resolve("container-123").resolve("id-123"), "metadata.json", "dump_bash.core.431.lz4");
    }

    @Test
    public void report_enqueued_and_processed_metrics() throws IOException {
        Path processingPath = crashPathInContainer.resolve("processing");
        Files.createFile(crashPathInContainer.resolve("dump-1"));
        Files.createFile(crashPathInContainer.resolve("dump-2"));
        Files.createFile(crashPathInContainer.resolve("hs_err_pid2.log"));
        Files.createDirectory(processingPath);
        Files.createFile(processingPath.resolve("metadata.json"));
        Files.createFile(processingPath.resolve("dump-3"));

        new UnixPath(doneCoredumpsPath.resolve("container-123").resolve("dump-3-folder").resolve("dump-3"))
                .createParents()
                .createNewFile();

        coredumpHandler.updateMetrics(context, crashPathInContainer);
        List<DimensionMetrics> updatedMetrics = metrics.getMetricsByType(Metrics.DimensionType.PRETAGGED);
        assertEquals(1, updatedMetrics.size());
        Map<String, Number> values = updatedMetrics.get(0).getMetrics();
        assertEquals(3, values.get("coredumps.enqueued").intValue());
        assertEquals(1, values.get("coredumps.processed").intValue());
    }

    @Before
    public void setup() throws IOException {
        Files.createDirectories(crashPathInContainer);
    }

    @After
    public void teardown() {
        terminal.verifyAllCommandsExecuted();
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

    private static Path createFileAged(Path path, Duration age) {
        return uncheck(() -> Files.setLastModifiedTime(
                Files.createFile(path),
                FileTime.from(Instant.now().minus(age))));
    }
}

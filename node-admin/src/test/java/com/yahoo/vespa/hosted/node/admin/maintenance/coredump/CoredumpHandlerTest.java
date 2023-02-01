// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.coredump;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.security.KeyId;
import com.yahoo.security.SealedSharedKey;
import com.yahoo.security.SecretSharedKey;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.node.admin.configserver.cores.CoreDumpMetadata;
import com.yahoo.vespa.hosted.node.admin.configserver.cores.Cores;
import com.yahoo.vespa.hosted.node.admin.container.metrics.DimensionMetrics;
import com.yahoo.vespa.hosted.node.admin.container.metrics.Metrics;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.ConvergenceException;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private final Cores cores = mock(Cores.class);
    private final Metrics metrics = new Metrics();
    private final ManualClock clock = new ManualClock();
    @SuppressWarnings("unchecked")
    private final Supplier<String> coredumpIdSupplier = mock(Supplier.class);
    private final SecretSharedKeySupplier secretSharedKeySupplier = mock(SecretSharedKeySupplier.class);
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private final CoredumpHandler coredumpHandler =
            new CoredumpHandler(coreCollector, cores, containerCrashPath.pathInContainer(),
                                doneCoredumpsPath, metrics, clock, coredumpIdSupplier, secretSharedKeySupplier,
                                flagSource);

    @Test
    void coredump_enqueue_test() throws IOException {
        ContainerPath crashPath = context.paths().of("/some/crash/path");
        ContainerPath processingDir = context.paths().of("/some/other/processing");

        Files.createDirectories(crashPath);
        createFileAged(crashPath.resolve("bash.core.431"), Duration.ZERO);

        assertFolderContents(crashPath, "bash.core.431");
        Optional<ContainerPath> enqueuedPath = coredumpHandler.enqueueCoredump(context, crashPath, processingDir);
        assertEquals(Optional.empty(), enqueuedPath);

        // bash.core.431 finished writing... and 2 more have since been written
        clock.advance(Duration.ofMinutes(3));
        createFileAged(crashPath.resolve("vespa-proton.core.119"), Duration.ofMinutes(10));
        createFileAged(crashPath.resolve("vespa-slobrok.core.673"), Duration.ofMinutes(5));

        when(coredumpIdSupplier.get()).thenReturn("id-123").thenReturn("id-321");
        enqueuedPath = coredumpHandler.enqueueCoredump(context, crashPath, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-123")), enqueuedPath);
        assertFolderContents(crashPath, "bash.core.431", "vespa-slobrok.core.673");
        assertFolderContents(processingDir, "id-123");
        assertFolderContents(processingDir.resolve("id-123"), "dump_vespa-proton.core.119");
        verify(coredumpIdSupplier, times(1)).get();

        // Enqueue another
        enqueuedPath = coredumpHandler.enqueueCoredump(context, crashPath, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-321")), enqueuedPath);
        assertFolderContents(crashPath, "bash.core.431");
        assertFolderContents(processingDir, "id-123", "id-321");
        assertFolderContents(processingDir.resolve("id-321"), "dump_vespa-slobrok.core.673");
        verify(coredumpIdSupplier, times(2)).get();
    }

    @Test
    void enqueue_with_hs_err_files() throws IOException {
        ContainerPath crashPath = context.paths().of("/some/crash/path");
        ContainerPath processingDir = context.paths().of("/some/other/processing");
        Files.createDirectories(crashPath);

        createFileAged(crashPath.resolve("java.core.69"), Duration.ofSeconds(515));
        createFileAged(crashPath.resolve("hs_err_pid69.log"), Duration.ofSeconds(520));

        createFileAged(crashPath.resolve("java.core.2420"), Duration.ofSeconds(540));
        createFileAged(crashPath.resolve("hs_err_pid2420.log"), Duration.ofSeconds(549));
        createFileAged(crashPath.resolve("hs_err_pid2421.log"), Duration.ofSeconds(550));

        when(coredumpIdSupplier.get()).thenReturn("id-123").thenReturn("id-321");
        Optional<ContainerPath> enqueuedPath = coredumpHandler.enqueueCoredump(context, crashPath, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-123")), enqueuedPath);
        assertFolderContents(crashPath, "hs_err_pid69.log", "java.core.69");
        assertFolderContents(processingDir, "id-123");
        assertFolderContents(processingDir.resolve("id-123"), "hs_err_pid2420.log", "hs_err_pid2421.log", "dump_java.core.2420");
    }

    @Test
    void coredump_to_process_test() throws IOException {
        ContainerPath processingDir = context.paths().of("/some/other/processing");

        // Initially there are no core dumps
        Optional<ContainerPath> enqueuedPath = coredumpHandler.enqueueCoredump(context, containerCrashPath, processingDir);
        assertEquals(Optional.empty(), enqueuedPath);

        // 3 core dumps occur
        Files.createDirectories(containerCrashPath);
        createFileAged(containerCrashPath.resolve("bash.core.431"), Duration.ZERO);
        createFileAged(containerCrashPath.resolve("vespa-proton.core.119"), Duration.ofMinutes(10));
        createFileAged(containerCrashPath.resolve("vespa-slobrok.core.673"), Duration.ofMinutes(5));

        when(coredumpIdSupplier.get()).thenReturn("id-123");
        enqueuedPath = coredumpHandler.getCoredumpToProcess(context, containerCrashPath, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-123")), enqueuedPath);

        // Running this again wont enqueue new core dumps as we are still processing the one enqueued previously
        enqueuedPath = coredumpHandler.getCoredumpToProcess(context, containerCrashPath, processingDir);
        assertEquals(Optional.of(processingDir.resolve("id-123")), enqueuedPath);
        verify(coredumpIdSupplier, times(1)).get();
    }

    @Test
    void gather_metadata_test() throws IOException {
        var metadata = new CoreDumpMetadata().setKernelVersion("3.10.0-862.9.1.el7.x86_64")
                                             .setBacktrace(List.of("call 1", "function 2", "something something"))
                                             .setVespaVersion("6.48.4")
                                             .setBinPath("/bin/bash")
                                             .setCoreDumpPath(context.paths().of("/home/docker/dumps/container-123/id-123/dump_core.456"))
                                             .setDockerImage(DockerImage.fromString("example.com/vespa/ci:6.48.4"));

        new UnixPath(fileSystem.getPath("/proc/cpuinfo")).createParents().writeUtf8File("microcode\t: 0xf0");

        ContainerPath coredumpDirectory = context.paths().of("/var/crash/id-123");
        Files.createDirectories(coredumpDirectory.pathOnHost());
        Files.createFile(coredumpDirectory.resolve("dump_core.456"));
        when(coreCollector.collect(eq(context), eq(coredumpDirectory.resolve("dump_core.456"))))
                .thenReturn(metadata);

        assertEquals(metadata, coredumpHandler.gatherMetadata(context, coredumpDirectory));
        verify(coreCollector, times(1)).collect(any(), any());

        // On second invocation the test already exist, so times(1) is not incremented
        assertEquals(metadata, coredumpHandler.gatherMetadata(context, coredumpDirectory));
        doThrow(new IllegalStateException("Should not be invoked"))
                .when(coreCollector).collect(any(), any());
        verify(coreCollector, times(1)).collect(any(), any());
    }

    @Test
    void cant_get_metadata_if_no_core_file() {
        assertThrows(IllegalStateException.class, () -> {
            coredumpHandler.gatherMetadata(context, context.paths().of("/fake/path"));
        });
    }

    @Test
    void fails_to_get_core_file_if_only_compressed_or_encrypted() {
        assertThrows(IllegalStateException.class, () -> {
            ContainerPath coredumpDirectory = context.paths().of("/path/to/coredump/proccessing/id-123");
            Files.createDirectories(coredumpDirectory);
            Files.createFile(coredumpDirectory.resolve("dump_bash.core.431.zst"));
            Files.createFile(coredumpDirectory.resolve("dump_bash.core.543.zst.enc"));
            coredumpHandler.findCoredumpFileInProcessingDirectory(coredumpDirectory);
        });
    }

    void do_process_single_coredump_test(String expectedCoreFileName) throws IOException {
        ContainerPath coredumpDirectory = context.paths().of("/path/to/coredump/proccessing/id-123");
        Files.createDirectories(coredumpDirectory);
        Files.write(coredumpDirectory.resolve("metadata2.json"), "{\"test-metadata\":{}}".getBytes());
        Files.createFile(coredumpDirectory.resolve("dump_bash.core.431"));
        assertFolderContents(coredumpDirectory, "metadata2.json", "dump_bash.core.431");
        CoreDumpMetadata expectedMetadata = new CoreDumpMetadata();
        expectedMetadata.setDecryptionToken("131Q0MMF3hBuMVnXg1WnSFexZGrcwa9ZhfHlegLNwPIN6hQJnBxq5srLf3aZbYdlRVE");

        coredumpHandler.processAndReportSingleCoreDump(context, coredumpDirectory, Optional.empty());
        verify(coreCollector, never()).collect(any(), any());
        verify(cores, times(1)).report(eq(context.hostname()), eq("id-123"), eq(expectedMetadata));
        assertFalse(Files.exists(coredumpDirectory));
        assertFolderContents(doneCoredumpsPath.resolve("container-123"), "id-123");
        assertFolderContents(doneCoredumpsPath.resolve("container-123").resolve("id-123"), "metadata2.json", expectedCoreFileName);
    }

    @Test
    void processing_single_coredump_test_without_encryption_throws() throws IOException {
        assertThrows(ConvergenceException.class, () -> do_process_single_coredump_test("dump_bash.core.431.zst"));
    }

    @Test
    void process_single_coredump_test_with_encryption() throws IOException {
        flagSource.withStringFlag(Flags.CORE_ENCRYPTION_PUBLIC_KEY_ID.id(), "bar-key");
        when(secretSharedKeySupplier.create(KeyId.ofString("bar-key"))).thenReturn(Optional.of(makeFixedSecretSharedKey()));
        do_process_single_coredump_test("dump_bash.core.431.zst.enc");
    }

    @Test
    void processing_throws_when_no_public_key_set_in_feature_flag() throws IOException {
        flagSource.withStringFlag(Flags.CORE_ENCRYPTION_PUBLIC_KEY_ID.id(), ""); // empty -> not set
        verify(secretSharedKeySupplier, never()).create(any());
        assertThrows(ConvergenceException.class, () -> do_process_single_coredump_test("dump_bash.core.431.zst"));
    }

    @Test
    void processing_throws_when_no_key_returned_for_key_id_specified_by_feature_flag() throws IOException {
        flagSource.withStringFlag(Flags.CORE_ENCRYPTION_PUBLIC_KEY_ID.id(), "baz-key");
        when(secretSharedKeySupplier.create(KeyId.ofString("baz-key"))).thenReturn(Optional.empty());
        assertThrows(ConvergenceException.class, () -> do_process_single_coredump_test("dump_bash.core.431.zst"));
    }

    @Test
    void report_enqueued_and_processed_metrics() throws IOException {
        Path processingPath = containerCrashPath.resolve("processing");
        Files.createFile(containerCrashPath.resolve("dump-1"));
        Files.createFile(containerCrashPath.resolve("dump-2"));
        Files.createFile(containerCrashPath.resolve("hs_err_pid2.log"));
        Files.createDirectory(processingPath);
        Files.createFile(processingPath.resolve("metadata2.json"));
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

    @BeforeEach
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

    private static byte[] bytesOf(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    private static SecretSharedKey makeFixedSecretSharedKey() {
        byte[] keyBytes = bytesOf("very secret yes!"); // 128 bits
        var secretKey = new SecretKeySpec(keyBytes, "AES");
        var keyId = KeyId.ofString("the shiniest key");
        // We don't parse any of these fields in the test, so just use dummy contents.
        byte[] enc = bytesOf("hello world");
        byte[] ciphertext = bytesOf("imaginary ciphertext");
        return new SecretSharedKey(secretKey, new SealedSharedKey(SealedSharedKey.CURRENT_TOKEN_VERSION, keyId, enc, ciphertext));
    }

}

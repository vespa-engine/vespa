// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.coredump;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class CoredumpHandlerTest {

    private static final Map<String, Object> attributes = new LinkedHashMap<>();
    private static final Map<String, Object> metadata = new LinkedHashMap<>();
    private static final String expectedMetadataFileContents = "{\"fields\":{" +
            "\"bin_path\":\"/bin/bash\"," +
            "\"backtrace\":[\"call 1\",\"function 2\",\"something something\"]," +
            "\"hostname\":\"host123.yahoo.com\"," +
            "\"vespa_version\":\"6.48.4\"," +
            "\"kernel_version\":\"2.6.32-573.22.1.el6.YAHOO.20160401.10.x86_64\"," +
            "\"docker_image\":\"vespa/ci:6.48.4\"}}";

    static {
        attributes.put("hostname", "host123.yahoo.com");
        attributes.put("vespa_version", "6.48.4");
        attributes.put("kernel_version", "2.6.32-573.22.1.el6.YAHOO.20160401.10.x86_64");
        attributes.put("docker_image", "vespa/ci:6.48.4");

        metadata.put("bin_path", "/bin/bash");
        metadata.put("backtrace", Arrays.asList("call 1", "function 2", "something something"));
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final CoreCollector coreCollector = mock(CoreCollector.class);
    private final CoredumpReporter coredumpReporter = mock(CoredumpReporter.class);
    private CoredumpHandler coredumpHandler;
    private Path crashPath;
    private Path donePath;
    private Path processingPath;

    @Before
    public void setup() throws IOException {
        crashPath = folder.newFolder("crash").toPath();
        donePath = folder.newFolder("done").toPath();
        processingPath = CoredumpHandler.getProcessingCoredumpsPath(crashPath);

        coredumpHandler = new CoredumpHandler(coreCollector, coredumpReporter, donePath);
    }

    @Test
    public void ignoresIncompleteCoredumps() throws IOException {
        Path coredumpPath = createCoredump(".core.dump", Instant.now());
        coredumpHandler.enqueueCoredumps(crashPath);

        // The 'processing' directory should be empty
        assertFolderContents(processingPath);

        // The 'crash' directory should have 'processing' and the incomplete core dump in it
        assertFolderContents(crashPath, processingPath.getFileName().toString(), coredumpPath.getFileName().toString());
    }

    @Test
    public void startProcessingTest() throws IOException {
        Path coredumpPath = createCoredump("core.dump", Instant.now());
        coredumpHandler.enqueueCoredumps(crashPath);

        // Contents of 'crash' should be only the 'processing' directory
        assertFolderContents(crashPath, processingPath.getFileName().toString());

        // The 'processing' directory should have 1 directory inside for the core.dump we just created
        List<Path> processedCoredumps = Files.list(processingPath).collect(Collectors.toList());
        assertEquals(processedCoredumps.size(), 1);

        // Inside the coredump directory, there should be 1 file: core.dump
        assertFolderContents(processedCoredumps.get(0), coredumpPath.getFileName().toString());
    }

    @Test
    public void limitToProcessingOneCoredumpAtTheTimeTest() throws IOException {
        final String oldestCoredump = "core.dump0";
        final Instant startTime = Instant.now();
        createCoredump(oldestCoredump, startTime.minusSeconds(3600));
        createCoredump("core.dump1", startTime.minusSeconds(1000));
        createCoredump("core.dump2", startTime);
        coredumpHandler.enqueueCoredumps(crashPath);

        List<Path> processingCoredumps = Files.list(processingPath).collect(Collectors.toList());
        assertEquals(1, processingCoredumps.size());

        // Make sure that the 1 coredump that we are processing is the oldest one
        Set<String> filenamesInProcessingDirectory = Files.list(processingCoredumps.get(0))
                .map(file -> file.getFileName().toString())
                .collect(Collectors.toSet());
        assertEquals(Collections.singleton(oldestCoredump), filenamesInProcessingDirectory);

        // Running enqueueCoredumps should not start processing any new coredumps as we already are processing one
        coredumpHandler.enqueueCoredumps(crashPath);
        assertEquals(processingCoredumps, Files.list(processingPath).collect(Collectors.toList()));
        filenamesInProcessingDirectory = Files.list(processingCoredumps.get(0))
                .map(file -> file.getFileName().toString())
                .collect(Collectors.toSet());
        assertEquals(Collections.singleton(oldestCoredump), filenamesInProcessingDirectory);
    }

    @Test
    public void coredumpMetadataCollectAndWriteTest() throws IOException {
        createCoredump("core.dump", Instant.now());
        coredumpHandler.enqueueCoredumps(crashPath);
        Path processingCoredumpPath = Files.list(processingPath).findFirst().orElseThrow(() ->
                new RuntimeException("Expected to find directory with coredump in processing dir"));
        when(coreCollector.collect(eq(processingCoredumpPath.resolve("core.dump")))).thenReturn(metadata);

        // Inside 'processing' directory, there should be a new directory containing 'core.dump' file
        String returnedMetadata = coredumpHandler.collectMetadata(processingCoredumpPath, attributes);
        String metadataFileContents = new String(Files.readAllBytes(
                processingCoredumpPath.resolve(CoredumpHandler.METADATA_FILE_NAME)));
        assertEquals(expectedMetadataFileContents, metadataFileContents);
        assertEquals(expectedMetadataFileContents, returnedMetadata);
    }

    @Test
    public void coredumpMetadataReadIfExistsTest() throws IOException {
        final String documentId = "UIDD-ABCD-EFGH";
        Path metadataPath = createProcessedCoredump(documentId);

        verifyZeroInteractions(coreCollector);
        String returnedMetadata = coredumpHandler.collectMetadata(metadataPath.getParent(), attributes);
        assertEquals(expectedMetadataFileContents, returnedMetadata);
    }

    @Test
    public void reportSuccessCoredumpTest() throws IOException {
        final String documentId = "UIDD-ABCD-EFGH";
        createProcessedCoredump(documentId);

        coredumpHandler.processAndReportCoredumps(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME), attributes);
        verify(coredumpReporter).reportCoredump(eq(documentId), eq(expectedMetadataFileContents));

        // The coredump should not have been moved out of 'processing' and into 'done' as the report failed
        assertFolderContents(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME));
        assertFolderContents(donePath.resolve(documentId), CoredumpHandler.METADATA_FILE_NAME);
    }

    @Test
    public void reportFailCoredumpTest() throws IOException {
        final String documentId = "UIDD-ABCD-EFGH";
        Path metadataPath = createProcessedCoredump(documentId);

        doThrow(new RuntimeException()).when(coredumpReporter).reportCoredump(any(), any());
        coredumpHandler.processAndReportCoredumps(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME), attributes);
        verify(coredumpReporter).reportCoredump(eq(documentId), eq(expectedMetadataFileContents));

        // The coredump should not have been moved out of 'processing' and into 'done' as the report failed
        assertFolderContents(donePath);
        assertFolderContents(metadataPath.getParent(), CoredumpHandler.METADATA_FILE_NAME);
    }

    private static void assertFolderContents(Path pathToFolder, String... filenames) throws IOException {
        Set<Path> expectedContentsOfFolder = Arrays.stream(filenames)
                .map(pathToFolder::resolve)
                .collect(Collectors.toSet());
        Set<Path> actualContentsOfFolder = Files.list(pathToFolder).collect(Collectors.toSet());
        assertEquals(expectedContentsOfFolder, actualContentsOfFolder);
    }

    private Path createCoredump(String coredumpName, Instant lastModified) throws IOException {
        Path coredumpPath = crashPath.resolve(coredumpName);
        coredumpPath.toFile().createNewFile();
        coredumpPath.toFile().setLastModified(lastModified.toEpochMilli());
        return coredumpPath;
    }

    private Path createProcessedCoredump(String documentId) throws IOException {
        Path coredumpPath = crashPath
                .resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME)
                .resolve(documentId)
                .resolve(CoredumpHandler.METADATA_FILE_NAME);
        coredumpPath.getParent().toFile().mkdirs();
        return Files.write(coredumpPath, expectedMetadataFileContents.getBytes());
    }
}

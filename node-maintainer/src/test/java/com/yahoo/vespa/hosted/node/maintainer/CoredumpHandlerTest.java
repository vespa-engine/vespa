// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintainer;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicStatusLine;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class CoredumpHandlerTest {
    private final HttpClient httpClient = mock(HttpClient.class);
    private final CoreCollector coreCollector = mock(CoreCollector.class);
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

    private final CoredumpHandler coredumpHandler = new CoredumpHandler(httpClient, coreCollector);


    @Rule
    public TemporaryFolder folder= new TemporaryFolder();

    @Test
    public void ignoresIncompleteCoredumps() throws IOException {
        Path coredumpPath = createCoredump(".core.dump");
        Path crashPath = coredumpPath.getParent();
        Path processingPath = coredumpHandler.processCoredumps(crashPath, attributes);

        // The 'processing' directory should be empty
        assertFolderContents(processingPath);

        // The 'crash' directory should have 'processing' and the incomplete core dump in it
        assertFolderContents(crashPath, processingPath.getFileName().toString(), coredumpPath.getFileName().toString());
    }

    @Test
    public void startProcessingTest() throws IOException {
        Path coredumpPath = createCoredump("core.dump");
        Path crashPath = coredumpPath.getParent();
        Path processingPath = crashPath.resolve("processing_dir");
        coredumpHandler.startProcessing(coredumpPath, crashPath.resolve("processing_dir"));

        // Contents of 'crash' should be only the 'processing' directory
        assertFolderContents(crashPath, processingPath.getFileName().toString());

        // The 'processing' directory should have 1 directory inside for the core.dump we just created
        List<Path> processedCoredumps = Files.list(processingPath).collect(Collectors.toList());
        assertEquals(processedCoredumps.size(), 1);

        // Inside the coredump directory, there should be 1 file: core.dump
        assertFolderContents(processedCoredumps.get(0), coredumpPath.getFileName().toString());
    }

    @Test
    public void coredumpMetadataCollectAndWriteTest() throws IOException, InterruptedException {
        when(coreCollector.collect(any())).thenReturn(metadata);
        Path coredumpPath = createCoredump("core.dump");
        Path crashPath = coredumpPath.getParent();
        Path processingPath = coredumpHandler.processCoredumps(crashPath, attributes);

        // Inside 'processing' directory, there should be a new directory containing 'metadata.json' file
        List<Path> processedCoredumps = Files.list(processingPath).collect(Collectors.toList());
        String metadataFileContents = new String(Files.readAllBytes(
                processedCoredumps.get(0).resolve(CoredumpHandler.METADATA_FILE_NAME)));
        assertEquals(expectedMetadataFileContents, metadataFileContents);
    }

    @Test
    public void reportSuccessCoredumpTest() throws IOException, URISyntaxException, InterruptedException {
        final String documentId = "UIDD-ABCD-EFGH";
        Path coredumpPath = createProcessedCoredump(documentId);

        setNextHttpResponse(200, Optional.empty());
        coredumpHandler.report(coredumpPath.getParent());
        validateNextHttpPost(documentId, expectedMetadataFileContents);
    }

    @Test
    public void reportFailCoredumpTest() throws IOException, URISyntaxException {
        final String documentId = "UIDD-ABCD-EFGH";

        Path metadataPath = createProcessedCoredump(documentId);
        Path crashPath = metadataPath.getParent().getParent().getParent();
        Path donePath = folder.newFolder("done").toPath();

        setNextHttpResponse(500, Optional.of("Internal server error"));
        coredumpHandler.reportCoredumps(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME), donePath);
        validateNextHttpPost(documentId, expectedMetadataFileContents);

        // The coredump should not have been moved out of 'processing' and into 'done' as the report failed
        assertFolderContents(donePath);
        assertFolderContents(metadataPath.getParent(), CoredumpHandler.METADATA_FILE_NAME);
    }

    @Test
    public void finishProcessingTest() throws IOException {
        final String documentId = "UIDD-ABCD-EFGH";

        Path coredumpPath = createProcessedCoredump(documentId);
        Path crashPath = coredumpPath.getParent().getParent().getParent();
        Path donePath = folder.newFolder("done").toPath();

        coredumpHandler.finishProcessing(coredumpPath.getParent(), donePath);

        // The coredump should've been moved out of 'processing' and into 'done'
        assertFolderContents(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME));
        assertFolderContents(donePath.resolve(documentId), CoredumpHandler.METADATA_FILE_NAME);
    }


    private static void assertFolderContents(Path pathToFolder, String... filenames) throws IOException {
        Set<Path> expectedContentsOfFolder = Arrays.stream(filenames)
                .map(pathToFolder::resolve)
                .collect(Collectors.toSet());
        Set<Path> actualContentsOfFolder = Files.list(pathToFolder).collect(Collectors.toSet());
        assertEquals(expectedContentsOfFolder, actualContentsOfFolder);
    }

    private Path createCoredump(String coredumpName) throws IOException {
        Path crashPath = folder.newFolder("crash").toPath();
        Path coredumpPath = crashPath.resolve(coredumpName);
        coredumpPath.toFile().createNewFile();
        return coredumpPath;
    }

    private Path createProcessedCoredump(String documentId) throws IOException {
        Path crashPath = folder.newFolder("crash").toPath();
        Path coredumpPath = crashPath
                .resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME)
                .resolve(documentId)
                .resolve(CoredumpHandler.METADATA_FILE_NAME);
        coredumpPath.getParent().toFile().mkdirs();
        return Files.write(coredumpPath, expectedMetadataFileContents.getBytes());
    }

    private void setNextHttpResponse(int code, Optional<String> message) throws IOException {
        DefaultHttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
        HttpResponse httpResponse = responseFactory.newHttpResponse(
                new BasicStatusLine(HttpVersion.HTTP_1_1, code, null), null);
        if (message.isPresent()) httpResponse.setEntity(new StringEntity(message.get()));

        when(httpClient.execute(any())).thenReturn(httpResponse);
    }

    private void validateNextHttpPost(String documentId, String expectedBody) throws IOException, URISyntaxException {
        ArgumentCaptor<HttpPost> capturedPost = ArgumentCaptor.forClass(HttpPost.class);
        verify(httpClient).execute(capturedPost.capture());

        URI expectedURI = new URI(CoredumpHandler.FEED_ENDPOINT + "/" + documentId);
        assertEquals(expectedURI, capturedPost.getValue().getURI());
        assertEquals("application/json", capturedPost.getValue().getHeaders(HttpHeaders.CONTENT_TYPE)[0].getValue());
        assertEquals(expectedBody,
                new BufferedReader(new InputStreamReader(capturedPost.getValue().getEntity().getContent())).readLine());
    }
}

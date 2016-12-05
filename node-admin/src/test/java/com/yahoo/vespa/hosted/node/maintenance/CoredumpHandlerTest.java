// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintenance;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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

    @Rule
    public TemporaryFolder folder= new TemporaryFolder();

    @Test
    public void ignoresIncompleteCoredumps() throws IOException {
        Path crashPath = folder.newFolder("crash").toPath();
        Path coredumpPath = crashPath.resolve(".core.dump");
        coredumpPath.toFile().createNewFile();

        CoredumpHandler coredumpHandler = new CoredumpHandler(httpClient, coreCollector, attributes);
        coredumpHandler.processCoredumps(crashPath);

        // The 'processing' directory should be empty
        assertEquals(Files.list(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME)).count(), 0);

        // The 'crash' directory should have 'processing' and the incomplete core dump in it
        Set<Path> expectedContentsOfCrash = new HashSet<>(Arrays.asList(coredumpPath,
                crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME)));
        assertEquals(expectedContentsOfCrash, Files.list(crashPath).collect(Collectors.toSet()));
    }

    @Test
    public void testProcessCoredumps() throws IOException, InterruptedException {
        Path crashPath = folder.newFolder("crash").toPath();
        Path coredumpPath = crashPath.resolve("core.dump");
        coredumpPath.toFile().createNewFile();

        when(coreCollector.collect(any())).thenReturn(metadata);

        CoredumpHandler coredumpHandler = new CoredumpHandler(httpClient, coreCollector, attributes);
        coredumpHandler.processCoredumps(coredumpPath);

        // Contents of 'crash' should be only the 'processing' directory
        Set<Path> expectedContentsOfCrash = Collections.singleton(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME));
        assertEquals(expectedContentsOfCrash, Files.list(crashPath).collect(Collectors.toSet()));

        // The 'processing' directory should have 1 directory inside for the core.dump we just created
        assertEquals(Files.list(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME)).count(), 1);

        // Inside the coredump directory, there should be 2 files, the core.dump and the metadata.json
        Path processedCoredumpPath = Files.list(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME)).findFirst().get();
        Set<Path> expectedContentsOfCoredump = new HashSet<>(Arrays.asList(processedCoredumpPath.resolve("core.dump"),
                processedCoredumpPath.resolve(CoredumpHandler.METADATA_FILE_NAME)));
        assertEquals(expectedContentsOfCoredump, Files.list(processedCoredumpPath).collect(Collectors.toSet()));

        // Contents of metadata.json is just the result of CoreCollector.collect() + attributes
        String metadataFileContents = new String(Files.readAllBytes(
                processedCoredumpPath.resolve(CoredumpHandler.METADATA_FILE_NAME)));
        assertEquals(expectedMetadataFileContents, metadataFileContents);
    }

    @Test
    public void testReportCoredump() throws IOException {
        final String documentId = "UIDD-ABCD-EFGH";

        Path crashPath = folder.newFolder("crash").toPath();
        Path donePath = folder.newFolder("done").toPath();
        Path coredumpPath = crashPath
                .resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME)
                .resolve(documentId)
                .resolve(CoredumpHandler.METADATA_FILE_NAME);
        coredumpPath.getParent().toFile().mkdirs();
        Files.write(coredumpPath, expectedMetadataFileContents.getBytes());

        HttpPost post = new HttpPost(CoredumpHandler.FEED_ENDPOINT + "/" + documentId);
        post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        post.setEntity(new StringEntity(expectedMetadataFileContents));

        DefaultHttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
        HttpResponse httpResponse = responseFactory.newHttpResponse(
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, null), null);
        when(httpClient.execute(any())).thenReturn(httpResponse);

        CoredumpHandler coredumpHandler = new CoredumpHandler(httpClient, coreCollector, attributes);
        coredumpHandler.reportCoredumps(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME), donePath);

        ArgumentCaptor<HttpPost> capturedPost = ArgumentCaptor.forClass(HttpPost.class);
        verify(httpClient).execute(capturedPost.capture());
        assertEquals("application/json", capturedPost.getValue().getHeaders(HttpHeaders.CONTENT_TYPE)[0].getValue());

        assertEquals(expectedMetadataFileContents,
                new BufferedReader(new InputStreamReader(capturedPost.getValue().getEntity().getContent())).readLine());

        // The coredump should've been moved out of 'processing' and into 'done'
        assertEquals(Files.list(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME)).count(), 0);
        assertTrue(Files.exists(donePath
                .resolve(documentId)
                .resolve(CoredumpHandler.METADATA_FILE_NAME)));
    }


    @Test
    public void testReportCoredumpFailToReport() throws IOException {
        final String documentId = "UIDD-ABCD-EFGH";

        Path crashPath = folder.newFolder("crash").toPath();
        Path donePath = folder.newFolder("done").toPath();
        Path coredumpPath = crashPath
                .resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME)
                .resolve(documentId)
                .resolve(CoredumpHandler.METADATA_FILE_NAME);
        coredumpPath.getParent().toFile().mkdirs();
        Files.write(coredumpPath, expectedMetadataFileContents.getBytes());

        HttpPost post = new HttpPost(CoredumpHandler.FEED_ENDPOINT + "/" + documentId);
        post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        post.setEntity(new StringEntity(expectedMetadataFileContents));

        DefaultHttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
        HttpResponse httpResponse = responseFactory.newHttpResponse(
                new BasicStatusLine(HttpVersion.HTTP_1_1, 500, null), new BasicHttpContext());
        httpResponse.setEntity(new StringEntity("Internal server error"));
        when(httpClient.execute(any())).thenReturn(httpResponse);

        CoredumpHandler coredumpHandler = new CoredumpHandler(httpClient, coreCollector, attributes);
        coredumpHandler.reportCoredumps(crashPath.resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME), donePath);

        ArgumentCaptor<HttpPost> capturedPost = ArgumentCaptor.forClass(HttpPost.class);
        verify(httpClient).execute(capturedPost.capture());
        assertEquals("application/json", capturedPost.getValue().getHeaders(HttpHeaders.CONTENT_TYPE)[0].getValue());

        assertEquals(expectedMetadataFileContents,
                new BufferedReader(new InputStreamReader(capturedPost.getValue().getEntity().getContent())).readLine());

        // The coredump should not have been moved out of 'processing' and into 'done' as the report failed
        assertEquals(Files.list(crashPath.resolve(donePath)).count(), 0);
        assertTrue(Files.exists(crashPath
                .resolve(CoredumpHandler.PROCESSING_DIRECTORY_NAME)
                .resolve(documentId)
                .resolve(CoredumpHandler.METADATA_FILE_NAME)));
    }
}

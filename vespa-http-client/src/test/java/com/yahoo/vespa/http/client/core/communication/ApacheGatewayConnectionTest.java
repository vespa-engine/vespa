// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.TestUtils;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespa.http.client.core.ServerResponseException;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHeader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class ApacheGatewayConnectionTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testProtocolV3() throws Exception {
        final Endpoint endpoint = Endpoint.create("hostname", 666, false);
        final FeedParams feedParams = new FeedParams.Builder().setDataFormat(FeedParams.DataFormat.JSON_UTF8).build();
        final String clusterSpecificRoute = "";
        final ConnectionParams connectionParams = new ConnectionParams.Builder()
                .setEnableV3Protocol(true)
                .build();
        final List<Document> documents = new ArrayList<>();

        final String vespaDocContent ="Hello, I a JSON doc.";
        final String docId = "42";

        final AtomicInteger requestsReceived = new AtomicInteger(0);
        // This is the fake server, takes header client ID and uses this as session Id.
        ApacheGatewayConnection.HttpClientFactory mockFactory = mockHttpClientFactory(post -> {
            final Header clientIdHeader = post.getFirstHeader(Headers.CLIENT_ID);
            return httpResponse(clientIdHeader.getValue(), "3");
        });

        ApacheGatewayConnection apacheGatewayConnection =
                new ApacheGatewayConnection(
                        endpoint,
                        feedParams,
                        clusterSpecificRoute,
                        connectionParams,
                        mockFactory,
                        "clientId");
        apacheGatewayConnection.connect();
        apacheGatewayConnection.handshake();
        documents.add(createDoc(docId, vespaDocContent, true));

        apacheGatewayConnection.writeOperations(documents);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testServerReturnsBadSessionInV3() throws Exception {
        final Endpoint endpoint = Endpoint.create("hostname", 666, false);
        final FeedParams feedParams = new FeedParams.Builder().setDataFormat(FeedParams.DataFormat.JSON_UTF8).build();
        final String clusterSpecificRoute = "";
        final ConnectionParams connectionParams = new ConnectionParams.Builder()
                .setEnableV3Protocol(true)
                .build();

        // This is the fake server, returns wrong session Id.
        ApacheGatewayConnection.HttpClientFactory mockFactory = mockHttpClientFactory(post -> httpResponse("Wrong Id from server", "3"));

        ApacheGatewayConnection apacheGatewayConnection =
                new ApacheGatewayConnection(
                        endpoint,
                        feedParams,
                        clusterSpecificRoute,
                        connectionParams,
                        mockFactory,
                        "clientId");
        apacheGatewayConnection.connect();
        final List<Document> documents = new ArrayList<>();
        apacheGatewayConnection.writeOperations(documents);
    }

    @Test(expected=RuntimeException.class)
    public void testBadConfigParameters() throws Exception {
            final Endpoint endpoint = Endpoint.create("hostname", 666, false);
        final FeedParams feedParams = new FeedParams.Builder().setDataFormat(FeedParams.DataFormat.JSON_UTF8).build();
        final String clusterSpecificRoute = "";
        final ConnectionParams connectionParams = new ConnectionParams.Builder()
                .setEnableV3Protocol(true)
                .build();

        final ApacheGatewayConnection.HttpClientFactory mockFactory =
                mock(ApacheGatewayConnection.HttpClientFactory.class);

        new ApacheGatewayConnection(
                endpoint,
                feedParams,
                clusterSpecificRoute,
                connectionParams,
                mockFactory,
                null);
    }

    @Test
    public void testJsonDocumentHeader() throws Exception {
        final Endpoint endpoint = Endpoint.create("hostname", 666, false);
        final FeedParams feedParams = new FeedParams.Builder().setDataFormat(FeedParams.DataFormat.JSON_UTF8).build();
        final String clusterSpecificRoute = "";
        final ConnectionParams connectionParams = new ConnectionParams.Builder()
                .setUseCompression(true)
                .build();
        final List<Document> documents = new ArrayList<>();

        final String vespaDocContent ="Hello, I a JSON doc.";
        final String docId = "42";

        final AtomicInteger requestsReceived = new AtomicInteger(0);

        // This is the fake server, checks that DATA_FORMAT header is set properly.
        ApacheGatewayConnection.HttpClientFactory mockFactory = mockHttpClientFactory(post -> {
            final Header header = post.getFirstHeader(Headers.DATA_FORMAT);
            if (requestsReceived.incrementAndGet() == 1) {
                // This is handshake, it is not json.
                assert (header == null);
                return httpResponse("clientId", "3");
            }
            assertNotNull(header);
            assertThat(header.getValue(), is(FeedParams.DataFormat.JSON_UTF8.name()));
            // Test is done.
            return httpResponse("clientId", "3");
        });

        ApacheGatewayConnection apacheGatewayConnection =
                new ApacheGatewayConnection(
                        endpoint,
                        feedParams,
                        clusterSpecificRoute,
                        connectionParams,
                        mockFactory,
                        "clientId");
        apacheGatewayConnection.connect();
        apacheGatewayConnection.handshake();

        documents.add(createDoc(docId, vespaDocContent, true));

        apacheGatewayConnection.writeOperations(documents);
    }

    @Test
    public void testZipAndCreateEntity() throws IOException {
        final String testString = "Hello world";
        InputStream stream = new ByteArrayInputStream(testString.getBytes(StandardCharsets.UTF_8));
        // Send in test data to method.
        InputStreamEntity inputStreamEntity = ApacheGatewayConnection.zipAndCreateEntity(stream);
        // Verify zipped data by comparing unzipped data with test data.
        final String rawContent = TestUtils.zipStreamToString(inputStreamEntity.getContent());
        assert(testString.equals(rawContent));
    }

    /**
     *  Mocks the HttpClient, and verifies that the compressed data is sent.
     */
    @Test
    public void testCompressedWriteOperations() throws Exception {
        final Endpoint endpoint = Endpoint.create("hostname", 666, false);
        final FeedParams feedParams = new FeedParams.Builder().build();
        final String clusterSpecificRoute = "";
        final ConnectionParams connectionParams = new ConnectionParams.Builder()
                .setUseCompression(true)
                .build();
        final List<Document> documents = new ArrayList<>();

        final String vespaDocContent ="Hello, I am the document data.";
        final String docId = "42";

        final Document doc = createDoc(docId, vespaDocContent, false);

        // When sending data on http client, check if it is compressed. If compressed, unzip, check result,
        // and count down latch.
        ApacheGatewayConnection.HttpClientFactory mockFactory = mockHttpClientFactory(post -> {
            final Header header = post.getFirstHeader("Content-Encoding");
            if (header != null && header.getValue().equals("gzip")) {
                final String rawContent = TestUtils.zipStreamToString(post.getEntity().getContent());
                final String vespaHeaderText = "<vespafeed>\n";
                final String vespaFooterText = "</vespafeed>\n";

                assertThat(rawContent, is(
                        doc.getOperationId() + " 38\n" + vespaHeaderText + vespaDocContent + "\n"
                                + vespaFooterText));

            }
            return httpResponse("clientId", "3");
        });

        StatusLine statusLineMock = mock(StatusLine.class);
        when(statusLineMock.getStatusCode()).thenReturn(200);

        ApacheGatewayConnection apacheGatewayConnection =
                new ApacheGatewayConnection(
                        endpoint,
                        feedParams,
                        clusterSpecificRoute,
                        connectionParams,
                        mockFactory,
                        "clientId");
        apacheGatewayConnection.connect();
        apacheGatewayConnection.handshake();

        documents.add(doc);

        apacheGatewayConnection.writeOperations(documents);
    }

    @Test
    public void dynamic_headers_are_added_to_the_response() throws IOException, ServerResponseException, InterruptedException {
        ConnectionParams.HeaderProvider headerProvider = mock(ConnectionParams.HeaderProvider.class);
        when(headerProvider.getHeaderValue())
                .thenReturn("v1")
                .thenReturn("v2")
                .thenReturn("v3");

        ConnectionParams connectionParams = new ConnectionParams.Builder()
                .addDynamicHeader("foo", headerProvider)
                .build();

        AtomicInteger counter = new AtomicInteger(1);
        ApacheGatewayConnection.HttpClientFactory mockFactory = mockHttpClientFactory(post  -> {
            Header[] fooHeader = post.getHeaders("foo");
            assertEquals(1, fooHeader.length);
            assertEquals("foo", fooHeader[0].getName());
            assertEquals("v" + counter.getAndIncrement(), fooHeader[0].getValue());
            return httpResponse("clientId", "3");

        });

        ApacheGatewayConnection apacheGatewayConnection =
            new ApacheGatewayConnection(
                    Endpoint.create("hostname", 666, false),
                    new FeedParams.Builder().build(),
                    "",
                    connectionParams,
                    mockFactory,
                    "clientId");
        apacheGatewayConnection.connect();
        apacheGatewayConnection.handshake();

        List<Document> documents = new ArrayList<>();
        documents.add(createDoc("42", "content", true));
        apacheGatewayConnection.writeOperations(documents);
        apacheGatewayConnection.writeOperations(documents);

        verify(headerProvider, times(3)).getHeaderValue(); // 1x connect(), 2x writeOperations()
    }

    @Test
    public void detailed_error_message_is_extracted_from_error_responses_with_json() throws IOException, ServerResponseException {
        String reasonPhrase = "Unauthorized";
        String errorMessage = "Invalid credentials";
        expectedException.expect(ServerResponseException.class);
        expectedException.expectMessage(reasonPhrase + " - " + errorMessage);

        ApacheGatewayConnection.HttpClientFactory mockFactory = mockHttpClientFactory(post  -> createErrorHttpResponse(401, reasonPhrase, errorMessage));

        ApacheGatewayConnection apacheGatewayConnection =
            new ApacheGatewayConnection(
                    Endpoint.create("hostname", 666, false),
                    new FeedParams.Builder().build(),
                    "",
                    new ConnectionParams.Builder().build(),
                    mockFactory,
                    "clientId");
        apacheGatewayConnection.connect();
        apacheGatewayConnection.handshake();

        apacheGatewayConnection.writeOperations(Collections.singletonList(createDoc("42", "content", true)));
    }

    private static ApacheGatewayConnection.HttpClientFactory mockHttpClientFactory(HttpExecuteMock httpExecuteMock) throws IOException {
        ApacheGatewayConnection.HttpClientFactory mockFactory =
                mock(ApacheGatewayConnection.HttpClientFactory.class);
        HttpClient httpClientMock = mock(HttpClient.class);
        when(mockFactory.createClient()).thenReturn(httpClientMock);
        stub(httpClientMock.execute(any())).toAnswer((Answer) invocation -> {
            Object[] args = invocation.getArguments();
            HttpPost post = (HttpPost) args[0];
            return httpExecuteMock.execute(post);
        });
        return mockFactory;
    }

    @FunctionalInterface private interface HttpExecuteMock {
        HttpResponse execute(HttpPost httpPost) throws IOException;
    }

    private Document createDoc(final String docId, final String content, boolean useJson) throws IOException {
        return new Document(docId, content.getBytes(), null /* context */);
    }

    private void addMockedHeader(
            final HttpResponse httpResponseMock,
            final String name,
            final String value,
            HeaderElement[] elements) {
        final Header header = new Header() {
            @Override
            public String getName() {
                return name;
            }
            @Override
            public String getValue() {
                return value;
            }
            @Override
            public HeaderElement[] getElements() throws ParseException {
                return elements;
            }
        };
        when(httpResponseMock.getFirstHeader(name)).thenReturn(header);
    }

    private HttpResponse httpResponse(String sessionIdInResult, String version) throws IOException {
        final HttpResponse httpResponseMock = mock(HttpResponse.class);

        StatusLine statusLineMock = mock(StatusLine.class);
        when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);
        when(statusLineMock.getStatusCode()).thenReturn(200);

        addMockedHeader(httpResponseMock, Headers.SESSION_ID, sessionIdInResult, null);
        addMockedHeader(httpResponseMock, Headers.VERSION, version, null);
        HeaderElement[] headerElements = new HeaderElement[1];
        headerElements[0] = mock(HeaderElement.class);

        final HttpEntity httpEntityMock = mock(HttpEntity.class);
        when(httpResponseMock.getEntity()).thenReturn(httpEntityMock);

        final InputStream inputs = new ByteArrayInputStream("fake response data".getBytes());

        when(httpEntityMock.getContent()).thenReturn(inputs);
        return httpResponseMock;
    }

    private static HttpResponse createErrorHttpResponse(int statusCode, String reasonPhrase, String message) throws IOException {
        HttpResponse response = mock(HttpResponse.class);

        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(statusCode);
        when(statusLine.getReasonPhrase()).thenReturn(reasonPhrase);
        when(response.getStatusLine()).thenReturn(statusLine);

        HttpEntity httpEntity = mock(HttpEntity.class);
        when(httpEntity.getContentType()).thenReturn(new BasicHeader("Content-Type", "application/json"));
        String json = String.format("{\"message\": \"%s\"}", message);
        when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        when(response.getEntity()).thenReturn(httpEntity);
        return response;
    }
}

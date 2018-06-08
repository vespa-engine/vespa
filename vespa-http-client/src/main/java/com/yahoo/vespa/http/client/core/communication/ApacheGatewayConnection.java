// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.component.Vtag;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.Encoder;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespa.http.client.core.ServerResponseException;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * @author Einar M R Rosenvinge
 */
class ApacheGatewayConnection implements GatewayConnection {

    private static Logger log = Logger.getLogger(ApacheGatewayConnection.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String PATH = "/reserved-for-internal-use/feedapi?";
    private final List<Integer> SUPPORTED_VERSIONS = new ArrayList<>();
    private static final byte[] START_OF_FEED_XML = "<vespafeed>\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] END_OF_FEED_XML = "\n</vespafeed>\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] START_OF_FEED_JSON = "[".getBytes(StandardCharsets.UTF_8);
    private static final byte[] END_OF_FEED_JSON = "]".getBytes(StandardCharsets.UTF_8);
    private final byte[] startOfFeed;
    private final byte[] endOfFeed;
    private final Endpoint endpoint;
    private final FeedParams feedParams;
    private final String clusterSpecificRoute;
    private final ConnectionParams connectionParams;
    private HttpClient httpClient;
    private String sessionId;
    private final String clientId;
    private int negotiatedVersion = -1;
    private final HttpClientFactory httpClientFactory;
    private final String shardingKey = UUID.randomUUID().toString().substring(0, 5);

    ApacheGatewayConnection(
            Endpoint endpoint,
            FeedParams feedParams,
            String clusterSpecificRoute,
            ConnectionParams connectionParams,
            HttpClientFactory httpClientFactory,
            String clientId) {
        SUPPORTED_VERSIONS.add(2);
        this.endpoint = endpoint;
        this.feedParams = feedParams;
        this.clusterSpecificRoute = clusterSpecificRoute;
        this.httpClientFactory = httpClientFactory;
        this.connectionParams = connectionParams;
        this.httpClient = null;
        boolean isJson = feedParams.getDataFormat() == FeedParams.DataFormat.JSON_UTF8;
        if (isJson) {
            startOfFeed = START_OF_FEED_JSON;
            endOfFeed = END_OF_FEED_JSON;
        } else {
            startOfFeed = START_OF_FEED_XML;
            endOfFeed = END_OF_FEED_XML;
        }
        this.clientId = clientId;
        if (connectionParams.isEnableV3Protocol()) {
            if (this.clientId == null) {
                throw new RuntimeException("Set to support version 3, but got no client Id.");
            }
            SUPPORTED_VERSIONS.add(3);
        }
    }

    @Override
    public InputStream writeOperations(List<Document> docs) throws ServerResponseException, IOException {
        return write(docs, false, connectionParams.getUseCompression());
    }

    @Override
    public InputStream drain() throws ServerResponseException, IOException {
        return write(Collections.<Document>emptyList(), true /* drain */, false /* use compression */);
    }

    @Override
    public boolean connect() {
        log.fine("Attempting to connect to " + endpoint);
        if (httpClient != null) {
            log.log(Level.WARNING, "Previous httpClient still exists.");
        }
        httpClient = httpClientFactory.createClient();
        return httpClient != null;
    }

    // Protected for easier testing only.
    protected static InputStreamEntity zipAndCreateEntity(final InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4096];
        GZIPOutputStream gzos = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            gzos = new GZIPOutputStream(baos);
            while (inputStream.available() > 0) {
                int length = inputStream.read(buffer);
                gzos.write(buffer, 0,length);
            }
        } finally {
            if (gzos != null)  {
                gzos.close();
            }
        }
        byte[] fooGzippedBytes = baos.toByteArray();
        return new InputStreamEntity(new ByteArrayInputStream(fooGzippedBytes), -1);
    }

    private InputStream write(List<Document> docs, boolean drain, boolean useCompression)
            throws ServerResponseException, IOException {
        HttpPost httpPost = createPost(drain, useCompression, false /* this is not hanshake */);

        final ByteBuffer[] buffers = getDataWithStartAndEndOfFeed(docs, negotiatedVersion);
        final InputStream inputStream = new ByteBufferInputStream(buffers);
        final InputStreamEntity reqEntity;
        if (useCompression ) {
            reqEntity = zipAndCreateEntity(inputStream);
        } else {
            reqEntity = new InputStreamEntity(inputStream, -1);
        }
        reqEntity.setChunked(true);
        httpPost.setEntity(reqEntity);
        return executePost(httpPost);
    }

    private ByteBuffer[] getDataWithStartAndEndOfFeed(List<Document> docs, int version) {
        List<ByteBuffer> data = new ArrayList<ByteBuffer>();
        if (version == 2 || version == 3) {
            for (Document doc : docs) {
                int operationSize = doc.size() + startOfFeed.length + endOfFeed.length;
                StringBuilder envelope = new StringBuilder();
                Encoder.encode(doc.getOperationId(), envelope);
                envelope.append(' ');
                envelope.append(Integer.toHexString(operationSize));
                envelope.append('\n');
                data.add(StandardCharsets.US_ASCII.encode(envelope.toString()));
                data.add(ByteBuffer.wrap(startOfFeed));
                data.add(doc.getData());
                data.add(ByteBuffer.wrap(endOfFeed));
            }
        } else {
            throw new IllegalArgumentException("Protocol version " + version + " unsupported by client.");
        }
        return data.toArray(new ByteBuffer[data.size()]);
    }

    private HttpPost createPost(boolean drain, boolean useCompression, boolean isHandshake) {
        HttpPost httpPost = new HttpPost(createUri());

        for (int v : SUPPORTED_VERSIONS) {
            httpPost.addHeader(Headers.VERSION, "" + v);
        }
        if (sessionId != null) {
            httpPost.setHeader(Headers.SESSION_ID, sessionId);
        }
        if (clientId != null) {
            httpPost.setHeader(Headers.CLIENT_ID, clientId);
        }
        httpPost.setHeader(Headers.SHARDING_KEY, shardingKey);
        if (drain) {
            httpPost.setHeader(Headers.DRAIN, "true");
        } else {
            httpPost.setHeader(Headers.DRAIN, "false");
        }
        if (clusterSpecificRoute != null) {
            httpPost.setHeader(Headers.ROUTE, feedParams.getRoute());
        } else {
            if (feedParams.getRoute() != null) {
                httpPost.setHeader(Headers.ROUTE, feedParams.getRoute());
            }
        }
        if (!isHandshake) {
            if (feedParams.getDataFormat() == FeedParams.DataFormat.JSON_UTF8) {
                httpPost.setHeader(Headers.DATA_FORMAT, FeedParams.DataFormat.JSON_UTF8.name());
            } else {
                httpPost.setHeader(Headers.DATA_FORMAT, FeedParams.DataFormat.XML_UTF8.name());
            }
            if (feedParams.getPriority() != null) {
                httpPost.setHeader(Headers.PRIORITY, feedParams.getPriority());
            }
            if (connectionParams.getTraceLevel() != 0) {
                httpPost.setHeader(Headers.TRACE_LEVEL, String.valueOf(connectionParams.getTraceLevel()));
            }
            if (negotiatedVersion == 3 && feedParams.getDenyIfBusyV3()) {
                httpPost.setHeader(Headers.DENY_IF_BUSY, "true");
            }
        }
        if (feedParams.getSilentUpgrade()) {
            httpPost.setHeader(Headers.SILENTUPGRADE, "true");
        }
        httpPost.setHeader(Headers.TIMEOUT, "" + feedParams.getServerTimeout(TimeUnit.SECONDS));

        for (Map.Entry<String, String> extraHeader : connectionParams.getHeaders()) {
            httpPost.addHeader(extraHeader.getKey(), extraHeader.getValue());
        }
        connectionParams.getDynamicHeaders().forEach((headerName, provider) -> {
            String headerValue = Objects.requireNonNull(
                    provider.getHeaderValue(),
                    provider.getClass().getName() + ".getHeader() returned null as header value!");
            httpPost.addHeader(headerName, headerValue);
        });

        if (useCompression) {
            httpPost.setHeader("Content-Encoding", "gzip");
        }
        return httpPost;
    }

    private InputStream executePost(HttpPost httpPost) throws ServerResponseException, IOException {
        HttpResponse response;
        try {
            if (httpClient == null) {
                throw new IOException("Trying to executePost while not having a connection/http client");
            }
            response = httpClient.execute(httpPost);
        } catch (IOException e) {
            httpPost.abort();
            throw e;
        } catch (Exception e) {
            httpPost.abort();
            throw e;
        }
        try {
            verifyServerResponseCode(response);
            verifyServerVersion(response.getFirstHeader(Headers.VERSION));
            verifySessionHeader(response.getFirstHeader(Headers.SESSION_ID));
        } catch (ServerResponseException e) {
            httpPost.abort();
            throw e;
        }
        return response.getEntity().getContent();
    }

    private void verifyServerResponseCode(HttpResponse response) throws ServerResponseException {
        StatusLine statusLine = response.getStatusLine();
        // We use code 261-299 to report errors related to internal transitive errors that the tenants should not care
        // about to avoid masking more serious errors.
        int statusCode = statusLine.getStatusCode();
        if (statusCode > 199 && statusCode < 260) {
            return;
        }
        if (statusCode == 299) {
            throw new ServerResponseException(429, "Too  many requests.");
        }
        String message = tryGetDetailedErrorMessage(response)
                .orElseGet(statusLine::getReasonPhrase);
        throw new ServerResponseException(statusLine.getStatusCode(), message);
    }

    private static Optional<String> tryGetDetailedErrorMessage(HttpResponse response) {
        Header contentType = response.getEntity().getContentType();
        if (contentType == null || !contentType.getValue().equalsIgnoreCase("application/json")) return Optional.empty();
        try (InputStream in = response.getEntity().getContent()) {
            JsonNode jsonNode = mapper.readTree(in);
            JsonNode message = jsonNode.get("message");
            if (message == null || message.textValue() == null) return Optional.empty();
            return Optional.of(response.getStatusLine().getReasonPhrase() + " - " + message.textValue());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void verifySessionHeader(Header serverHeader) throws ServerResponseException {
        if (serverHeader == null) {
            throw new ServerResponseException("Got no session ID from server.");
        }
        final String serverHeaderVal = serverHeader.getValue().trim();
        if (negotiatedVersion == 3) {
            if (clientId == null || !clientId.equals(serverHeaderVal)) {
                String message = "Running using v3. However, server responds with different session " +
                        "than client has set; " + serverHeaderVal + " vs client code " + clientId;
                log.severe(message);
                throw new ServerResponseException(message);
            }
            return;
        }
        if (sessionId == null) { //this must be the first request
            log.finer("Got session ID from server: " + serverHeaderVal);
            this.sessionId = serverHeaderVal;
            return;
        } else {
            if (!sessionId.equals(serverHeaderVal)) {
                log.info("Request has been routed to a server which does not recognize the client session."
                        + " Most likely cause is upgrading of cluster, transitive error.");
                throw new ServerResponseException(
                        "Session ID received from server ('" + serverHeaderVal
                        + "') does not match cached session ID ('" + sessionId + "')");
            }
        }
    }

    private void verifyServerVersion(Header serverHeader) throws ServerResponseException {
        if (serverHeader == null) {
            throw new ServerResponseException("Got bad protocol version from server.");
        }
        int serverVersion;
        try {
            serverVersion = Integer.parseInt(serverHeader.getValue());
        } catch (NumberFormatException nfe) {
            throw new ServerResponseException("Got bad protocol version from server: " + nfe.getMessage());
        }
        if (!SUPPORTED_VERSIONS.contains(serverVersion)) {
            throw new ServerResponseException("Unsupported version: " + serverVersion
                    + ". Supported versions: " + SUPPORTED_VERSIONS);
        }
        if (negotiatedVersion == -1) {
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "Server decided upon protocol version " + serverVersion + ".");
            }
        }
        if (this.connectionParams.isEnableV3Protocol() && serverVersion != 3) {
            throw new ServerResponseException("Client was set up to use v3 of protocol, however, gateway wants to " +
                    "use version " + serverVersion + ". Already set up structures for v3 so can not do v2 now.");
        }
        this.negotiatedVersion = serverVersion;
    }

    private String createUri() {
        StringBuilder u = new StringBuilder();
        u.append(endpoint.isUseSsl() ? "https://" : "http://");
        u.append(endpoint.getHostname());
        u.append(":").append(endpoint.getPort());
        u.append(PATH);
        u.append(feedParams.toUriParameters());
        return u.toString();
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public void handshake() throws ServerResponseException, IOException {
        final boolean useCompression = false;
        final boolean drain = false;
        final boolean handshake = true;
        HttpPost httpPost = createPost(drain, useCompression, handshake);

        final String oldSessionID = sessionId;
        sessionId = null;
        try (InputStream stream = executePost(httpPost)) {
            if (oldSessionID != null && !oldSessionID.equals(sessionId)) {
                throw new ServerResponseException(
                        "Session ID changed after new handshake, some documents might not be acked to correct thread. "
                                + getEndpoint() + " old " + oldSessionID + " new " + sessionId);
            }
            if (stream == null) {
                log.fine("Stream is null.");
            }
            log.fine("Got session ID " + sessionId);
        }
    }

    @Override
    public void close() {
        httpClient = null;
    }

    /**
     * On re-connect we want to recreate the connection, hence we need a factory.
     */
    public static class HttpClientFactory {

        final ConnectionParams connectionParams;
        final boolean useSsl;

        public HttpClientFactory(final ConnectionParams connectionParams, final boolean useSsl) {
            this.connectionParams = connectionParams;
            this.useSsl = useSsl;
        }

        public HttpClient createClient() {
            HttpClientBuilder clientBuilder = HttpClientBuilder.create();
            if (useSsl && connectionParams.getSslContext() != null) {
                Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("https", new SSLConnectionSocketFactory(
                                connectionParams.getSslContext(), connectionParams.getHostnameVerifier()))
                        .register("http", PlainConnectionSocketFactory.INSTANCE)
                        .build();
                PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
                clientBuilder.setConnectionManager(connMgr);

            }
            clientBuilder.setUserAgent(String.format("vespa-http-client (%s)", Vtag.currentVersion));
            clientBuilder.setMaxConnPerRoute(1);
            clientBuilder.setMaxConnTotal(1);
            clientBuilder.disableContentCompression();
            // Try to disable the disabling to see if system tests become stable again.
            // clientBuilder.disableAutomaticRetries();
            clientBuilder.setConnectionTimeToLive(15, TimeUnit.SECONDS);
            {
                RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
                requestConfigBuilder.setSocketTimeout(0);
                if (connectionParams.getProxyHost() != null) {
                    requestConfigBuilder.setProxy(new HttpHost(connectionParams.getProxyHost(), connectionParams.getProxyPort()));
                }
                clientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());
            }

            log.fine("Creating HttpClient: " + " ConnectionTimeout "
                            + " SocketTimeout 0 secs "
                            + " proxyhost (can be null) " + connectionParams.getProxyHost()
                            + ":" + connectionParams.getProxyPort()
                            + (useSsl ? " using ssl " : " not using ssl")
            );
            return clientBuilder.build();
        }
    }

}

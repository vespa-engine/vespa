// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.athenz.identity.ServiceIdentitySslSocketFactory;
import com.yahoo.vespa.athenz.identity.SiaIdentityProvider;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.hosted.node.admin.component.ConfigServerInfo;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.net.ssl.HostnameVerifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singleton;

/**
 * Retries request on config server a few times before giving up. Assumes that all requests should be sent with
 * content-type application/json
 *
 * @author dybdahl
 * @author bjorncs
 */
public class ConfigServerApiImpl implements ConfigServerApi {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(ConfigServerApiImpl.class);

    private final ObjectMapper mapper = new ObjectMapper();

    private final List<URI> configServers;

    private final CloseableHttpClient client;

    public static ConfigServerApiImpl create(ConfigServerInfo info, SiaIdentityProvider provider) {
        return new ConfigServerApiImpl(
                info.getConfigServerUris(),
                new AthenzIdentityVerifier(singleton(info.getConfigServerIdentity())),
                provider);
    }

    public static ConfigServerApiImpl createFor(ConfigServerInfo info,
                                                SiaIdentityProvider provider,
                                                HostName configServerHostname) {
        return new ConfigServerApiImpl(
                Collections.singleton(info.getConfigServerUri(configServerHostname.value())),
                new AthenzIdentityVerifier(singleton(info.getConfigServerIdentity())),
                provider);
    }

    private ConfigServerApiImpl(Collection<URI> configServers,
                                HostnameVerifier verifier,
                                SiaIdentityProvider identityProvider) {
        this(configServers, createClient(new SSLConnectionSocketFactory(new ServiceIdentitySslSocketFactory(identityProvider), verifier)));
    }

    private ConfigServerApiImpl(Collection<URI> configServers, CloseableHttpClient client) {
        this.configServers = randomizeConfigServerUris(configServers);
        this.client = client;
    }

    public static ConfigServerApiImpl createForTesting(List<URI> configServerHosts) {
        return new ConfigServerApiImpl(configServerHosts, createClient(SSLConnectionSocketFactory.getSocketFactory()));
    }

    static ConfigServerApiImpl createForTestingWithClient(List<URI> configServerHosts,
                                                          CloseableHttpClient client) {
        return new ConfigServerApiImpl(configServerHosts, client);
    }

    interface CreateRequest {
        HttpUriRequest createRequest(URI configServerUri) throws JsonProcessingException, UnsupportedEncodingException;
    }

    private <T> T tryAllConfigServers(CreateRequest requestFactory, Class<T> wantedReturnType) {
        Exception lastException = null;
        for (URI configServer : configServers) {
            try (CloseableHttpResponse response = client.execute(requestFactory.createRequest(configServer))) {
                HttpException.handleStatusCode(
                        response.getStatusLine().getStatusCode(), "Config server " + configServer);

                try {
                    return mapper.readValue(response.getEntity().getContent(), wantedReturnType);
                } catch (IOException e) {
                    throw new RuntimeException("Failed parse response from config server", e);
                }
            } catch (HttpException e) {
                if (!e.isRetryable()) throw e;
                lastException = e;
            } catch (Exception e) {
                // Failure to communicate with a config server is not abnormal during upgrades
                if (e.getMessage().contains("(Connection refused)")) {
                    NODE_ADMIN_LOGGER.info("Connection refused to " + configServer + " (upgrading?), will try next");
                } else {
                    NODE_ADMIN_LOGGER.warning("Failed to communicate with " + configServer + ", will try next: " + e.getMessage());
                }
                lastException = e;
            }
        }

        throw new RuntimeException("All requests against the config servers ("
                + configServers + ") failed, last as follows:", lastException);
    }

    @Override
    public <T> T put(String path, Optional<Object> bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPut put = new HttpPut(configServer.resolve(path));
            setContentTypeToApplicationJson(put);
            if (bodyJsonPojo.isPresent()) {
                put.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo.get())));
            }
            return put;
        }, wantedReturnType);
    }

    @Override
    public <T> T patch(String path, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPatch patch = new HttpPatch(configServer.resolve(path));
            setContentTypeToApplicationJson(patch);
            patch.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo)));
            return patch;
        }, wantedReturnType);
    }

    @Override
    public <T> T delete(String path, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer ->
                new HttpDelete(configServer.resolve(path)), wantedReturnType);
    }

    @Override
    public <T> T get(String path, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer ->
                new HttpGet(configServer.resolve(path)), wantedReturnType);
    }

    @Override
    public <T> T post(String path, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPost post = new HttpPost(configServer.resolve(path));
            setContentTypeToApplicationJson(post);
            post.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo)));
            return post;
        }, wantedReturnType);
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void setContentTypeToApplicationJson(HttpRequestBase request) {
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    }

    private static CloseableHttpClient createClient(SSLConnectionSocketFactory socketFactory) {
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", socketFactory)
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        cm.setMaxTotal(200); // Increase max total connections to 200, which should be enough

        // Have experienced hang in socket read, which may have been because of
        // system defaults, therefore set explicit timeouts. Set arbitrarily to
        // 15s > 10s used by Orchestrator lock timeout.
        int timeoutMs = 15_000;
        RequestConfig requestBuilder = RequestConfig.custom()
                .setConnectTimeout(timeoutMs) // establishment of connection
                .setConnectionRequestTimeout(timeoutMs) // connection from connection manager
                .setSocketTimeout(timeoutMs) // waiting for data
                .build();

        return HttpClientBuilder.create()
                .setDefaultRequestConfig(requestBuilder)
                .disableAutomaticRetries()
                .setUserAgent("node-admin")
                .setConnectionManager(cm)
                .build();
    }

    // Shuffle config server URIs to balance load
    private static List<URI> randomizeConfigServerUris(Collection<URI> configServerUris) {
        List<URI> shuffledConfigServerHosts = new ArrayList<>(configServerUris);
        Collections.shuffle(shuffledConfigServerHosts);
        return shuffledConfigServerHosts;
    }
}

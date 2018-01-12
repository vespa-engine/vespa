// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.concurrent.ThreadFactoryFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.ssl.SSLContextBuilder;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Retries request on config server a few times before giving up. Assumes that all requests should be sent with
 * content-type application/json
 *
 * @author dybdahl
 */
public class ConfigServerHttpRequestExecutor implements AutoCloseable {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(ConfigServerHttpRequestExecutor.class);
    private static final Duration CLIENT_REFRESH_INTERVAL = Duration.ofHours(1);

    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService clientRefresherScheduler =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory("http-client-refresher"));
    private final List<URI> configServerHosts;

    /**
     * The 'client' will be periodically re-created by clientRefresherScheduler if we provide keyStoreOptions
     * or trustStoreOptions. This is needed because the key/trust stores are updated outside of node-admin,
     * but we want to use the most recent store.
     *
     * The 'client' reference must be volatile because it is set and read in different threads, and visibility
     * of changes is only guaranteed for volatile variables.
     */
    private volatile SelfCloseableHttpClient client;

    public static ConfigServerHttpRequestExecutor create(
            Collection<URI> configServerUris, Optional<KeyStoreOptions> keyStoreOptions, Optional<KeyStoreOptions> trustStoreOptions) {
        Supplier<SelfCloseableHttpClient> clientSupplier = () -> createHttpClient(keyStoreOptions, trustStoreOptions);
        ConfigServerHttpRequestExecutor requestExecutor = new ConfigServerHttpRequestExecutor(
                randomizeConfigServerUris(configServerUris), clientSupplier.get());

        if (keyStoreOptions.isPresent() || trustStoreOptions.isPresent()) {
            requestExecutor.clientRefresherScheduler.scheduleAtFixedRate(() -> requestExecutor.client = clientSupplier.get(),
                    CLIENT_REFRESH_INTERVAL.toMillis(), CLIENT_REFRESH_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        }
        return requestExecutor;
    }

    ConfigServerHttpRequestExecutor(List<URI> configServerHosts, SelfCloseableHttpClient client) {
        this.configServerHosts = configServerHosts;
        this.client = client;
    }

    public interface CreateRequest {
        HttpUriRequest createRequest(URI configServerUri) throws JsonProcessingException, UnsupportedEncodingException;
    }

    private <T> T tryAllConfigServers(CreateRequest requestFactory, Class<T> wantedReturnType) {
        Exception lastException = null;
        for (URI configServer : configServerHosts) {
            final CloseableHttpResponse response;
            try {
                response = client.execute(requestFactory.createRequest(configServer));
            } catch (Exception e) {
                // Failure to communicate with a config server is not abnormal, as they are
                // upgraded at the same time as Docker hosts.
                if (e.getMessage().indexOf("(Connection refused)") > 0) {
                    NODE_ADMIN_LOGGER.info("Connection refused to " + configServer + " (upgrading?), will try next");
                } else {
                    NODE_ADMIN_LOGGER.warning("Failed to communicate with " + configServer + ", will try next: " + e.getMessage());
                }
                lastException = e;
                // reshuffle when failure, to avoid always trying in the same order. TODO: Should rather keep track of when and which server failed
                Collections.shuffle(configServerHosts);
                continue;
            }

            try {
                Optional<HttpException> retryableException = HttpException.handleStatusCode(
                        response.getStatusLine().getStatusCode(),
                        "Config server " + configServer);
                if (retryableException.isPresent()) {
                    lastException = retryableException.get();
                    continue;
                }

                try {
                    return mapper.readValue(response.getEntity().getContent(), wantedReturnType);
                } catch (IOException e) {
                    throw new RuntimeException("Response didn't contain nodes element, failed parsing?", e);
                }
            } finally {
                try {
                    response.close();
                } catch (IOException e) {
                    NODE_ADMIN_LOGGER.warning("Ignoring exception from closing response", e);
                }
            }
        }

        throw new RuntimeException("All requests against the config servers ("
                + configServerHosts + ") failed, last as follows:", lastException);
    }

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

    public <T> T patch(String path, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPatch patch = new HttpPatch(configServer.resolve(path));
            setContentTypeToApplicationJson(patch);
            patch.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo)));
            return patch;
        }, wantedReturnType);
    }

    public <T> T delete(String path, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer ->
                new HttpDelete(configServer.resolve(path)), wantedReturnType);
    }

    public <T> T get(String path, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer ->
                new HttpGet(configServer.resolve(path)), wantedReturnType);
    }

    public <T> T post(String path, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPost post = new HttpPost(configServer.resolve(path));
            setContentTypeToApplicationJson(post);
            post.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo)));
            return post;
        }, wantedReturnType);
    }

    private void setContentTypeToApplicationJson(HttpRequestBase request) {
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    }

    // Shuffle config server URIs to balance load
    private static List<URI> randomizeConfigServerUris(Collection<URI> configServerUris) {
        List<URI> shuffledConfigServerHosts = new ArrayList<>(configServerUris);
        Collections.shuffle(shuffledConfigServerHosts);
        return shuffledConfigServerHosts;
    }

    private static SelfCloseableHttpClient createHttpClient(Optional<KeyStoreOptions> keyStoreOptions,
                                                            Optional<KeyStoreOptions> trustStoreOptions) {
        NODE_ADMIN_LOGGER.info("Creating new HTTP client");
        try {
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    makeSslContext(keyStoreOptions, trustStoreOptions), NoopHostnameVerifier.INSTANCE);
            return new SelfCloseableHttpClient(sslSocketFactory);
        } catch (Exception e) {
            NODE_ADMIN_LOGGER.error("Failed to create HTTP client with custom SSL Context, proceeding with default");
            return new SelfCloseableHttpClient();
        }
    }

    private static SSLContext makeSslContext(Optional<KeyStoreOptions> keyStoreOptions, Optional<KeyStoreOptions> trustStoreOptions)
            throws KeyManagementException, NoSuchAlgorithmException {
        SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
        keyStoreOptions.ifPresent(options -> {
            try {
                sslContextBuilder.loadKeyMaterial(options.getKeyStore(), options.password);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        trustStoreOptions.ifPresent(options -> {
            try {
                sslContextBuilder.loadTrustMaterial(options.getKeyStore(), null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return sslContextBuilder.build();
    }

    @Override
    public void close() {
        clientRefresherScheduler.shutdown();
        do {
            try {
                clientRefresherScheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e1) {
                NODE_ADMIN_LOGGER.info("Interrupted while waiting for clientRefresherScheduler to shutdown");
            }
        } while (!clientRefresherScheduler.isTerminated());

        client.close();
    }
}

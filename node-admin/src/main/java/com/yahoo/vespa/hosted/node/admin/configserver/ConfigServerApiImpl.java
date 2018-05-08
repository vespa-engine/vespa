// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identity.SiaIdentityProvider;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.hosted.node.admin.component.ConfigServerInfo;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;

import javax.net.ssl.SSLContext;
import java.io.IOException;
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
 */
public class ConfigServerApiImpl implements ConfigServerApi {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(ConfigServerApiImpl.class);

    private final ObjectMapper mapper = new ObjectMapper();

    private final List<URI> configServers;

    private Runnable runOnClose = () -> {};

    /**
     * The 'client' may be periodically re-created through calls to setSSLConnectionSocketFactory.
     *
     * The 'client' reference must be volatile because it is set and read in different threads, and visibility
     * of changes is only guaranteed for volatile variables.
     */
    private volatile SelfCloseableHttpClient client;

    /**
     * Creates an api for talking to the config servers with a fixed socket factory.
     *
     * <p>This may be used to avoid requiring background certificate signing requests (CSR)
     * against the config server when client validation is enabled in the config server.
     */
    public static ConfigServerApiImpl createWithSocketFactory(
            List<URI> configServerHosts,
            SSLConnectionSocketFactory socketFactory) {
        return new ConfigServerApiImpl(configServerHosts, new SelfCloseableHttpClient(socketFactory));
    }

    public static ConfigServerApiImpl create(ConfigServerInfo configServerInfo,
                                             SiaIdentityProvider identityProvider) {
        return new ConfigServerApiImpl(configServerInfo.getConfigServerUris(), identityProvider);
    }

    public static ConfigServerApiImpl createFor(ConfigServerInfo configServerInfo,
                                                SiaIdentityProvider identityProvider,
                                                HostName configServer) {
        URI uri = configServerInfo.getConfigServerUri(configServer.value());
        return new ConfigServerApiImpl(Collections.singletonList(uri), identityProvider);
    }

    static ConfigServerApiImpl createForTestingWithClient(List<URI> configServerHosts,
                                                          SelfCloseableHttpClient client) {
        return new ConfigServerApiImpl(configServerHosts, client);
    }

    private ConfigServerApiImpl(Collection<URI> configServers, SiaIdentityProvider identityProvider) {
        this(configServers, createClient(identityProvider));

        // The same object MUST be passed to both addIdentityListener and removeIdentityListener,
        // as two method references aren't equal.
        ServiceIdentityProvider.Listener listener = this::setClient;
        identityProvider.addIdentityListener(listener);
        this.runOnClose = () -> identityProvider.removeIdentityListener(listener);
    }

    private ConfigServerApiImpl(Collection<URI> configServers, SelfCloseableHttpClient client) {
        this.configServers = randomizeConfigServerUris(configServers);
        this.client = client;
    }

    interface CreateRequest {
        HttpUriRequest createRequest(URI configServerUri) throws JsonProcessingException, UnsupportedEncodingException;
    }

    private <T> T tryAllConfigServers(CreateRequest requestFactory, Class<T> wantedReturnType) {
        Exception lastException = null;
        for (URI configServer : configServers) {
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
        runOnClose.run();
        client.close();
    }

    private void setContentTypeToApplicationJson(HttpRequestBase request) {
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    }

    private void setClient(SSLContext sslContext, AthenzService identity) {
        this.client = createClient(sslContext, identity);
    }

    private static SelfCloseableHttpClient createClient(SSLContext sslContext, AthenzService identity) {
        AthenzIdentityVerifier identityVerifier = new AthenzIdentityVerifier(singleton(identity));
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext, identityVerifier);
        return new SelfCloseableHttpClient(socketFactory);
    }

    private static SelfCloseableHttpClient createClient(SiaIdentityProvider identityProvider) {
        return createClient(identityProvider.getIdentitySslContext(), identityProvider.identity());
    }

    // Shuffle config server URIs to balance load
    private static List<URI> randomizeConfigServerUris(Collection<URI> configServerUris) {
        List<URI> shuffledConfigServerHosts = new ArrayList<>(configServerUris);
        Collections.shuffle(shuffledConfigServerHosts);
        return shuffledConfigServerHosts;
    }
}

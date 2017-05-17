// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Retries request on config server a few times before giving up. Assumes that all requests should be sent with
 * content-type application/json
 *
 * @author dybdahl
 */
public class ConfigServerHttpRequestExecutor {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(ConfigServerHttpRequestExecutor.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final CloseableHttpClient client;
    private final List<String> configServerHosts;
    private final static int MAX_LOOPS = 2;

    @Override
    public void finalize() throws Throwable {
        try {
            client.close();
        } catch (Exception e) {
            NODE_ADMIN_LOGGER.warning("Ignoring exception thrown when closing client against " + configServerHosts, e);
        }

        super.finalize();
    }

    public static ConfigServerHttpRequestExecutor create(Set<String> configServerHosts) {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        // Increase max total connections to 200, which should be enough
        cm.setMaxTotal(200);
        return new ConfigServerHttpRequestExecutor(configServerHosts,
                                                   HttpClientBuilder.create().disableAutomaticRetries().setConnectionManager(cm).build());
    }

    ConfigServerHttpRequestExecutor(Set<String> configServerHosts, CloseableHttpClient client) {
        this.configServerHosts = randomizeConfigServerHosts(configServerHosts);
        this.client = client;
    }

    public interface CreateRequest {
        HttpUriRequest createRequest(String configserver) throws JsonProcessingException, UnsupportedEncodingException;
    }

    public class NotFoundException extends RuntimeException {
        private static final long serialVersionUID = 4791511887L;
        public NotFoundException(String message) {
            super(message);
        }
    }

    private <T> T tryAllConfigServers(CreateRequest requestFactory, Class<T> wantedReturnType) {
        Exception lastException = null;
        for (int loopRetry = 0; loopRetry < MAX_LOOPS; loopRetry++) {
            for (String configServer : configServerHosts) {
                final CloseableHttpResponse response;
                try {
                    response = client.execute(requestFactory.createRequest(configServer));
                } catch (Exception e) {
                    lastException = e;
                    NODE_ADMIN_LOGGER.info("Exception while talking to " + configServer + " (will try all config servers):" + e.getMessage());
                    continue;
                }

                try {
                    if (response.getStatusLine().getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                        throw new NotFoundException("Not found returned from " + configServer);
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
        }
        throw new RuntimeException("Failed executing request, last exception: ", lastException);
    }

    public <T> T put(String path, int port, Optional<Object> bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPut put = new HttpPut("http://" + configServer + ":" + port + path);
            setContentTypeToApplicationJson(put);
            if (bodyJsonPojo.isPresent()) {
                put.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo.get())));
            }
            return put;
        }, wantedReturnType);
    }

    public <T> T patch(String path, int port, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPatch patch = new HttpPatch("http://" + configServer + ":" + port + path);
            setContentTypeToApplicationJson(patch);
            patch.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo)));
            return patch;
        }, wantedReturnType);
    }

    public <T> T delete(String path, int port, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer ->
                new HttpDelete("http://" + configServer + ":" + port + path), wantedReturnType);
    }

    public <T> T get(String path, int port, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer ->
                new HttpGet("http://" + configServer + ":" + port + path), wantedReturnType);
    }

    public <T> T post(String path, int port, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPost post = new HttpPost("http://" + configServer + ":" + port + path);
            setContentTypeToApplicationJson(post);
            post.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo)));
            return post;
        }, wantedReturnType);
    }

    private void setContentTypeToApplicationJson(HttpRequestBase request) {
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    }

    // Shuffle config server hosts to balance load
    private List<String> randomizeConfigServerHosts(Set<String> configServerHosts) {
        List<String> shuffledConfigServerHosts = new ArrayList<>(configServerHosts);
        Collections.shuffle(shuffledConfigServerHosts);
        return shuffledConfigServerHosts;
    }

}

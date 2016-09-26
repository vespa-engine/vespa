// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retries request on config server a few times before giving up.
 *
 * @author dybdahl
 */
public class ConfigServerHttpRequestExecutor {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(ConfigServerHttpRequestExecutor.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private final Set<String> configServerHosts;
    private final static int MAX_LOOPS = 2;

    public static ConfigServerHttpRequestExecutor create(Set<String> configServerHosts) {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        // Increase max total connections to 200, which should be enough
        cm.setMaxTotal(200);
        return new ConfigServerHttpRequestExecutor(configServerHosts, HttpClientBuilder.create()
                .setConnectionManager(cm).build());
    }

    public ConfigServerHttpRequestExecutor(Set<String> configServerHosts, HttpClient client) {
        this.configServerHosts = configServerHosts;
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

    public <T> T tryAllConfigServers(CreateRequest requestFactory, Class<T> wantedReturnType) {
        Exception lastException = null;
        for (int loopRetry = 0; loopRetry < MAX_LOOPS; loopRetry++) {
            for (String configServer : configServerHosts) {
                final HttpResponse response;
                try {
                    response = client.execute(requestFactory.createRequest(configServer));
                } catch (Exception e) {
                    lastException = e;
                    NODE_ADMIN_LOGGER.info("Exception while talking to " + configServer + " (will try all config servers):" + e.getMessage());
                    continue;
                }
                if (response.getStatusLine().getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException("Not found returned from " + configServer);
                }
                if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
                    String entity = read(response.getEntity());
                    NODE_ADMIN_LOGGER.info("Non-200 HTTP response code received:\n" + entity);
                    throw new RuntimeException("Did not get response code 200, but " + response.getStatusLine().getStatusCode() +
                            entity);
                }
                try {
                    return mapper.readValue(response.getEntity().getContent(), wantedReturnType);
                } catch (IOException e) {
                    throw new RuntimeException("Response didn't contain nodes element, failed parsing?", e);
                }
            }
        }
        throw new RuntimeException("Failed executing request, last exception: ", lastException);
    }

    public <T> T put(String path, int port, Optional<Object> bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPut put = new HttpPut("http://" + configServer + ":" + port + path);
            if (bodyJsonPojo.isPresent()) {
                put.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo.get())));
            }
            return put;
        }, wantedReturnType);
    }

    public <T> T patch(String path, int port, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPatch patch = new HttpPatch("http://" + configServer + ":" + port + path);
            patch.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo)));
            return patch;
        }, wantedReturnType);
    }

    public <T> T delete(String path, int port, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            return new HttpDelete("http://" + configServer + ":" + port + path);
        }, wantedReturnType);
    }

    public <T> T get(String path, int port, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            return new HttpGet("http://" + configServer + ":" + port + path);
        }, wantedReturnType);
    }

    private  static String read(HttpEntity input)  {
        try {
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input.getContent()))) {
                return buffer.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            return "Failed reading stream: " + e.getMessage();
        }
    }
}
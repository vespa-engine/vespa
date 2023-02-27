// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import ai.vespa.util.http.hc4.SslConnectionSocketFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identity.ServiceIdentitySslSocketFactory;
import com.yahoo.vespa.hosted.node.admin.component.ConfigServerInfo;
import com.yahoo.yolean.Exceptions;
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
import org.apache.http.util.EntityUtils;

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
import java.util.logging.Logger;

/**
 * Retries request on config server a few times before giving up. Assumes that all requests should be sent with
 * content-type application/json
 *
 * @author dybdahl
 * @author bjorncs
 */
public class ConfigServerApiImpl implements ConfigServerApi {

    private static final Logger logger = Logger.getLogger(ConfigServerApiImpl.class.getName());
    private static final RequestConfig DEFAULT_REQUEST_CONFIG = RequestConfig.custom()
            .setConnectionRequestTimeout(1_000) // connection from connection manager
            .setConnectTimeout(10_000) // establishment of connection
            .setSocketTimeout(10_000) // waiting for data
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    private final List<URI> configServers;

    private final CloseableHttpClient client;

    public static ConfigServerApiImpl create(ConfigServerInfo info,
                                             ServiceIdentityProvider provider,
                                             HostnameVerifier hostnameVerifier) {
        return new ConfigServerApiImpl(
                info.getConfigServerUris(),
                hostnameVerifier,
                provider);
    }

    public static ConfigServerApiImpl createFor(ConfigServerInfo info,
                                                ServiceIdentityProvider provider,
                                                HostnameVerifier hostnameVerifier,
                                                HostName configServerHostname) {
        return new ConfigServerApiImpl(
                List.of(info.getConfigServerUri(configServerHostname.value())),
                hostnameVerifier,
                provider);
    }

    private ConfigServerApiImpl(Collection<URI> configServers,
                                HostnameVerifier verifier,
                                ServiceIdentityProvider identityProvider) {
        this(configServers, createClient(SslConnectionSocketFactory.of(new ServiceIdentitySslSocketFactory(identityProvider), verifier)));
    }

    private ConfigServerApiImpl(Collection<URI> configServers, CloseableHttpClient client) {
        this.configServers = randomizeConfigServerUris(configServers);
        this.client = client;
    }

    public static ConfigServerApiImpl createForTesting(List<URI> configServerHosts) {
        return new ConfigServerApiImpl(configServerHosts, createClient(SslConnectionSocketFactory.of()));
    }

    static ConfigServerApiImpl createForTestingWithClient(List<URI> configServerHosts,
                                                          CloseableHttpClient client) {
        return new ConfigServerApiImpl(configServerHosts, client);
    }

    interface CreateRequest {
        HttpUriRequest createRequest(URI configServerUri) throws JsonProcessingException, UnsupportedEncodingException;
    }

    private <T> T tryAllConfigServers(CreateRequest requestFactory, Class<T> wantedReturnType, Params<T> params) {
        T lastResult = null;
        Exception lastException = null;

        for (URI configServer : configServers) {
            var request = Exceptions.uncheck(() -> requestFactory.createRequest(configServer));
            try (CloseableHttpResponse response = client.execute(request)) {
                var responseBody = EntityUtils.toString(response.getEntity());
                HttpException.handleStatusCode(response.getStatusLine().getStatusCode(),
                                               request.getMethod() + " " + request.getURI() +
                                               " failed with response '" + responseBody + "'");

                T result;
                try {
                    result = mapper.readValue(responseBody, wantedReturnType);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed parse response from config server", e);
                }

                if (params.getRetryPolicy().tryNextConfigServer(configServer, result)) {
                    lastResult = result;
                    lastException = null;
                } else {
                    return result;
                }
            } catch (HttpException e) {
                if (!e.isRetryable()) throw e;
                lastResult = null;
                lastException = e;
            } catch (Exception e) {
                lastResult = null;
                lastException = e;
                if (configServers.size() == 1) break;

                // Failure to communicate with a config server is not abnormal during upgrades
                if (ConnectionException.isKnownConnectionException(e)) {
                    logger.info("Failed to connect to " + configServer + ", will try next: " + e.getMessage());
                } else {
                    logger.warning("Failed to communicate with " + configServer + ", will try next: " + e.getMessage());
                }
            }
        }

        if (lastResult != null) {
            logger.warning("Giving up after trying all config servers: returning result: " + lastResult);
            return lastResult;
        }

        String prefix = configServers.size() == 1 ?
                "Request against " + configServers.get(0) + " failed: " :
                "All requests against the config servers (" + configServers + ") failed, last as follows: ";
        throw ConnectionException.handleException(prefix, lastException);
    }

    @Override
    public <T> T put(String path, Optional<Object> bodyJsonPojo, Class<T> wantedReturnType, Params<T> params) {
        Optional<RequestConfig> requestConfigOverride = getRequestConfigOverride(params);
        return tryAllConfigServers(configServer -> {
            HttpPut put = new HttpPut(configServer.resolve(path));
            requestConfigOverride.ifPresent(put::setConfig);
            setContentTypeToApplicationJson(put);
            if (bodyJsonPojo.isPresent()) {
                put.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo.get())));
            }
            return put;
        }, wantedReturnType, params);
    }

    @Override
    public <T> T patch(String path, Object bodyJsonPojo, Class<T> wantedReturnType, Params<T> params) {
        Optional<RequestConfig> requestConfigOverride = getRequestConfigOverride(params);
        return tryAllConfigServers(configServer -> {
            HttpPatch patch = new HttpPatch(configServer.resolve(path));
            requestConfigOverride.ifPresent(patch::setConfig);
            setContentTypeToApplicationJson(patch);
            patch.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo)));
            return patch;
        }, wantedReturnType, params);
    }

    @Override
    public <T> T delete(String path, Class<T> wantedReturnType, Params<T> params) {
        Optional<RequestConfig> requestConfigOverride = getRequestConfigOverride(params);
        return tryAllConfigServers(configServer -> {
            HttpDelete delete = new HttpDelete(configServer.resolve(path));
            requestConfigOverride.ifPresent(delete::setConfig);
            return delete;
        }, wantedReturnType, params);
    }

    @Override
    public <T> T get(String path, Class<T> wantedReturnType, Params<T> params) {
        Optional<RequestConfig> requestConfig = getRequestConfigOverride(params);
        return tryAllConfigServers(configServer -> {
            HttpGet get = new HttpGet(configServer.resolve(path));
            requestConfig.ifPresent(get::setConfig);
            return get;
        }, wantedReturnType, params);
    }

    @Override
    public <T> T post(String path, Object bodyJsonPojo, Class<T> wantedReturnType, Params<T> params) {
        Optional<RequestConfig> requestConfigOverride = getRequestConfigOverride(params);
        return tryAllConfigServers(configServer -> {
            HttpPost post = new HttpPost(configServer.resolve(path));
            requestConfigOverride.ifPresent(post::setConfig);
            setContentTypeToApplicationJson(post);
            post.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo)));
            return post;
        }, wantedReturnType, params);
    }

    @Override
    public void close() {
        // Need to do try and catch, using e.g. uncheck(client::close) might fail because
        // components are deconstructed in random order and if the bundle containing uncheck has been
        // unloaded it will fail with NoClassDefFoundError
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
        // system defaults, therefore set explicit timeouts.
        return HttpClientBuilder.create()
                .setDefaultRequestConfig(DEFAULT_REQUEST_CONFIG)
                .disableAutomaticRetries()
                .disableConnectionState() // Share connections between subsequent requests.
                .setUserAgent("node-admin")
                .setConnectionManager(cm)
                .build();
    }

    private static <T> Optional<RequestConfig> getRequestConfigOverride(Params<T> params) {
        if (params.getConnectionTimeout().isEmpty()) return Optional.empty();

        RequestConfig.Builder builder = RequestConfig.copy(DEFAULT_REQUEST_CONFIG);

        params.getConnectionTimeout().ifPresent(connectionTimeout -> {
            builder.setConnectTimeout((int) connectionTimeout.toMillis());
            builder.setSocketTimeout((int) connectionTimeout.toMillis());
        });

        return Optional.of(builder.build());
    }

    // Shuffle config server URIs to balance load
    private static List<URI> randomizeConfigServerUris(Collection<URI> configServerUris) {
        List<URI> shuffledConfigServerHosts = new ArrayList<>(configServerUris);
        Collections.shuffle(shuffledConfigServerHosts);
        return shuffledConfigServerHosts;
    }

}

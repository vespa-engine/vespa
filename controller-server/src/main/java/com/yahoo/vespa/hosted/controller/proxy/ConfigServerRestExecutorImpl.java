// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.ZoneId;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author Haakon Dybdahl
 */
@SuppressWarnings("unused") // Injected
public class ConfigServerRestExecutorImpl implements ConfigServerRestExecutor {

    private static final Duration PROXY_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ZoneRegistry zoneRegistry;

    public ConfigServerRestExecutorImpl(ZoneRegistry zoneRegistry) {
        this.zoneRegistry = zoneRegistry;
    }

    @Override
    public ProxyResponse handle(ProxyRequest proxyRequest) throws ProxyException {
        if (proxyRequest.isDiscoveryRequest()) {
            return createDiscoveryResponse(proxyRequest);
        }

        ZoneId zoneId = ZoneId.from(proxyRequest.getEnvironment(), proxyRequest.getRegion());

        // Make a local copy of the list as we want to manipulate it in case of ping problems.
        List<URI> allServers = new ArrayList<>(zoneRegistry.getConfigServerUris(zoneId));

        StringBuilder errorBuilder = new StringBuilder();
        if (queueFirstServerIfDown(allServers)) {
            errorBuilder.append("Change ordering due to failed ping.");
        }
        for (URI uri : allServers) {
            Optional<ProxyResponse> proxyResponse = proxyCall(uri, proxyRequest, errorBuilder);
            if (proxyResponse.isPresent()) {
                return proxyResponse.get();
            }
        }
        // TODO Add logging, for now, experimental and we want to not add more noise.
        throw new ProxyException(ErrorResponse.internalServerError("Failed talking to config servers: "
                + errorBuilder.toString()));
    }

    private static class DiscoveryResponseStructure {
        public List<String> uris = new ArrayList<>();
    }

    private ProxyResponse createDiscoveryResponse(ProxyRequest proxyRequest) {
        ObjectMapper mapper = new ObjectMapper();
        DiscoveryResponseStructure responseStructure = new DiscoveryResponseStructure();

        List<ZoneId> zones = zoneRegistry.zones();
        for (ZoneId zone : zones) {
            if (!"".equals(proxyRequest.getEnvironment()) &&
                !proxyRequest.getEnvironment().equals(zone.environment().value())) {
                continue;
            }
            responseStructure.uris.add(proxyRequest.getScheme() + "://" + proxyRequest.getControllerPrefix() +
                                       zone.environment().name() + "/" + zone.region().value());
        }
        JsonNode node = mapper.valueToTree(responseStructure);
        return new ProxyResponse(proxyRequest, node.toString(), 200, Optional.empty(), "application/json");
    }

    private String removeFirstSlashIfAny(String url) {
        if (url.startsWith("/")) {
            return url.substring(1);
        }
        return url;
    }

    private Optional<ProxyResponse> proxyCall(URI uri, ProxyRequest proxyRequest, StringBuilder errorBuilder)
            throws ProxyException {
        String fullUri = uri.toString() + removeFirstSlashIfAny(proxyRequest.getConfigServerRequest());
        final HttpRequestBase requestBase = createHttpBaseRequest(
                proxyRequest.getMethod(), fullUri, proxyRequest.getData());
        // Empty list of headers to copy for now, add headers when needed, or rewrite logic.
        copyHeaders(proxyRequest.getHeaders(), requestBase, new HashSet<>());

        RequestConfig config = RequestConfig.custom()
                                            .setConnectTimeout((int) PROXY_REQUEST_TIMEOUT.toMillis())
                                            .setConnectionRequestTimeout((int) PROXY_REQUEST_TIMEOUT.toMillis())
                                            .setSocketTimeout((int) PROXY_REQUEST_TIMEOUT.toMillis()).build();
        try (
                CloseableHttpClient client = createHttpClient(config);
                CloseableHttpResponse response = client.execute(requestBase);
        ) {
            if (response.getStatusLine().getStatusCode() / 100 == 5) {
                errorBuilder.append("Talking to server ").append(uri.getHost());
                errorBuilder.append(", got ").append(response.getStatusLine().getStatusCode()).append(" ")
                            .append(streamToString(response.getEntity().getContent())).append("\n");
                return Optional.empty();
            }
            final Header contentHeader = response.getLastHeader("Content-Type");
            final String contentType;
            if (contentHeader != null && contentHeader.getValue() != null && ! contentHeader.getValue().isEmpty()) {
                contentType = contentHeader.getValue().replace("; charset=UTF-8","");
            } else {
                contentType = "application/json";
            }
            return Optional.of(new ProxyResponse(
                    proxyRequest,
                    streamToString(response.getEntity().getContent()),
                    response.getStatusLine().getStatusCode(),
                    Optional.of(uri),
                    contentType));

            // Send response back
        } catch (IOException|RuntimeException e) {
            errorBuilder.append("Talking to server ").append(uri.getHost());
            errorBuilder.append(" got exception ").append(e.getMessage());
            return Optional.empty();
        }
    }

    private HttpRequestBase createHttpBaseRequest(String method, String uri, InputStream data) throws ProxyException {
        Method enumMethod =  Method.valueOf(method);
        switch (enumMethod) {
            case GET:
                return new HttpGet(uri);
            case POST:
                HttpPost post = new HttpPost(uri);
                if (data != null) {
                    post.setEntity(new InputStreamEntity(data));
                }
                return post;
            case PUT:
                HttpPut put = new HttpPut(uri);
                if (data != null) {
                    put.setEntity(new InputStreamEntity(data));
                }
                return put;
            case DELETE:
                return new HttpDelete(uri);
            case PATCH:
                HttpPatch patch = new HttpPatch(uri);
                if (data != null) {
                    patch.setEntity(new InputStreamEntity(data));
                }
                return patch;
            default:
                throw new ProxyException(ErrorResponse.methodNotAllowed("Will not proxy such calls."));
        }
    }

    private void copyHeaders(Map<String, List<String>> headers, HttpRequestBase toRequest, Set<String> headersToCopy) {
        for (Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {
            for (String value : headerEntry.getValue()) {
                if (headersToCopy.contains(value)) {
                    toRequest.addHeader(headerEntry.getKey(), value);
                }
            }
        }
    }

    public static String streamToString(final InputStream inputStream) throws IOException {
        final StringBuilder out = new StringBuilder();
        while (true) {
            byte[] bytesFromStream = IOUtils.readBytes(inputStream, 1024);
            if (bytesFromStream.length == 0) {
                return out.toString();
            }
            out.append(new String(bytesFromStream, StandardCharsets.UTF_8));
        }
    }

    /**
     * During upgrade, one server can be down, this is normal. Therefor we do a quick ping on the first server,
     * if it is not responding, we try the other servers first. False positive/negatives are not critical,
     * but will increase latency to some extent.
     */
    private boolean queueFirstServerIfDown(List<URI> allServers) {
        if (allServers.size() < 2) {
            return false;
        }
        URI uri = allServers.get(0);
        HttpGet httpget = new HttpGet(uri);

        int timeout = 500;
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout).build();
        try (
                CloseableHttpClient client = createHttpClient(config);
                CloseableHttpResponse response = client.execute(httpget);

        ) {
            if (response.getStatusLine().getStatusCode() == 200) {
                return false;
            }

        } catch (IOException e) {
            // We ignore this, if server is restarting this might happen.
        }
        // Some error happened, move this server to the back. The other servers should be running.
        allServers.remove(0);
        allServers.add(uri);
        return true;
    }

    private static CloseableHttpClient createHttpClient(RequestConfig config) {
        return HttpClientBuilder.create()
                .setUserAgent("config-server-client")
                .setDefaultRequestConfig(config)
                .build();
    }

}

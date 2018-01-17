// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.yahoo.config.provision.Environment;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzIdentity;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzIdentityVerifier;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzSslContextProvider;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzUtils;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneList;
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
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.Collections.singleton;

/**
 * @author Haakon Dybdahl
 * @author bjorncs
 */
@SuppressWarnings("unused") // Injected
public class ConfigServerRestExecutorImpl implements ConfigServerRestExecutor {

    private static final Logger log = Logger.getLogger(ConfigServerRestExecutorImpl.class.getName());

    private static final Duration PROXY_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> HEADERS_TO_COPY = Collections.singleton("X-HTTP-Method-Override");

    private final ZoneRegistry zoneRegistry;
    private final AthenzSslContextProvider sslContextProvider;

    @Inject
    public ConfigServerRestExecutorImpl(ZoneRegistry zoneRegistry,
                                        AthenzSslContextProvider sslContextProvider) {
        this.zoneRegistry = zoneRegistry;
        this.sslContextProvider = sslContextProvider;
    }

    @Override
    public ProxyResponse handle(ProxyRequest proxyRequest) throws ProxyException {
        if (proxyRequest.isDiscoveryRequest()) {
            return createDiscoveryResponse(proxyRequest);
        }

        ZoneId zoneId = ZoneId.from(proxyRequest.getEnvironment(), proxyRequest.getRegion());

        // Make a local copy of the list as we want to manipulate it in case of ping problems.
        List<URI> allServers = new ArrayList<>(zoneRegistry.getConfigServerSecureUris(zoneId));

        StringBuilder errorBuilder = new StringBuilder();
        if (queueFirstServerIfDown(allServers, proxyRequest)) {
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
        String environmentName = proxyRequest.getEnvironment();

        ZoneList zones = zoneRegistry.zones().all();
        if ( ! environmentName.isEmpty())
            zones = zones.in(Environment.from(environmentName));

        for (ZoneId zoneId : zones.ids()) {
            responseStructure.uris.add(proxyRequest.getScheme() + "://" + proxyRequest.getControllerPrefix() +
                                       zoneId.environment().name() + "/" + zoneId.region().value());
        }
        JsonNode node = mapper.valueToTree(responseStructure);
        return new ProxyResponse(proxyRequest, node.toString(), 200, Optional.empty(), "application/json");
    }

    private static String removeFirstSlashIfAny(String url) {
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
        copyHeaders(proxyRequest.getHeaders(), requestBase);

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout((int) PROXY_REQUEST_TIMEOUT.toMillis())
                .setConnectionRequestTimeout((int) PROXY_REQUEST_TIMEOUT.toMillis())
                .setSocketTimeout((int) PROXY_REQUEST_TIMEOUT.toMillis()).build();
        try (
                CloseableHttpClient client = createHttpClient(config, sslContextProvider, zoneRegistry, proxyRequest);
                CloseableHttpResponse response = client.execute(requestBase);
        ) {
            String content = getContent(response);
            int status = response.getStatusLine().getStatusCode();
            if (status / 100 == 5) {
                errorBuilder.append("Talking to server ").append(uri.getHost());
                errorBuilder.append(", got ").append(status).append(" ")
                        .append(content).append("\n");
                log.log(LogLevel.DEBUG, () -> String.format("Got response from %s with status code %d and content:\n %s",
                                                            uri.getHost(), status, content));
                return Optional.empty();
            }
            final Header contentHeader = response.getLastHeader("Content-Type");
            final String contentType;
            if (contentHeader != null && contentHeader.getValue() != null && ! contentHeader.getValue().isEmpty()) {
                contentType = contentHeader.getValue().replace("; charset=UTF-8","");
            } else {
                contentType = "application/json";
            }
            // Send response back
            return Optional.of(new ProxyResponse(proxyRequest, content, status, Optional.of(uri), contentType));
        } catch (Exception e) {
            errorBuilder.append("Talking to server ").append(uri.getHost());
            errorBuilder.append(" got exception ").append(e.getMessage());
            log.log(LogLevel.DEBUG, e, () -> "Got exception while sending request to " + uri.getHost());
            return Optional.empty();
        }
    }

    private static String getContent(CloseableHttpResponse response) {
        return Optional.ofNullable(response.getEntity())
                .map(entity ->
                     {
                         try {
                             return EntityUtils.toString(entity);
                         } catch (IOException e) {
                             throw new UncheckedIOException(e);
                         }
                     }
                ).orElse("");
    }

    private static HttpRequestBase createHttpBaseRequest(String method, String uri, InputStream data) throws ProxyException {
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

    private static void copyHeaders(Map<String, List<String>> headers, HttpRequestBase toRequest) {
        for (Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {
            if (HEADERS_TO_COPY.contains(headerEntry.getKey())) {
                for (String value : headerEntry.getValue()) {
                    toRequest.addHeader(headerEntry.getKey(), value);
                }
            }
        }
    }

    /**
     * During upgrade, one server can be down, this is normal. Therefor we do a quick ping on the first server,
     * if it is not responding, we try the other servers first. False positive/negatives are not critical,
     * but will increase latency to some extent.
     */
    private boolean queueFirstServerIfDown(List<URI> allServers, ProxyRequest proxyRequest) {
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
                CloseableHttpClient client = createHttpClient(config, sslContextProvider, zoneRegistry, proxyRequest);
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

    private static CloseableHttpClient createHttpClient(RequestConfig config,
                                                        AthenzSslContextProvider sslContextProvider,
                                                        ZoneRegistry zoneRegistry,
                                                        ProxyRequest proxyRequest) {
        AthenzIdentityVerifier hostnameVerifier =
                new AthenzIdentityVerifier(
                        singleton(
                                zoneRegistry.getConfigserverAthenzService(
                                        ZoneId.from(proxyRequest.getEnvironment(), proxyRequest.getRegion()))));
        return HttpClientBuilder.create()
                .setUserAgent("config-server-proxy-client")
                .setSslcontext(sslContextProvider.get())
                .setHostnameVerifier(new AthenzIdentityVerifierAdapter(hostnameVerifier))
                .setDefaultRequestConfig(config)
                .build();
    }

    private static class AthenzIdentityVerifierAdapter implements X509HostnameVerifier {

        private final AthenzIdentityVerifier verifier;

        AthenzIdentityVerifierAdapter(AthenzIdentityVerifier verifier) {
            this.verifier = verifier;
        }

        @Override
        public boolean verify(String hostname, SSLSession sslSession) {
            return verifier.verify(hostname, sslSession);
        }

        @Override
        public void verify(String host, SSLSocket ssl) { /* All sockets accepted */}

        @Override
        public void verify(String hostname, X509Certificate certificate) throws SSLException {
            AthenzIdentity identity = AthenzUtils.createAthenzIdentity(certificate);
            if (!verifier.isTrusted(identity)) {
                throw new SSLException("Athenz identity is not trusted: " + identity.getFullName());
            }
        }

        @Override
        public void verify(String hostname, String[] cns, String[] subjectAlts) throws SSLException {
            AthenzIdentity identity = AthenzUtils.createAthenzIdentity(cns[0]);
            if (!verifier.isTrusted(identity)) {
                throw new SSLException("Athenz identity is not trusted: " + identity.getFullName());
            }
        }
    }

}

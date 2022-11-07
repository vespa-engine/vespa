// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.text.Text;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.hosted.controller.api.integration.ControllerIdentityProvider;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.yolean.concurrent.Sleeper;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.yolean.Exceptions.uncheck;


/**
 * @author Haakon Dybdahl
 * @author bjorncs
 */
@SuppressWarnings("unused") // Injected
public class ConfigServerRestExecutorImpl extends AbstractComponent implements ConfigServerRestExecutor {

    private static final Logger LOG = Logger.getLogger(ConfigServerRestExecutorImpl.class.getName());
    private static final Duration PROXY_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration PING_REQUEST_TIMEOUT = Duration.ofMillis(500);
    private static final Duration SINGLE_TARGET_WAIT = Duration.ofSeconds(2);
    private static final int SINGLE_TARGET_RETRIES = 3;
    private static final Set<String> HEADERS_TO_COPY = Set.of("X-HTTP-Method-Override", "Content-Type");

    private final CloseableHttpClient client;
    private final Sleeper sleeper;

    @Inject
    public ConfigServerRestExecutorImpl(ZoneRegistry zoneRegistry, ControllerIdentityProvider identityProvider) {
        this(new SSLConnectionSocketFactory(identityProvider.getConfigServerSslSocketFactory(), new ControllerOrConfigserverHostnameVerifier(zoneRegistry)),
                Sleeper.DEFAULT,
                new ConnectionReuseStrategy(zoneRegistry));
    }

    ConfigServerRestExecutorImpl(SSLConnectionSocketFactory connectionSocketFactory,
                                 Sleeper sleeper, ConnectionReuseStrategy connectionReuseStrategy) {
        this.client = createHttpClient(connectionSocketFactory, connectionReuseStrategy);
        this.sleeper = sleeper;
    }

    @Override
    public ProxyResponse handle(ProxyRequest request) {
        List<URI> targets = new ArrayList<>(request.getTargets());

        StringBuilder errorBuilder = new StringBuilder();
        boolean singleTarget = targets.size() == 1;
        if (singleTarget) {
            for (int i = 0; i < SINGLE_TARGET_RETRIES - 1; i++) {
                targets.add(targets.get(0));
            }
        } else if (queueFirstServerIfDown(targets)) {
            errorBuilder.append("Change ordering due to failed ping.");
        }

        for (URI url : targets) {
            Optional<ProxyResponse> proxyResponse = proxy(request, url, errorBuilder);
            if (proxyResponse.isPresent()) {
                return proxyResponse.get();
            }
            if (singleTarget) {
                sleeper.sleep(SINGLE_TARGET_WAIT);
            }
        }

        throw new RuntimeException("Failed talking to config servers: " + errorBuilder);
    }

    private Optional<ProxyResponse> proxy(ProxyRequest request, URI url, StringBuilder errorBuilder) {
        HttpRequestBase requestBase = createHttpBaseRequest(
                request.getMethod(), request.createConfigServerRequestUri(url), request.getData());
        // Empty list of headers to copy for now, add headers when needed, or rewrite logic.
        copyHeaders(request.getHeaders(), requestBase);

        try (CloseableHttpResponse response = client.execute(requestBase)) {
            String content = getContent(response);
            int status = response.getStatusLine().getStatusCode();
            if (status / 100 == 5) {
                errorBuilder.append("Talking to server ").append(url.getHost())
                            .append(", got ").append(status).append(" ")
                            .append(content).append("\n");
                LOG.log(Level.FINE, () -> Text.format("Got response from %s with status code %d and content:\n %s",
                                                        url.getHost(), status, content));
                return Optional.empty();
            }
            Header contentHeader = response.getLastHeader("Content-Type");
            String contentType;
            if (contentHeader != null && contentHeader.getValue() != null && ! contentHeader.getValue().isEmpty()) {
                contentType = contentHeader.getValue().replace("; charset=UTF-8","");
            } else {
                contentType = "application/json";
            }
            // Send response back
            return Optional.of(new ProxyResponse(request, content, status, url, contentType));
        } catch (Exception e) {
            errorBuilder.append("Talking to server ").append(url.getHost())
                        .append(" got exception ").append(e.getMessage())
                        .append("\n");
            LOG.log(Level.FINE, e, () -> "Got exception while sending request to " + url.getHost());
            return Optional.empty();
        }
    }

    private static String getContent(CloseableHttpResponse response) {
        return Optional.ofNullable(response.getEntity())
                .map(entity -> uncheck(() -> EntityUtils.toString(entity)))
                .orElse("");
    }

    private static HttpRequestBase createHttpBaseRequest(Method method, URI url, InputStream data) {
        switch (method) {
            case GET:
                return new HttpGet(url);
            case POST:
                HttpPost post = new HttpPost(url);
                if (data != null) {
                    post.setEntity(new InputStreamEntity(data));
                }
                return post;
            case PUT:
                HttpPut put = new HttpPut(url);
                if (data != null) {
                    put.setEntity(new InputStreamEntity(data));
                }
                return put;
            case DELETE:
                return new HttpDelete(url);
            case PATCH:
                HttpPatch patch = new HttpPatch(url);
                if (data != null) {
                    patch.setEntity(new InputStreamEntity(data));
                }
                return patch;
        }
        throw new IllegalArgumentException("Refusing to proxy " + method + " " + url + ": Unsupported method");
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
     * During upgrade, one server can be down, this is normal. Therefore we do a quick ping on the first server,
     * if it is not responding, we try the other servers first. False positive/negatives are not critical,
     * but will increase latency to some extent.
     */
    private boolean queueFirstServerIfDown(List<URI> allServers) {
        if (allServers.size() < 2) {
            return false;
        }
        URI uri = allServers.get(0);
        HttpGet httpGet = new HttpGet(uri);

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout((int) PING_REQUEST_TIMEOUT.toMillis())
                .setConnectionRequestTimeout((int) PING_REQUEST_TIMEOUT.toMillis())
                .setSocketTimeout((int) PING_REQUEST_TIMEOUT.toMillis()).build();
        httpGet.setConfig(config);

        try (CloseableHttpResponse response = client.execute(httpGet)) {
            if (response.getStatusLine().getStatusCode() == 200) {
                return false;
            }

        } catch (IOException e) {
            // We ignore this, if server is restarting this might happen.
        }
        // Some error happened, move this server to the back. The other servers should be running.
        Collections.rotate(allServers, -1);
        return true;
    }

    @Override
    public void deconstruct() {
        try {
            client.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CloseableHttpClient createHttpClient(SSLConnectionSocketFactory connectionSocketFactory,
                                                        org.apache.http.ConnectionReuseStrategy connectionReuseStrategy) {

        RequestConfig config = RequestConfig.custom()
                                            .setConnectTimeout((int) PROXY_REQUEST_TIMEOUT.toMillis())
                                            .setConnectionRequestTimeout((int) PROXY_REQUEST_TIMEOUT.toMillis())
                                            .setSocketTimeout((int) PROXY_REQUEST_TIMEOUT.toMillis()).build();
        return HttpClientBuilder.create()
                                .setUserAgent("config-server-proxy-client")
                                .setSSLSocketFactory(connectionSocketFactory)
                                .setDefaultRequestConfig(config)
                                .setMaxConnPerRoute(10)
                                .setMaxConnTotal(500)
                                .setConnectionReuseStrategy(connectionReuseStrategy)
                                .setConnectionTimeToLive(1, TimeUnit.MINUTES)
                                .build();

    }

    private static class ControllerOrConfigserverHostnameVerifier implements HostnameVerifier {

        private final HostnameVerifier configserverVerifier;

        ControllerOrConfigserverHostnameVerifier(ZoneRegistry registry) {
            this.configserverVerifier = createConfigserverVerifier(registry);
        }

        private static HostnameVerifier createConfigserverVerifier(ZoneRegistry registry) {
            Set<AthenzIdentity> configserverIdentities = registry.zones().all().zones().stream()
                    .map(zone -> registry.getConfigServerHttpsIdentity(zone.getId()))
                    .collect(Collectors.toSet());
            return new AthenzIdentityVerifier(configserverIdentities);
        }

        @Override
        public boolean verify(String hostname, SSLSession session) {
            return "localhost".equals(hostname) || configserverVerifier.verify(hostname, session);
        }
    }

    /**
     * A connection reuse strategy which avoids reusing connections to VIPs. Since VIPs are TCP-level load balancers,
     * a reconnect is needed to (potentially) switch real server.
     */
    public static class ConnectionReuseStrategy extends DefaultConnectionReuseStrategy {

        private final Set<String> vips;

        public ConnectionReuseStrategy(ZoneRegistry zoneRegistry) {
            this(zoneRegistry.zones().all().ids().stream()
                             .map(zoneRegistry::getConfigServerVipUri)
                             .map(URI::getHost)
                             .collect(Collectors.toUnmodifiableSet()));
        }

        public ConnectionReuseStrategy(Set<String> vips) {
            this.vips = Set.copyOf(vips);
        }

        @Override
        public boolean keepAlive(HttpResponse response, HttpContext context) {
            HttpCoreContext coreContext = HttpCoreContext.adapt(context);
            String host = coreContext.getTargetHost().getHostName();
            if (vips.contains(host)) {
                return false;
            }
            return super.keepAlive(response, context);
        }

    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.status;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.lang.CachedSupplier;
import com.yahoo.routing.config.ZoneConfig;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.yolean.Exceptions;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.NoopUserTokenHandler;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Caching client for the /routing/v1/status API on the config server. That API decides if a deployment (or entire zone)
 * is explicitly disabled in any global endpoints.
 *
 * This caches the status for a brief period to avoid drowning config servers with requests from health check pollers.
 *
 * @author oyving
 * @author andreer
 * @author mpolden
 */
public class RoutingStatusClient extends AbstractComponent implements RoutingStatus {

    private static final Logger log = Logger.getLogger(RoutingStatusClient.class.getName());
    private static final Duration requestTimeout = Duration.ofSeconds(2);
    private static final Duration cacheTtl = Duration.ofSeconds(5);

    private final CloseableHttpClient httpClient;
    private final URI configServerVip;
    private final CachedSupplier<Status> cache = new CachedSupplier<>(this::status, cacheTtl);

    @Inject
    public RoutingStatusClient(ZoneConfig config, ServiceIdentityProvider provider) {
        this(
                HttpClientBuilder.create()
                                 .setDefaultRequestConfig(RequestConfig.custom()
                                                                       .setConnectTimeout((int) requestTimeout.toMillis())
                                                                       .setConnectionRequestTimeout((int) requestTimeout.toMillis())
                                                                       .setSocketTimeout((int) requestTimeout.toMillis())
                                                                       .build())
                                 .setSSLContext(provider.getIdentitySslContext())
                                 .setSSLHostnameVerifier(createHostnameVerifier(config))
                                 // Required to enable connection pooling, which is disabled by default when using mTLS
                                 .setUserTokenHandler(NoopUserTokenHandler.INSTANCE)
                                 .setUserAgent("hosted-vespa-routing-status-client")
                                 .build(),
                URI.create(config.configserverVipUrl())
        );
    }

    public RoutingStatusClient(CloseableHttpClient httpClient, URI configServerVip) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.configServerVip = Objects.requireNonNull(configServerVip);
    }

    @Override
    public boolean isActive(String upstreamName) {
        try {
            return cache.get().isActive(upstreamName);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to get status for '" + upstreamName + "': " + Exceptions.toMessageString(e));
            return true; // Assume IN if cache update fails
        }
    }

    @Override
    public void deconstruct() {
        Exceptions.uncheck(httpClient::close);
    }

    void invalidateCache() {
        cache.invalidate();
    }

    private Status status() {
        Slime slime = get("/routing/v2/status");
        Cursor root = slime.get();
        Set<String> inactiveDeployments = SlimeUtils.entriesStream(root.field("inactiveDeployments"))
                                                    .map(inspector -> inspector.field("upstreamName").asString())
                                                    .collect(Collectors.toUnmodifiableSet());
        boolean zoneActive = root.field("zoneActive").asBool();
        return new Status(zoneActive, inactiveDeployments);
    }

    private Slime get(String path) {
        URI url = configServerVip.resolve(path);
        HttpGet httpGet = new HttpGet(url);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            String entity = Exceptions.uncheck(() -> EntityUtils.toString(response.getEntity()));
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                throw new IllegalArgumentException("Got status code " + response.getStatusLine().getStatusCode() +
                                                   " for URL " + url + ", with response: " + entity);
            }
            return SlimeUtils.jsonToSlime(entity);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static AthenzIdentityVerifier createHostnameVerifier(ZoneConfig config) {
        return new AthenzIdentityVerifier(Set.of(new AthenzService(config.configserverAthenzDomain(),
                                                                   config.configserverAthenzServiceName())));
    }

    private static class Status {

        private final boolean zoneActive;
        private final Set<String> inactiveDeployments;

        public Status(boolean zoneActive, Set<String> inactiveDeployments) {
            this.zoneActive = zoneActive;
            this.inactiveDeployments = Set.copyOf(Objects.requireNonNull(inactiveDeployments));
        }

        public boolean isActive(String upstreamName) {
            return zoneActive && !inactiveDeployments.contains(upstreamName);
        }

    }

}

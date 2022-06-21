// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.nginx;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.lang.CachedSupplier;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.routing.status.HealthStatus;
import com.yahoo.vespa.hosted.routing.status.ServerGroup;
import com.yahoo.yolean.Exceptions;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Client for the Nginx upstream health status page served at /health-status.
 *
 * @author oyving
 * @author mpolden
 */
public class NginxHealthClient extends AbstractComponent implements HealthStatus {

    private static final URI healthStatusUrl = URI.create("http://localhost:4080/health-status/?format=json");
    private static final Duration requestTimeout = Duration.ofSeconds(5);
    private static final Duration cacheTtl = Duration.ofSeconds(5);

    private final CloseableHttpClient httpClient;
    private final CachedSupplier<ServerGroup> cache = new CachedSupplier<>(this::getStatus, cacheTtl);

    @Inject
    public NginxHealthClient() {
        this(
                HttpClientBuilder.create()
                                 .setDefaultRequestConfig(RequestConfig.custom()
                                                                       .setConnectTimeout((int) requestTimeout.toMillis())
                                                                       .setConnectionRequestTimeout((int) requestTimeout.toMillis())
                                                                       .setSocketTimeout((int) requestTimeout.toMillis())
                                                                       .build())
                                 .build()
        );
    }

    NginxHealthClient(CloseableHttpClient client) {
        this.httpClient = Objects.requireNonNull(client);
    }

    @Override
    public ServerGroup servers() {
        return cache.get();
    }

    private ServerGroup getStatus() {
        HttpGet httpGet = new HttpGet(healthStatusUrl);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            String entity = Exceptions.uncheck(() -> EntityUtils.toString(response.getEntity()));
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                throw new IllegalArgumentException("Got status code " + response.getStatusLine().getStatusCode() +
                                                   " for URL " + healthStatusUrl + ", with response: " + entity);
            }
            return parseStatus(entity);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ServerGroup parseStatus(String json) {
        Slime slime = SlimeUtils.jsonToSlime(json);
        Cursor root = slime.get();
        List<ServerGroup.Server> servers = new ArrayList<>();
        Cursor serversObject = root.field("servers");
        Cursor serverArray = serversObject.field("stream");

        serverArray.traverse((ArrayTraverser) (idx, inspector) -> {
            String upstreamName = inspector.field("upstream").asString();
            String hostPort = inspector.field("name").asString();
            boolean up = "up".equals(inspector.field("status").asString());
            servers.add(new ServerGroup.Server(upstreamName, hostPort, up));
        });
        return new ServerGroup(servers);
    }

    @Override
    public void deconstruct() {
        Exceptions.uncheck(httpClient::close);
    }

}

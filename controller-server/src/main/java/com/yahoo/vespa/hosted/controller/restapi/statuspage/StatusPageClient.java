// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.statuspage;

import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * A basic client for the StatusPage API.
 *
 * @author mpolden
 */
public class StatusPageClient {

    private static final Duration requestTimeout = Duration.ofSeconds(30);

    private final URI url;
    private final String key;

    private StatusPageClient(URI url, String key) {
        this.url = Objects.requireNonNull(url, "url cannot be null");
        this.key = Objects.requireNonNull(key, "key cannot be null");
    }

    /** GET given page and return response body as slime */
    public Slime get(String page, Optional<String> since) {
        HttpGet get = new HttpGet(pageUrl(page, since));
        try (CloseableHttpClient client = client()) {
            try (CloseableHttpResponse response = client.execute(get)) {
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    throw new IllegalArgumentException("Received status " + response.getStatusLine().getStatusCode() +
                                                       " from StatusPage");
                }
                byte[] body = EntityUtils.toByteArray(response.getEntity());
                return SlimeUtils.jsonToSlime(body);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    URI pageUrl(String page, Optional<String> since) {
        if (!allowAccess(page)) {
            throw new IllegalArgumentException("Invalid resource: '" + page + "'");
        }
        URIBuilder builder = new URIBuilder(url)
                .setPath("/api/v2/" + page + ".json")
                .setParameter("api_key", key);
        since.ifPresent(s -> builder.setParameter("since", s));
        try {
            return builder.build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static StatusPageClient create(URI url, String secret) {
        String[] parts = secret.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid secret");
        }
        String pageId = parts[0];
        String apiKey = parts[1];
        if (isDefault(url)) {
            // Rewrite URL to include page ID
            try {
                url = new URIBuilder(url).setHost(pageId + "." + url.getHost()).build();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return new StatusPageClient(url, apiKey);
    }

    private static CloseableHttpClient client() {
        HttpClientBuilder builder = HttpClients.custom();
        RequestConfig requestConfig = RequestConfig.custom()
                                                   .setConnectionRequestTimeout((int) requestTimeout.toMillis())
                                                   .setConnectTimeout((int) requestTimeout.toMillis())
                                                   .setSocketTimeout((int) requestTimeout.toMillis())
                                                   .build();
        return builder.setDefaultRequestConfig(requestConfig)
                      .setUserAgent("vespa-statuspage-client")
                      .build();
    }

    /** Returns whether given page is allowed to be accessed */
    private static boolean allowAccess(String page) {
        switch (page) {
            case "incidents":
            case "scheduled-maintenances":
                return true;
        }
        return false;
    }

    private static boolean isDefault(URI url) {
        return "statuspage.io".equals(url.getHost());
    }

}

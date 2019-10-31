// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpRequest;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.yahoo.jdisc.http.HttpRequest.Method;

/**
 * Keeping information about the calls that are being proxied.
 * A request is of form /zone/v2/[environment]/[region]/[config-server-path]
 *
 * @author Haakon Dybdahl
 */
public class ProxyRequest {

    private final Method method;
    private final URI requestUri;
    private final Map<String, List<String>> headers;
    private final InputStream requestData;

    private final ZoneId zoneId;
    private final String proxyPath;

    /**
     * The constructor calls exception if the request is invalid.
     *
     * @param request the request from the jdisc framework.
     * @param zoneId the zone to proxy to.
     * @param proxyPath the path to proxy to.
     * @throws ProxyException on errors
     */
    public ProxyRequest(HttpRequest request, ZoneId zoneId, String proxyPath) throws ProxyException {
        this(request.getMethod(), request.getUri(), request.getJDiscRequest().headers(), request.getData(),
             zoneId, proxyPath);
    }

    ProxyRequest(Method method, URI requestUri, Map<String, List<String>> headers, InputStream body,
                 ZoneId zoneId, String proxyPath) throws ProxyException {
        Objects.requireNonNull(requestUri, "Request must be non-null");
        if (!requestUri.getPath().endsWith(proxyPath))
            throw new ProxyException(ErrorResponse.badRequest(String.format(
                    "Request path '%s' does not end with proxy path '%s'", requestUri.getPath(), proxyPath)));

        this.method = Objects.requireNonNull(method);
        this.requestUri = Objects.requireNonNull(requestUri);
        this.headers = Objects.requireNonNull(headers);
        this.requestData = body;

        this.zoneId = Objects.requireNonNull(zoneId);
        this.proxyPath = proxyPath.startsWith("/") ? proxyPath : "/" + proxyPath;
    }


    public Method getMethod() {
        return method;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public InputStream getData() {
        return requestData;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public URI createConfigServerRequestUri(URI baseURI) {
        try {
            return new URI(baseURI.getScheme(), baseURI.getUserInfo(), baseURI.getHost(),
                    baseURI.getPort(), proxyPath, requestUri.getQuery(), requestUri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public URI getControllerPrefixUri() {
        String prefixPath = proxyPath.equals("/") && !requestUri.getPath().endsWith("/") ?
                requestUri.getPath() + proxyPath :
                requestUri.getPath().substring(0, requestUri.getPath().length() - proxyPath.length() + 1);
        try {
            return new URI(requestUri.getScheme(), requestUri.getUserInfo(), requestUri.getHost(),
                    requestUri.getPort(), prefixPath, null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "[zone: " + zoneId + " request: " + proxyPath + "]";
    }

}

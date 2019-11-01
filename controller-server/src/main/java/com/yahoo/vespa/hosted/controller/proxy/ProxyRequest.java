// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

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

    private final List<URI> targets;
    private final String targetPath;

    /**
     * The constructor calls exception if the request is invalid.
     *
     * @param request the request from the jdisc framework.
     * @param targets list of targets this request should be proxied to (targets are tried once in order until a response is returned).
     * @param targetPath the path to proxy to.
     * @throws ProxyException on errors
     */
    public ProxyRequest(HttpRequest request, List<URI> targets, String targetPath) throws ProxyException {
        this(request.getMethod(), request.getUri(), request.getJDiscRequest().headers(), request.getData(),
             targets, targetPath);
    }

    ProxyRequest(Method method, URI requestUri, Map<String, List<String>> headers, InputStream body,
                 List<URI> targets, String targetPath) throws ProxyException {
        Objects.requireNonNull(requestUri, "Request must be non-null");
        if (!requestUri.getPath().endsWith(targetPath))
            throw new ProxyException(ErrorResponse.badRequest(String.format(
                    "Request path '%s' does not end with proxy path '%s'", requestUri.getPath(), targetPath)));

        this.method = Objects.requireNonNull(method);
        this.requestUri = Objects.requireNonNull(requestUri);
        this.headers = Objects.requireNonNull(headers);
        this.requestData = body;

        this.targets = List.copyOf(targets);
        this.targetPath = targetPath.startsWith("/") ? targetPath : "/" + targetPath;
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

    public List<URI> getTargets() {
        return targets;
    }

    public URI createConfigServerRequestUri(URI baseURI) {
        try {
            return new URI(baseURI.getScheme(), baseURI.getUserInfo(), baseURI.getHost(),
                    baseURI.getPort(), targetPath, requestUri.getQuery(), requestUri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public URI getControllerPrefixUri() {
        String prefixPath = targetPath.equals("/") && !requestUri.getPath().endsWith("/") ?
                requestUri.getPath() + targetPath :
                requestUri.getPath().substring(0, requestUri.getPath().length() - targetPath.length() + 1);
        try {
            return new URI(requestUri.getScheme(), requestUri.getUserInfo(), requestUri.getHost(),
                    requestUri.getPort(), prefixPath, null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "[targets: " + targets + " request: " + targetPath + "]";
    }

}

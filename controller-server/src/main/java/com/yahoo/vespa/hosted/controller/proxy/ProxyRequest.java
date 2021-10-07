// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import com.yahoo.container.jdisc.HttpRequest;

import com.yahoo.text.Text;
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

    ProxyRequest(Method method, URI url, Map<String, List<String>> headers, InputStream body, List<URI> targets,
                 String path) {
        Objects.requireNonNull(url);
        if (!url.getPath().endsWith(path)) {
            throw new IllegalArgumentException(Text.format("Request path '%s' does not end with proxy path '%s'", url.getPath(), path));
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("targets must be non-empty");
        }
        this.method = Objects.requireNonNull(method);
        this.requestUri = Objects.requireNonNull(url);
        this.headers = Objects.requireNonNull(headers);
        this.requestData = body;
        this.targets = List.copyOf(targets);
        this.targetPath = path.startsWith("/") ? path : "/" + path;
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

    /** Create a proxy request that repeatedly tries a single target */
    public static ProxyRequest tryOne(URI target, String path, HttpRequest request) {
        return new ProxyRequest(request.getMethod(), request.getUri(), request.getJDiscRequest().headers(),
                                request.getData(), List.of(target), path);
    }

}

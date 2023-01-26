// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;
import ai.vespa.http.HttpURL.Path;
import ai.vespa.http.HttpURL.Query;
import ai.vespa.http.HttpURL.Scheme;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.security.NodeHostnameVerifier;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.http.HttpFetcher;
import com.yahoo.vespa.config.server.http.HttpFetcher.Params;
import com.yahoo.vespa.config.server.http.NotFoundException;
import com.yahoo.vespa.config.server.http.SimpleHttpFetcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpProxy {

    private static final Logger logger = Logger.getLogger(HttpProxy.class.getName());

    private final HttpFetcher fetcher;

    @Inject public HttpProxy(NodeHostnameVerifier verifier) { this(new SimpleHttpFetcher(verifier)); }

    public HttpProxy(HttpFetcher fetcher) { this.fetcher = fetcher; }

    public HttpResponse get(Application application, String hostName, String serviceType, Path path, Query query) {
        return get(application, hostName, serviceType, path, query, null);
    }

    public HttpResponse get(Application application, String hostName, String serviceType, Path path, Query query, HttpURL forwardedUrl) {
        HostInfo host = application.getModel().getHosts().stream()
                .filter(hostInfo -> hostInfo.getHostname().equals(hostName))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Failed to find host " + hostName));

        ServiceInfo service = host.getServices().stream()
                .filter(serviceInfo -> serviceType.equals(serviceInfo.getServiceType()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Failed to find any service of type " + serviceType
                        + " on host " + hostName));

        // "http" and "state" seems to uniquely identify an interesting HTTP port on each service
        PortInfo port = service.getPorts().stream()
                               .filter(portInfo -> portInfo.getTags().containsAll(List.of("http", "state")))
                               .findFirst()
                               .orElseThrow(() -> new NotFoundException("Failed to find HTTP state port"));

        HttpURL url = HttpURL.create(Scheme.http, DomainName.of(host.getHostname()), port.getPort(), path, query);
        HttpResponse response = fetcher.get(new Params(29_000), // 29 sec (30 sec on controller)
                                            url.asURI());
        return forwardedUrl == null ? response : new UrlRewritingProxyResponse(response, url, forwardedUrl);
    }

    static class UrlRewritingProxyResponse extends HttpResponse {

        final HttpResponse wrapped;
        final String patten;
        final String replacement;

        public UrlRewritingProxyResponse(HttpResponse wrapped, HttpURL requestUrl, HttpURL forwardedUrl) {
            super(wrapped.getStatus());
            this.wrapped = wrapped;
            this.patten = requestUrl.withPath(requestUrl.path().withoutTrailingSlash()).withQuery(Query.empty()).asURI().toString();
            this.replacement = forwardedUrl.withPath(forwardedUrl.path().withoutTrailingSlash()).withQuery(Query.empty()).asURI().toString();
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            wrapped.render(buffer);
            outputStream.write(buffer.toString(Charset.forName(wrapped.getCharacterEncoding()))
                                     .replace(patten, replacement)
                                     .getBytes(UTF_8));
        }

        @Override
        public String getContentType() {
            return wrapped.getContentType();
        }

    }

}

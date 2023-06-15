// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.util;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.jdisc.http.HttpRequest.Version;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.filter.SecurityResponseFilter;
import com.yahoo.jdisc.http.server.jetty.RequestUtils;
import com.yahoo.jdisc.service.CurrentContainer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.yahoo.jdisc.http.HttpRequest.Version.HTTP_1_1;
import static com.yahoo.jdisc.http.server.jetty.AccessLoggingRequestHandler.CONTEXT_KEY_ACCESS_LOG_ENTRY;

/**
 * Test helper for {@link SecurityRequestFilter}/{@link SecurityResponseFilter}.
 *
 * @author bjorncs
 */
public class FilterTestUtils {

    private FilterTestUtils() {}

    public static RequestBuilder newRequestBuilder() { return new RequestBuilder(); }

    public static class RequestBuilder {

        private URI uri = URI.create("https://localhost:443/");
        private Method method = Method.GET;
        private Clock clock = Clock.systemUTC();
        private Principal principal;
        private final Map<String, Object> attributes = new TreeMap<>();
        private List<X509Certificate> certificates = List.of();
        private final Map<String, String> headers = new TreeMap<>();
        private Version version = HTTP_1_1;
        private SocketAddress remoteAddress;
        private AccessLogEntry accessLogEntry;
        private final List<Cookie> cookies = new ArrayList<>();

        private RequestBuilder() {}

        public RequestBuilder withUri(String uri) { return withUri(URI.create(uri)); }
        public RequestBuilder withUri(URI uri) { this.uri = uri; return this; }
        public RequestBuilder withMethod(String method) { return withMethod(Method.valueOf(method)); }
        public RequestBuilder withMethod(Method method) { this.method = method; return this; }
        public RequestBuilder withClock(Clock clock) { this.clock = clock; return this; }
        public RequestBuilder withPrincipal(Principal principal) { this.principal = principal; return this; }
        public RequestBuilder withAttribute(String name, Object value) { attributes.put(name, value); return this; }
        public RequestBuilder withClientCertificate(X509Certificate cert) { return withClientCertificate(List.of(cert)); }
        public RequestBuilder withClientCertificate(List<X509Certificate> certs) { certificates = List.copyOf(certs); return this; }
        public RequestBuilder withHeader(String name, String value) { headers.put(name, value); return this; }
        public RequestBuilder withHttpVersion(Version version) { this.version = version; return this; }
        public RequestBuilder withRemoteAddress(String host, int port) { return withRemoteAddress(new InetSocketAddress(host, port)); }
        public RequestBuilder withRemoteAddress(SocketAddress address) { this.remoteAddress = address; return this; }
        public RequestBuilder withCookie(String cookie) { cookies.addAll(Cookie.fromCookieHeader(cookie)); return this; }
        public RequestBuilder withCookie(Cookie cookie) { cookies.add(cookie); return this; }
        public RequestBuilder withAccessLogEntry(AccessLogEntry entry) { this.accessLogEntry = entry; return this; }

        public DiscFilterRequest build() {
            var httpReq = HttpRequest.newServerRequest(
                    new DummyContainer(clock), uri, method, version, remoteAddress, clock.millis(), clock.millis());
            var filterReq = new DiscFilterRequest(httpReq);
            filterReq.setUserPrincipal(principal);
            filterReq.setAttribute(CONTEXT_KEY_ACCESS_LOG_ENTRY, accessLogEntry != null ? accessLogEntry : new AccessLogEntry());
            attributes.forEach(filterReq::setAttribute);
            filterReq.setAttribute(RequestUtils.JDISC_REQUEST_X509CERT, certificates.toArray(X509Certificate[]::new));
            headers.forEach(filterReq::addHeader);
            filterReq.setCookies(cookies);
            return filterReq;
        }
    }

    private record DummyContainer(Clock clock) implements CurrentContainer, Container, RequestHandler {
        @Override public RequestHandler resolveHandler(Request request) { return this; }
        @Override public <T> T getInstance(Class<T> type) { throw new UnsupportedOperationException(); }
        @Override public void release() {}
        @Override public long currentTimeMillis() { return clock.millis(); }
        @Override public ContentChannel handleRequest(Request request, ResponseHandler handler) { throw new UnsupportedOperationException(); }
        @Override public void handleTimeout(Request request, ResponseHandler handler) { throw new UnsupportedOperationException(); }
        @Override public Container newReference(URI uri, Object context) { return this; }
        @Override public Container newReference(URI uri) { return this; }
        @Override public ResourceReference refer(Object context) { return References.NOOP_REFERENCE; }
        @Override public ResourceReference refer() { return References.NOOP_REFERENCE; }
        @Override public Instant currentTime() { return clock.instant(); }
    }
}

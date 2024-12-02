// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A HTTP request.
 *
 * @author Anirudha Khanna
 * @author Einar M R Rosenvinge
 */
public class HttpRequest extends Request {

    public enum Method {
        OPTIONS,
        GET,
        HEAD,
        POST,
        PUT,
        PATCH,
        DELETE,
        TRACE,
        CONNECT
    }

    public enum Version {
        HTTP_1_0("HTTP/1.0"),
        HTTP_1_1("HTTP/1.1"),
        HTTP_2_0("HTTP/2.0");

        private final String str;

        Version(String str) {
            this.str = str;
        }

        @Override
        public String toString() {
            return str;
        }

        public static Version fromString(String str) {
            for (Version version : values()) {
                if (version.str.equals(str)) {
                    return version;
                }
            }
            throw new IllegalArgumentException(str);
        }
    }

    private final long jvmRelativeCreatedAt = System.nanoTime();
    private final HeaderFields trailers = new HeaderFields();
    private final Map<String, List<String>> parameters = new HashMap<>();
    private volatile Principal principal;
    private final long connectedAt;
    private Method method;
    private Version version;
    private SocketAddress remoteAddress;
    private URI proxyServer;
    private Long connectionTimeout;

    protected HttpRequest(CurrentContainer container, URI uri, Method method, Version version,
                          SocketAddress remoteAddress, Long connectedAtMillis, long creationTime)
    {
        super(container, uri, true, creationTime);
        try {
            this.method = method;
            this.version = version;
            this.remoteAddress = remoteAddress;
            this.parameters.putAll(getUriQueryParameters(uri));
            if (connectedAtMillis != null) {
                this.connectedAt = connectedAtMillis;
            } else {
                this.connectedAt = creationTime(TimeUnit.MILLISECONDS);
            }
        } catch (Throwable e) {
            release();
            throw e;
        }
    }

    private HttpRequest(Request parent, URI uri, Method method, Version version) {
        super(parent, uri);
        try {
            this.method = method;
            this.version = version;
            this.remoteAddress = null;
            this.parameters.putAll(getUriQueryParameters(uri));
            this.connectedAt = creationTime(TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            release();
            throw e;
        }
    }

    private static Map<String, List<String>> getUriQueryParameters(URI uri) {
        if (uri.getRawQuery() == null) return Map.of();
        MultiMap<String> params = new MultiMap<>();
        UrlEncoded.decodeUtf8To(uri.getRawQuery(), params);
        return Map.copyOf(params);
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Version getVersion() {
        return version;
    }

    /** Returns the remote address, or null if unresolved */
    public String getRemoteHostAddress() {
        if (remoteAddress instanceof InetSocketAddress) {
            InetAddress remoteInetAddress =  ((InetSocketAddress) remoteAddress).getAddress();
            if (remoteInetAddress == null)
                return null;
            return remoteInetAddress.getHostAddress();
        }
        else {
            throw new RuntimeException("Unknown SocketAddress class: " + remoteAddress.getClass().getName());
        }
    }

    public String getRemoteHostName() {
        if (remoteAddress instanceof InetSocketAddress) {
            InetAddress remoteInetAddress = ((InetSocketAddress) remoteAddress).getAddress();
            if (remoteInetAddress == null) return null; // not resolved; we have no network
            return remoteInetAddress.getHostName();
        }
        else {
            throw new RuntimeException("Unknown SocketAddress class: " + remoteAddress.getClass().getName());
        }
    }

    public int getRemotePort() {
        if (remoteAddress instanceof InetSocketAddress)
            return ((InetSocketAddress) remoteAddress).getPort();
        else
            throw new RuntimeException("Unknown SocketAddress class: " + remoteAddress.getClass().getName());
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public URI getProxyServer() {
        return proxyServer;
    }

    public void setProxyServer(URI proxyServer) {
        this.proxyServer = proxyServer;
    }

    /**
     * <p>For server requests, this returns the timestamp of when the underlying HTTP channel was connected.
     *
     * <p>For client requests, this returns the same value as {@link #creationTime(java.util.concurrent.TimeUnit)}.</p>
     *
     * @param unit the unit to return the time in
     * @return the timestamp of when the underlying HTTP channel was connected, or request creation time
     */
    public long getConnectedAt(TimeUnit unit) {
        return unit.convert(connectedAt, TimeUnit.MILLISECONDS);
    }

    public Long getConnectionTimeout(TimeUnit unit) {
        if (connectionTimeout == null) {
            return null;
        }
        return unit.convert(connectionTimeout, TimeUnit.MILLISECONDS);
    }

    /**
     * <p>Sets the allocated time that this HttpRequest is allowed to spend trying to connect to a remote host. This has
     * no effect on an HttpRequest received by a {@link RequestHandler}. If no connection timeout is assigned to an
     * HttpRequest, it defaults the connection-timeout in the client configuration.</p>
     *
     * <p><b>NOTE:</b> Where {@link Request#setTimeout(long, TimeUnit)} sets the expiration time between calling a
     * RequestHandler and a {@link ResponseHandler}, this method sets the expiration time of the connect-operation as
     * performed by the client.</p>
     *
     * @param timeout The allocated amount of time.
     * @param unit    The time unit of the <em>timeout</em> argument.
     */
    public void setConnectionTimeout(long timeout, TimeUnit unit) {
        this.connectionTimeout = unit.toMillis(timeout);
    }

    public Map<String, List<String>> parameters() {
        return parameters;
    }

    public void copyHeaders(HeaderFields target) {
        target.addAll(headers());
    }

    public List<Cookie> decodeCookieHeader() {
        List<String> cookies = headers().get(HttpHeaders.Names.COOKIE);
        if (cookies == null) {
            return List.of();
        }
        List<Cookie> ret = new LinkedList<>();
        for (String cookie : cookies) {
            ret.addAll(Cookie.fromCookieHeader(cookie));
        }
        return ret;
    }

    public void encodeCookieHeader(List<Cookie> cookies) {
        headers().put(HttpHeaders.Names.COOKIE, Cookie.toCookieHeader(cookies));
    }

    /**
     * <p>Returns the set of trailer header fields of this HttpRequest. These are typically meta-data that should have
     * been part of {@link #headers()}, but were not available prior to calling {@link #connect(ResponseHandler)}. You
     * must NOT WRITE to these headers AFTER calling {@link ContentChannel#close(CompletionHandler)}, and you must NOT
     * READ from these headers BEFORE {@link ContentChannel#close(CompletionHandler)} has been called.</p>
     *
     * <p><b>NOTE:</b> These headers are NOT thread-safe. You need to explicitly synchronized on the returned object to
     * prevent concurrency issues such as ConcurrentModificationExceptions.</p>
     *
     * @return The trailer headers of this HttpRequest.
     */
    public HeaderFields trailers() {
        return trailers;
    }

    /**
     * Returns whether this request was <em>explicitly</em> chunked from the client.&nbsp;NOTE that there are cases
     * where the underlying HTTP server library (Netty for the time being) will read the request in a chunked manner. An
     * application MUST wait for {@link com.yahoo.jdisc.handler.ContentChannel#close(com.yahoo.jdisc.handler.CompletionHandler)}
     * before it can actually know that it has received the entire request.
     *
     * @return true if this request was chunked from the client.
     */
    public boolean isChunked() {
        return version == Version.HTTP_1_1 &&
               headers().containsIgnoreCase(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
    }

    public boolean hasChunkedResponse() {
        return version == Version.HTTP_1_1 &&
               !headers().isTrue(HttpHeaders.Names.X_DISABLE_CHUNKING);
    }

    public boolean isKeepAlive() {
        if (headers().containsIgnoreCase(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)) {
            return true;
        }
        if (headers().containsIgnoreCase(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)) {
            return false;
        }
        return version == Version.HTTP_1_1;
    }

    /**
     * @return the relative created timestamp (using {@link System#nanoTime()}
     */
    public long relativeCreatedAtNanoTime() { return jvmRelativeCreatedAt; }

    public Principal getUserPrincipal() {
        return principal;
    }

    public void setUserPrincipal(Principal principal) {
        this.principal = principal;
    }

    public static HttpRequest newServerRequest(CurrentContainer container, URI uri) {
        return newServerRequest(container, uri, Method.GET);
    }

    public static HttpRequest newServerRequest(CurrentContainer container, URI uri, Method method) {
        return newServerRequest(container, uri, method, Version.HTTP_1_1);
    }

    public static HttpRequest newServerRequest(CurrentContainer container, URI uri, Method method, Version version) {
        return newServerRequest(container, uri, method, version, null);
    }

    public static HttpRequest newServerRequest(CurrentContainer container, URI uri, Method method, Version version,
                                               SocketAddress remoteAddress) {
        return new HttpRequest(container, uri, method, version, remoteAddress, null, -1);
    }

    public static HttpRequest newServerRequest(CurrentContainer container, URI uri, Method method, Version version,
                                               SocketAddress remoteAddress, long connectedAtMillis, long creationTime)
    {
        return new HttpRequest(container, uri, method, version, remoteAddress, connectedAtMillis, creationTime);
    }

    public static HttpRequest newClientRequest(Request parent, URI uri) {
        return newClientRequest(parent, uri, Method.GET);
    }

    public static HttpRequest newClientRequest(Request parent, URI uri, Method method) {
        return newClientRequest(parent, uri, method, Version.HTTP_1_1);
    }

    public static HttpRequest newClientRequest(Request parent, URI uri, Method method, Version version) {
        return new HttpRequest(parent, uri, method, version);
    }

}

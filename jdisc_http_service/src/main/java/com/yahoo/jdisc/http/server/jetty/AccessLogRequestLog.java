// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.base.Objects;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.container.logging.RequestLogEntry;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.servlet.ServletRequest;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.server.jetty.HttpServletRequestUtils.getConnectorLocalPort;

/**
 * This class is a bridge between Jetty's {@link org.eclipse.jetty.server.handler.RequestLogHandler}
 * and our own configurable access logging in different formats provided by {@link AccessLog}.
 *
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
class AccessLogRequestLog extends AbstractLifeCycle implements org.eclipse.jetty.server.RequestLog {

    private static final Logger logger = Logger.getLogger(AccessLogRequestLog.class.getName());

    // HTTP headers that are logged as extra key-value-pairs in access log entries
    private static final List<String> LOGGED_REQUEST_HEADERS = List.of("Vespa-Client-Version");

    private final RequestLog requestLog;
    private final List<String> remoteAddressHeaders;
    private final List<String> remotePortHeaders;

    AccessLogRequestLog(RequestLog requestLog, ServerConfig.AccessLog config) {
        this.requestLog = requestLog;
        this.remoteAddressHeaders = config.remoteAddressHeaders();
        this.remotePortHeaders = config.remotePortHeaders();
    }

    @Override
    public void log(Request request, Response response) {
        try {
            RequestLogEntry.Builder builder = new RequestLogEntry.Builder();

            String peerAddress = request.getRemoteAddr();
            int peerPort = request.getRemotePort();
            long startTime = request.getTimeStamp();
            long endTime = System.currentTimeMillis();
            builder.peerAddress(peerAddress)
                    .peerPort(peerPort)
                    .localPort(getLocalPort(request))
                    .timestamp(Instant.ofEpochMilli(startTime))
                    .duration(Duration.ofMillis(Math.max(0, endTime - startTime)))
                    .contentSize(response.getHttpChannel().getBytesWritten())
                    .statusCode(response.getCommittedMetaData().getStatus());

            addNonNullValue(builder, request.getMethod(), RequestLogEntry.Builder::httpMethod);
            addNonNullValue(builder, request.getRequestURI(), RequestLogEntry.Builder::rawPath);
            addNonNullValue(builder, request.getProtocol(), RequestLogEntry.Builder::httpVersion);
            addNonNullValue(builder, request.getScheme(), RequestLogEntry.Builder::scheme);
            addNonNullValue(builder, request.getHeader("User-Agent"), RequestLogEntry.Builder::userAgent);
            addNonNullValue(builder, request.getHeader("Host"), RequestLogEntry.Builder::hostString);
            addNonNullValue(builder, request.getHeader("Referer"), RequestLogEntry.Builder::referer);
            addNonNullValue(builder, request.getQueryString(), RequestLogEntry.Builder::rawQuery);

            Principal principal = (Principal) request.getAttribute(ServletRequest.JDISC_REQUEST_PRINCIPAL);
            addNonNullValue(builder, principal, RequestLogEntry.Builder::userPrincipal);

            String requestFilterId = (String) request.getAttribute(ServletRequest.JDISC_REQUEST_CHAIN);
            addNonNullValue(builder, requestFilterId, (b, chain) -> b.addExtraAttribute("request-chain", chain));

            String responseFilterId = (String) request.getAttribute(ServletRequest.JDISC_RESPONSE_CHAIN);
            addNonNullValue(builder, responseFilterId, (b, chain) -> b.addExtraAttribute("response-chain", chain));

            UUID connectionId = (UUID) request.getAttribute(JettyConnectionLogger.CONNECTION_ID_REQUEST_ATTRIBUTE);
            addNonNullValue(builder, connectionId, (b, uuid) -> b.connectionId(uuid.toString()));

            String remoteAddress = getRemoteAddress(request);
            if (!Objects.equal(remoteAddress, peerAddress)) {
                builder.remoteAddress(remoteAddress);
            }
            int remotePort = getRemotePort(request);
            if (remotePort != peerPort) {
                builder.remotePort(remotePort);
            }
            LOGGED_REQUEST_HEADERS.forEach(header -> {
                String value = request.getHeader(header);
                if (value != null) {
                    builder.addExtraAttribute(header, value);
                }
            });
            X509Certificate[] clientCert = (X509Certificate[]) request.getAttribute(ServletRequest.SERVLET_REQUEST_X509CERT);
            if (clientCert != null && clientCert.length > 0) {
                builder.sslPrincipal(clientCert[0].getSubjectX500Principal());
            }

            AccessLogEntry accessLogEntry = (AccessLogEntry) request.getAttribute(JDiscHttpServlet.ATTRIBUTE_NAME_ACCESS_LOG_ENTRY);
            if (accessLogEntry != null) {
                var extraAttributes = accessLogEntry.getKeyValues();
                if (extraAttributes != null) {
                    extraAttributes.forEach(builder::addExtraAttributes);
                }
                addNonNullValue(builder, accessLogEntry.getHitCounts(), RequestLogEntry.Builder::hitCounts);
                addNonNullValue(builder, accessLogEntry.getTrace(), RequestLogEntry.Builder::traceNode);
            }

            requestLog.log(builder.build());
        } catch (Exception e) {
            // Catching any exceptions here as it is unclear how Jetty handles exceptions from a RequestLog.
            logger.log(Level.SEVERE, "Failed to log access log entry: " + e.getMessage(), e);
        }
    }

    private String getRemoteAddress(HttpServletRequest request) {
        for (String header : remoteAddressHeaders) {
            String value = request.getHeader(header);
            if (value != null) return value;
        }
        return request.getRemoteAddr();
    }

    private int getRemotePort(HttpServletRequest request) {
        for (String header : remotePortHeaders) {
            String value = request.getHeader(header);
            if (value != null) {
                OptionalInt maybePort = parsePort(value);
                if (maybePort.isPresent()) return maybePort.getAsInt();
            }
        }
        return request.getRemotePort();
    }

    private static int getLocalPort(Request request) {
        int connectorLocalPort = getConnectorLocalPort(request);
        if (connectorLocalPort <= 0) return request.getLocalPort(); // If connector is already closed
        return connectorLocalPort;
    }

    private static OptionalInt parsePort(String port) {
        try {
            return OptionalInt.of(Integer.parseInt(port));
        } catch (IllegalArgumentException e) {
            return OptionalInt.empty();
        }
    }

    private static <T> void addNonNullValue(
            RequestLogEntry.Builder builder, T value, BiConsumer<RequestLogEntry.Builder, T> setter) {
        if (value != null) {
            setter.accept(builder, value);
        }
    }

}

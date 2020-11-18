// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.base.Objects;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.servlet.ServletRequest;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.core.HttpServletRequestUtils.getConnectorLocalPort;

/**
 * This class is a bridge between Jetty's {@link org.eclipse.jetty.server.handler.RequestLogHandler}
 * and our own configurable access logging in different formats provided by {@link AccessLog}.
 *
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
class AccessLogRequestLog extends AbstractLifeCycle implements RequestLog {

    private static final Logger logger = Logger.getLogger(AccessLogRequestLog.class.getName());

    // HTTP headers that are logged as extra key-value-pairs in access log entries
    private static final List<String> LOGGED_REQUEST_HEADERS = List.of("Vespa-Client-Version");

    private final AccessLog accessLog;
    private final List<String> remoteAddressHeaders;
    private final List<String> remotePortHeaders;

    AccessLogRequestLog(AccessLog accessLog, ServerConfig.AccessLog config) {
        this.accessLog = accessLog;
        this.remoteAddressHeaders = config.remoteAddressHeaders();
        this.remotePortHeaders = config.remotePortHeaders();
    }

    @Override
    public void log(Request request, Response response) {
        try {
            AccessLogEntry accessLogEntry = Optional.ofNullable(request.getAttribute(JDiscHttpServlet.ATTRIBUTE_NAME_ACCESS_LOG_ENTRY))
                    .map(AccessLogEntry.class::cast)
                    .orElseGet(AccessLogEntry::new);

            accessLogEntry.setRawPath(request.getRequestURI());
            String queryString = request.getQueryString();
            if (queryString != null) {
                accessLogEntry.setRawQuery(queryString);
            }

            accessLogEntry.setUserAgent(request.getHeader("User-Agent"));
            accessLogEntry.setHttpMethod(request.getMethod());
            accessLogEntry.setHostString(request.getHeader("Host"));
            accessLogEntry.setReferer(request.getHeader("Referer"));

            String peerAddress = request.getRemoteAddr();
            accessLogEntry.setIpV4Address(peerAddress);
            accessLogEntry.setPeerAddress(peerAddress);
            String remoteAddress = getRemoteAddress(request);
            if (!Objects.equal(remoteAddress, peerAddress)) {
                accessLogEntry.setRemoteAddress(remoteAddress);
            }

            int peerPort = request.getRemotePort();
            accessLogEntry.setPeerPort(peerPort);
            int remotePort = getRemotePort(request);
            if (remotePort != peerPort) {
                accessLogEntry.setRemotePort(remotePort);
            }
            accessLogEntry.setHttpVersion(request.getProtocol());
            accessLogEntry.setScheme(request.getScheme());
            accessLogEntry.setLocalPort(getConnectorLocalPort(request));
            Principal principal = (Principal) request.getAttribute(ServletRequest.JDISC_REQUEST_PRINCIPAL);
            if (principal != null) {
                accessLogEntry.setUserPrincipal(principal);
            }
            X509Certificate[] clientCert = (X509Certificate[]) request.getAttribute(ServletRequest.SERVLET_REQUEST_X509CERT);
            if (clientCert != null && clientCert.length > 0) {
                accessLogEntry.setSslPrincipal(clientCert[0].getSubjectX500Principal());
            }
            String sslSessionId = (String) request.getAttribute(ServletRequest.SERVLET_REQUEST_SSL_SESSION_ID);
            if (sslSessionId != null) {
                accessLogEntry.addKeyValue("ssl-session-id", sslSessionId);
            }
            String cipherSuite = (String) request.getAttribute(ServletRequest.SERVLET_REQUEST_CIPHER_SUITE);
            if (cipherSuite != null) {
                accessLogEntry.addKeyValue("cipher-suite", cipherSuite);
            }
            String requestFilterId = (String) request.getAttribute(ServletRequest.JDISC_REQUEST_CHAIN);
            if (requestFilterId != null) {
                accessLogEntry.addKeyValue("request-chain", requestFilterId);
            }
            String responseFilterId = (String) request.getAttribute(ServletRequest.JDISC_RESPONSE_CHAIN);
            if (responseFilterId != null) {
                accessLogEntry.addKeyValue("response-chain", responseFilterId);
            }

            long startTime = request.getTimeStamp();
            long endTime = System.currentTimeMillis();
            accessLogEntry.setTimeStamp(startTime);
            accessLogEntry.setDurationBetweenRequestResponse(endTime - startTime);
            accessLogEntry.setReturnedContentSize(response.getHttpChannel().getBytesWritten());
            accessLogEntry.setStatusCode(response.getCommittedMetaData().getStatus());

            LOGGED_REQUEST_HEADERS.forEach(header -> {
                String value = request.getHeader(header);
                if (value != null) {
                    accessLogEntry.addKeyValue(header, value);
                }
            });

            accessLog.log(accessLogEntry);
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

    private static OptionalInt parsePort(String port) {
        try {
            return OptionalInt.of(Integer.parseInt(port));
        } catch (IllegalArgumentException e) {
            return OptionalInt.empty();
        }
    }

}

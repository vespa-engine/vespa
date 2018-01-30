// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.base.Objects;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.AccessLogEntry;

import com.yahoo.jdisc.http.servlet.ServletRequest;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

import javax.servlet.http.HttpServletRequest;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is a bridge between Jetty's {@link org.eclipse.jetty.server.handler.RequestLogHandler}
 * and our own configurable access logging in different formats provided by {@link AccessLog}.
 *
 * @author bakksjo
 * @author bjorncs
 */
public class AccessLogRequestLog extends AbstractLifeCycle implements RequestLog {

    private static final Logger logger = Logger.getLogger(AccessLogRequestLog.class.getName());

    private static final String HEADER_NAME_Y_RA = "y-ra";
    private static final String HEADER_NAME_Y_RP = "y-rp";
    private static final String HEADER_NAME_YAHOOREMOTEIP = "yahooremoteip";
    private static final String HEADER_NAME_X_FORWARDED_FOR = "x-forwarded-for";
    private static final String HEADER_NAME_CLIENT_IP = "client-ip";

    private final AccessLog accessLog;

    public AccessLogRequestLog(final AccessLog accessLog) {
        this.accessLog = accessLog;
    }

    @Override
    public void log(final Request request, final Response response) {
        try {
            final AccessLogEntry accessLogEntryFromServletRequest = (AccessLogEntry) request.getAttribute(
                    JDiscHttpServlet.ATTRIBUTE_NAME_ACCESS_LOG_ENTRY);
            final AccessLogEntry accessLogEntry;
            if (accessLogEntryFromServletRequest != null) {
                accessLogEntry = accessLogEntryFromServletRequest;
            } else {
                accessLogEntry = new AccessLogEntry();
                populateAccessLogEntryFromHttpServletRequest(request, accessLogEntry);
            }

            final long startTime = request.getTimeStamp();
            final long endTime = System.currentTimeMillis();
            accessLogEntry.setTimeStamp(startTime);
            accessLogEntry.setDurationBetweenRequestResponse(endTime - startTime);
            accessLogEntry.setReturnedContentSize(response.getContentCount());
            accessLogEntry.setStatusCode(response.getStatus());

            accessLog.log(accessLogEntry);
        } catch (Exception e) {
            // Catching any exceptions here as it is unclear how Jetty handles exceptions from a RequestLog.
            logger.log(Level.SEVERE, "Failed to log access log entry: " + e.getMessage(), e);
        }
    }

    /*
     * Collecting all log entry population based on extracting information from HttpServletRequest in one method
     * means that this may easily be moved to another location, e.g. if we want to populate this at instantiation
     * time rather than at logging time. We may, for example, want to set things such as http headers and ip
     * addresses up-front and make it illegal for request handlers to modify these later.
     */
    public static void populateAccessLogEntryFromHttpServletRequest(
            final HttpServletRequest request,
            final AccessLogEntry accessLogEntry) {
        setUriFromRequest(request, accessLogEntry);

        accessLogEntry.setRawPath(request.getRequestURI());
        String queryString = request.getQueryString();
        if (queryString != null) {
            accessLogEntry.setRawQuery(queryString);
        }

        final String remoteAddress = getRemoteAddress(request);
        final int remotePort = getRemotePort(request);
        final String peerAddress = request.getRemoteAddr();
        final int peerPort = request.getRemotePort();

        accessLogEntry.setUserAgent(request.getHeader("User-Agent"));
        accessLogEntry.setHttpMethod(request.getMethod());
        accessLogEntry.setHostString(request.getHeader("Host"));
        accessLogEntry.setReferer(request.getHeader("Referer"));
        accessLogEntry.setIpV4Address(peerAddress);
        accessLogEntry.setRemoteAddress(remoteAddress);
        accessLogEntry.setRemotePort(remotePort);
        if (!Objects.equal(remoteAddress, peerAddress)) {
            accessLogEntry.setPeerAddress(peerAddress);
        }
        if (remotePort != peerPort) {
            accessLogEntry.setPeerPort(peerPort);
        }
        accessLogEntry.setHttpVersion(request.getProtocol());
        accessLogEntry.setScheme(request.getScheme());
        accessLogEntry.setLocalPort(request.getLocalPort());
        Principal principal = (Principal) request.getAttribute(ServletRequest.JDISC_REQUEST_PRINCIPAL);
        if (principal != null) {
            accessLogEntry.setUserPrincipal(principal);
        }
        X509Certificate[] clientCert = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
        if (clientCert != null && clientCert.length > 0) {
            accessLogEntry.setSslPrincipal(clientCert[0].getSubjectX500Principal());
        }
    }

    private static String getRemoteAddress(final HttpServletRequest request) {
        return Alternative.preferred(request.getHeader(HEADER_NAME_Y_RA))
                .alternatively(() -> request.getHeader(HEADER_NAME_YAHOOREMOTEIP))
                .alternatively(() -> request.getHeader(HEADER_NAME_X_FORWARDED_FOR))
                .alternatively(() -> request.getHeader(HEADER_NAME_CLIENT_IP))
                .orElseGet(request::getRemoteAddr);
    }

    private static int getRemotePort(final HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HEADER_NAME_Y_RP))
                .map(Integer::valueOf)
                .orElseGet(request::getRemotePort);
    }

    @SuppressWarnings("deprecation")
    private static void setUriFromRequest(HttpServletRequest request, AccessLogEntry accessLogEntry) {
        tryCreateUriFromRequest(request)
                .ifPresent(accessLogEntry::setURI); // setURI is deprecated
    }

    // This is a mess and does not work correctly
    private static Optional<URI> tryCreateUriFromRequest(HttpServletRequest request) {
        final String quotedQuery = request.getQueryString();
        final String quotedPath = request.getRequestURI();
        try {
            final StringBuilder uriBuffer = new StringBuilder();
            uriBuffer.append(quotedPath);
            if (quotedQuery != null) {
                uriBuffer.append('?').append(quotedQuery);
            }
            return Optional.of(new URI(uriBuffer.toString()));
        } catch (URISyntaxException e) {
            return setUriFromMalformedInput(quotedPath, quotedQuery);
        }
    }

    private static Optional<URI> setUriFromMalformedInput(final String quotedPath, final String quotedQuery) {
        try {
            final String scheme = null;
            final String authority = null;
            final String fragment = null;
            return Optional.of(new URI(scheme, authority, unquote(quotedPath), unquote(quotedQuery), fragment));
        } catch (URISyntaxException e) {
            // I have no idea how this can happen here now...
            logger.log(Level.WARNING, "Could not convert String URI to URI object", e);
            return Optional.empty();
        }
    }

    private static String unquote(final String quotedQuery) {
        if (quotedQuery == null) {
            return null;
        }
        try {
            // inconsistent handling of semi-colon added here...
            return URLDecoder.decode(quotedQuery, StandardCharsets.UTF_8.name());
        } catch (IllegalArgumentException e) {
            return quotedQuery;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // should not happen
        }
    }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.collections.ListMap;
import com.yahoo.yolean.trace.TraceNode;

import javax.security.auth.x500.X500Principal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

/**
 * <p>Information to be logged in the access log.</p>
 *
 * <p>This class contains the union of all information that can be
 * logged with all the supported access log formats.</p>
 *
 * <p>The add methods can be called multiple times,
 * but the parameters should be different for each
 * invocation of the same method.</p>
 *
 * This class is thread-safe.
 *
 * @author Tony Vaagenes
 * @author bakksjo
 * @author bjorncs
 */
public class AccessLogEntry {

    // Sadly, there's no way to do compile-time validation of these field references.
    private static final String[] FIELDS_EXCLUDED_FROM_TOSTRING = new String[] {
            "monitor"
    };

    private final Object monitor = new Object();

    private String ipV4AddressInDotDecimalNotation;
    private long timeStampMillis;
    private long durationBetweenRequestResponseMillis;
    private long numBytesReturned;

    private String remoteAddress;
    private int remotePort;
    private String peerAddress;
    private int peerPort;

    private String profile;
    private String errorMessage;
    private String fileName;
    private String userAgent;
    private String referer;
    private String user;
    private HitCounts hitCounts;
    private String httpMethod;
    private String httpVersion;
    private String hostString;
    private int statusCode;
    private String scheme;
    private int localPort;
    private Principal principal;
    private X500Principal sslPrincipal;
    private String rawPath;
    private String rawQuery;
    private TraceNode traceNode;

    private ListMap<String,String> keyValues=null;

    public void setProfile( String profile ) {
        synchronized (monitor) {
            requireNull(this.profile);
            this.profile = profile;
        }
    }

    public String getProfile() {
        synchronized (monitor) {
            return profile;
        }
    }

    public void setErrorMessage(String errorMessage) {
        synchronized (monitor) {
            requireNull(this.errorMessage);
            this.errorMessage = errorMessage;
        }
    }

    public String getErrorMessage() {
        synchronized (monitor) {
            return errorMessage;
        }
    }

    public void setFileName(String fileName) {
        synchronized (monitor) {
            requireNull(this.fileName);
            this.fileName = fileName;
        }
    }

    public String getFileName() {
        synchronized (monitor) {
            return fileName;
        }
    }

    public void setUserAgent(String userAgent) {
        synchronized (monitor) {
            requireNull(this.userAgent);
            this.userAgent = userAgent;
        }
    }

    public String getUserAgent() {
        synchronized (monitor) {
            return userAgent;
        }
    }

    public void setReferer(String referer) {
        synchronized (monitor) {
            requireNull(this.referer);
            this.referer = referer;
        }
    }

    public String getReferer() {
        synchronized (monitor) {
            return referer;
        }
    }

    public void setUser(final String user) {
        synchronized (monitor) {
            requireNull(this.user);
            this.user = user;
        }
    }

    public String getUser() {
        synchronized (monitor) {
            return user;
        }
    }

    public void setHitCounts(final HitCounts hitCounts) {
        synchronized (monitor) {
            requireNull(this.hitCounts);
            this.hitCounts = hitCounts;
        }
    }

    public HitCounts getHitCounts() {
        synchronized (monitor) {
            return hitCounts;
        }
    }

    public void addKeyValue(String key,String value) {
        synchronized (monitor) {
            if (keyValues == null) {
                keyValues = new ListMap<>();
            }
            keyValues.put(key,value);
        }
    }

    public Map<String, List<String>> getKeyValues() {
        synchronized (monitor) {
            if (keyValues == null) {
                return null;
            }

            final Map<String, List<String>> newMapWithImmutableValues = mapValues(
                    keyValues.entrySet(),
                    valueList -> Collections.unmodifiableList(new ArrayList<>(valueList)));
            return Collections.unmodifiableMap(newMapWithImmutableValues);
        }
    }

    private static <K, V1, V2> Map<K, V2> mapValues(
            final Set<Map.Entry<K, V1>> entrySet,
            final Function<V1, V2> valueConverter) {
        return entrySet.stream()
                .collect(toMap(
                        entry -> entry.getKey(),
                        entry -> valueConverter.apply(entry.getValue())));
    }

    public enum HttpMethod {
        GET, POST;
    }

    public void setHttpMethod(HttpMethod method) {
        setHttpMethod(method.toString());
    }

    public void setHttpMethod(String method) {
        synchronized (monitor) {
            requireNull(this.httpMethod);
            this.httpMethod = method;
        }
    }

    public String getHttpMethod() {
        synchronized (monitor) {
            return httpMethod;
        }
    }

    public void setHttpVersion(final String httpVersion) {
        synchronized (monitor) {
            requireNull(this.httpVersion);
            this.httpVersion = httpVersion;
        }
    }

    public String getHttpVersion() {
        synchronized (monitor) {
            return httpVersion;
        }
    }

    public void setHostString(String hostString) {
        synchronized (monitor) {
            requireNull(this.hostString);
            this.hostString = hostString;
        }
    }

    public String getHostString() {
        synchronized (monitor) {
            return hostString;
        }
    }

    public void setIpV4Address(String ipV4AddressInDotDecimalNotation) {
        synchronized (monitor) {
            requireNull(this.ipV4AddressInDotDecimalNotation);
            this.ipV4AddressInDotDecimalNotation = ipV4AddressInDotDecimalNotation;
        }
    }

    public String getIpV4Address() {
        synchronized (monitor) {
            return ipV4AddressInDotDecimalNotation;
        }
    }

    public void setTimeStamp(long numMillisSince1Jan1970AtMidnightUTC) {
        synchronized (monitor) {
            requireZero(this.timeStampMillis);
            timeStampMillis = numMillisSince1Jan1970AtMidnightUTC;
        }
    }

    public long getTimeStampMillis() {
        synchronized (monitor) {
            return timeStampMillis;
        }
    }

    public void setDurationBetweenRequestResponse(long timeInMillis) {
        synchronized (monitor) {
            requireZero(this.durationBetweenRequestResponseMillis);
            durationBetweenRequestResponseMillis = timeInMillis;
        }
    }

    public long getDurationBetweenRequestResponseMillis() {
        synchronized (monitor) {
            return durationBetweenRequestResponseMillis;
        }
    }

    public void setReturnedContentSize(int byteCount) {
        setReturnedContentSize((long) byteCount);
    }

    public void setReturnedContentSize(long byteCount) {
        synchronized (monitor) {
            requireZero(this.numBytesReturned);
            numBytesReturned = byteCount;
        }
    }

    public long getReturnedContentSize() {
        synchronized (monitor) {
            return numBytesReturned;
        }
    }

    public void setRemoteAddress(String remoteAddress) {
        synchronized (monitor) {
            requireNull(this.remoteAddress);
            this.remoteAddress = remoteAddress;
        }
    }

    public void setRemoteAddress(final InetSocketAddress remoteAddress) {
        setRemoteAddress(getIpAddressAsString(remoteAddress));
    }

    private static String getIpAddressAsString(final InetSocketAddress remoteAddress) {
        final InetAddress inetAddress = remoteAddress.getAddress();
        if (inetAddress == null) {
            return null;
        }
        return inetAddress.getHostAddress();
    }

    public String getRemoteAddress() {
        synchronized (monitor) {
            return remoteAddress;
        }
    }

    public void setRemotePort(int remotePort) {
        synchronized (monitor) {
            requireZero(this.remotePort);
            this.remotePort = remotePort;
        }
    }

    public int getRemotePort() {
        synchronized (monitor) {
            return remotePort;
        }
    }

    public void setPeerAddress(final String peerAddress) {
        synchronized (monitor) {
            requireNull(this.peerAddress);
            this.peerAddress = peerAddress;
        }
    }

    public void setPeerPort(int peerPort) {
        synchronized (monitor) {
            requireZero(this.peerPort);
            this.peerPort = peerPort;
        }
    }

    public int getPeerPort() {
        synchronized (monitor) {
            return peerPort;
        }
    }

    public String getPeerAddress() {
        synchronized (monitor) {
            return peerAddress;
        }
    }

    public void setStatusCode(int statusCode) {
        synchronized (monitor) {
            requireZero(this.statusCode);
            this.statusCode = statusCode;
        }
    }

    public int getStatusCode() {
        synchronized (monitor) {
            return statusCode;
        }
    }

    public String getScheme() {
        synchronized (monitor) {
            return scheme;
        }
    }

    public void setScheme(String scheme) {
        synchronized (monitor) {
            requireNull(this.scheme);
            this.scheme = scheme;
        }
    }

    public int getLocalPort() {
        synchronized (monitor) {
            return localPort;
        }
    }

    public void setLocalPort(int localPort) {
        synchronized (monitor) {
            requireZero(this.localPort);
            this.localPort = localPort;
        }
    }

    public Principal getUserPrincipal() {
        synchronized (monitor) {
            return principal;
        }
    }

    public void setUserPrincipal(Principal principal) {
        synchronized (monitor) {
            requireNull(this.principal);
            this.principal = principal;
        }
    }

    public Principal getSslPrincipal() {
        synchronized (monitor) {
            return sslPrincipal;
        }
    }

    public void setSslPrincipal(X500Principal sslPrincipal) {
        synchronized (monitor) {
            requireNull(this.sslPrincipal);
            this.sslPrincipal = sslPrincipal;
        }
    }

    public void setRawPath(String rawPath) {
        synchronized (monitor) {
            requireNull(this.rawPath);
            this.rawPath = rawPath;
        }
    }

    public String getRawPath() {
        synchronized (monitor) {
            return rawPath;
        }
    }

    public void setRawQuery(String rawQuery) {
        synchronized (monitor) {
            requireNull(this.rawQuery);
            this.rawQuery = rawQuery;
        }
    }

    public Optional<String> getRawQuery() {
        synchronized (monitor) {
            return Optional.ofNullable(rawQuery);
        }
    }

    public void setTrace(TraceNode traceNode) {
        synchronized (monitor) {
            requireNull(this.traceNode);
            this.traceNode = traceNode;
        }
    }
    public TraceNode getTrace() {
        synchronized (monitor) {
            return traceNode;
        }
    }

    @Override
    public String toString() {
        synchronized (monitor) {
            return "AccessLogEntry{" +
                    "ipV4AddressInDotDecimalNotation='" + ipV4AddressInDotDecimalNotation + '\'' +
                    ", timeStampMillis=" + timeStampMillis +
                    ", durationBetweenRequestResponseMillis=" + durationBetweenRequestResponseMillis +
                    ", numBytesReturned=" + numBytesReturned +
                    ", remoteAddress='" + remoteAddress + '\'' +
                    ", remotePort=" + remotePort +
                    ", peerAddress='" + peerAddress + '\'' +
                    ", peerPort=" + peerPort +
                    ", profile='" + profile + '\'' +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", fileName='" + fileName + '\'' +
                    ", userAgent='" + userAgent + '\'' +
                    ", referer='" + referer + '\'' +
                    ", user='" + user + '\'' +
                    ", hitCounts=" + hitCounts +
                    ", httpMethod='" + httpMethod + '\'' +
                    ", httpVersion='" + httpVersion + '\'' +
                    ", hostString='" + hostString + '\'' +
                    ", statusCode=" + statusCode +
                    ", scheme='" + scheme + '\'' +
                    ", localPort=" + localPort +
                    ", principal=" + principal +
                    ", sslPrincipal=" + sslPrincipal +
                    ", rawPath='" + rawPath + '\'' +
                    ", rawQuery='" + rawQuery + '\'' +
                    ", trace='" + traceNode + '\'' +
                    ", keyValues=" + keyValues +
                    '}';
        }
    }

    private static void requireNull(final Object value) {
        if (value != null) {
            throw new IllegalStateException("Attempt to overwrite field that has been assigned. Value: " + value);
        }
    }

    private static void requireZero(final long value) {
        if (value != 0) {
            throw new IllegalStateException("Attempt to overwrite field that has been assigned. Value: " + value);
        }
    }

    private static void requireZero(final int value) {
        if (value != 0) {
            throw new IllegalStateException("Attempt to overwrite field that has been assigned. Value: " + value);
        }
    }

}

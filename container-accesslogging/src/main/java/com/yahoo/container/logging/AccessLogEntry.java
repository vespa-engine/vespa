// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.collections.ListMap;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;

import javax.security.auth.x500.X500Principal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
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
 * This class is thread-safe, but the inner class {@link AdInfo} is not.
 *
 * @author tonytv
 * @author bakksjo
 * @author bjorncs
 */
public class AccessLogEntry {
    public enum CookieType {
        b,
        l,
        n,
        geocookie,
        I,
        R,
        Y,
        M;
    }

    // Sadly, there's no way to do compile-time validation of these field references.
    private static final String[] FIELDS_EXCLUDED_FROM_TOSTRING = new String[] {
            "monitor"
    };

    private final Object monitor = new Object();

    private List<AdInfo> adInfos;
    private String spaceID;

    private String ipV4AddressInDotDecimalNotation;
    private long timeStampMillis;
    private long durationBetweenRequestResponseMillis;
    private long numBytesReturned;
    private URI uri;


    private String remoteAddress;
    private int remotePort;
    private String peerAddress;
    private int peerPort;

    private CookieType cookieType;
    private String cookie;
    private String weekOfRegistration;
    private String profile;
    private String internationalInfo;
    private String contentAttribute;
    private String webfactsDigitalSignature;
    private String errorMessage;
    private String fileName;
    private String userAgent;
    private String referer;
    private String user;
    private HitCounts hitCounts;
    private String requestExtra;
    private String responseExtra;
    private Boolean resultFromCache;
    private String httpMethod;
    private String httpVersion;
    private String partner;
    private String adRationale;
    private String incrementSlotByOneRequest;
    private String zDataIncrementSlotByOneRequest;
    private String hostString;
    private int statusCode;
    private String scheme;
    private int localPort;
    private Principal principal;
    private X500Principal sslPrincipal;
    private String rawPath;
    private String rawQuery;

    private ListMap<String,String> keyValues=null;

    public void setCookie( CookieType type, String cookie) {
        synchronized (monitor) {
            requireNull(this.cookieType);
            requireNull(this.cookie);
            this.cookieType = type;
            this.cookie = cookie;
        }
    }

    public CookieType getCookieType() {
        synchronized (monitor) {
            return cookieType;
        }
    }

    public String getCookie() {
        synchronized (monitor) {
            return cookie;
        }
    }

    public void setWeekOfRegistration( String weekOfRegistration ) {
        synchronized (monitor) {
            requireNull(this.weekOfRegistration);
            this.weekOfRegistration = weekOfRegistration;
        }
    }

    public String getWeekOfRegistration() {
        synchronized (monitor) {
            return weekOfRegistration;
        }
    }

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

    public void setInternationalInfo( String intl ) {
        synchronized (monitor) {
            requireNull(this.internationalInfo);
            this.internationalInfo = intl;
        }
    }

    public String getInternationalInfo() {
        synchronized (monitor) {
            return internationalInfo;
        }
    }

    public void setContentAttribute( String contentAttribute ) {
        synchronized (monitor) {
            requireNull(this.contentAttribute);
            this.contentAttribute = contentAttribute;
        }
    }

    public String getContentAttribute() {
        synchronized (monitor) {
            return contentAttribute;
        }
    }

    public void setAdSpaceID(String spaceID) {
        synchronized (monitor) {
            requireNull(this.spaceID);
            this.spaceID = spaceID;
        }
    }

    public String getAdSpaceID() {
        synchronized (monitor) {
            return spaceID;
        }
    }

    public void addAdInfo(AdInfo adInfo) {
        synchronized (monitor) {
            if (adInfos == null) {
                adInfos = new ArrayList<>();
            }
            adInfos.add( adInfo );
        }
    }

    public List<AdInfo> getAdInfos() {
        synchronized (monitor) {
            if (adInfos == null) {
                return Collections.emptyList();
            }
            // TODO: The returned list is unmodifiable, but its elements are not. But we're all friendly here, right?
            return Collections.unmodifiableList(adInfos);
        }
    }

    /**
     * This class is NOT thread-safe. It is assumed that a single instance is created/written by a single thread,
     * and all reads happen-after creation/population, i.e. no mutation after the instance is shared between threads.
     */
    public static class AdInfo {

        private String adServerString;
        private String adId;
        private String matchId;
        private String position;
        private String property;
        private String cpc;
        private String adClientVersion;
        private String linkId;
        private String bidPosition;

        public void setAdID(String id) {
            this.adId = id;
        }

        public String getAdID() {
            return adId;
        }

        public void setMatchID(String id) {
            this.matchId = id;
        }

        public String getMatchID() {
            return matchId;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public String getPosition() {
            return position;
        }

        public void setProperty(String property) {
            this.property = property;
        }

        public String getProperty() {
            return property;
        }

        public void setCPC(String cpc) {
            this.cpc = cpc;
        }

        public String getCPC() {
            return cpc;
        }

        public void setAdClientVersion(String adClientVersion) {
            this.adClientVersion = adClientVersion;
        }

        public String getAdClientVersion() {
            return adClientVersion;
        }

        public void setLinkID(String id) {
            this.linkId = id;
        }

        public String getLinkID() {
            return linkId;
        }

        public void setBidPosition(String bidPosition) {
            this.bidPosition = bidPosition;
        }

        public String getBidPosition() {
            return bidPosition;
        }

        public AdInfo() {}

        AdInfo(String adServerString) {
            this.adServerString = adServerString;
        }

        String getAdServerString() {
            return adServerString;
        }
    }

    public void setWebfactsDigitalSignature(String signature) {
        synchronized (monitor) {
            requireNull(this.webfactsDigitalSignature);
            this.webfactsDigitalSignature = signature;
        }
    }

    public String getWebfactsDigitalSignature() {
        synchronized (monitor) {
            return webfactsDigitalSignature;
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

    public String getRequestExtra() {
        synchronized (monitor) {
            return requestExtra;
        }
    }

    public String getResponseExtra() {
        synchronized (monitor) {
            return responseExtra;
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

    public void setResultFromCache(boolean fromCache) {
        synchronized (monitor) {
            requireNull(this.resultFromCache);
            this.resultFromCache = fromCache;
        }
    }

    public Boolean getResultFromCache() {
        synchronized (monitor) {
            return resultFromCache;
        }
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

    public void setPartner(String partner) {
        synchronized (monitor) {
            requireNull(this.partner);
            this.partner = partner;
        }
    }

    public String getPartner() {
        synchronized (monitor) {
            return partner;
        }
    }

    public void setAdRationale(String adRationale) {
        synchronized (monitor) {
            requireNull(this.adRationale);
            this.adRationale = adRationale;
        }
    }

    public String getAdRationale() {
        synchronized (monitor) {
            return adRationale;
        }
    }

    public void setIncrementSlotByOneRequest(String slotName) {
        synchronized (monitor) {
            requireNull(this.incrementSlotByOneRequest);
            this.incrementSlotByOneRequest = slotName;
        }
    }

    public String getIncrementSlotByOneRequest() {
        synchronized (monitor) {
            return incrementSlotByOneRequest;
        }
    }

    public void setZDataIncrementSlotByOneRequest(String slotName) {
        synchronized (monitor) {
            requireNull(this.zDataIncrementSlotByOneRequest);
            this.zDataIncrementSlotByOneRequest = slotName;
        }
    }

    public String getZDataIncrementSlotByOneRequest() {
        synchronized (monitor) {
            return zDataIncrementSlotByOneRequest;
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

    /**
     * @deprecated Use {@link #setRawPath(String)} and {@link #setRawQuery(String)} instead.
     */
    @Deprecated
    public void setURI(final URI uri) {
        synchronized (monitor) {
            requireNull(this.uri);
            this.uri = uri;
        }
    }

    /**
     * @deprecated Use {@link #getRawPath()} and {@link #getRawQuery()} instead. This method may return wrong path.
     */
    @Deprecated
    public URI getURI() {
        synchronized (monitor) {
            return uri;
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

    @Override
    public String toString() {
        synchronized (monitor) {
            return new ReflectionToStringBuilder(this)
                    .setExcludeFieldNames(FIELDS_EXCLUDED_FROM_TOSTRING)
                    .toString();
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

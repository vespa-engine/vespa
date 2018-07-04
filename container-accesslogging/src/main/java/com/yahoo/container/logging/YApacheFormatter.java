// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import java.net.URI;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.container.logging.AccessLogEntry.CookieType;

/**
 * Formatting of an {@link AccessLogEntry} in the yapache access log format.
 *
 * @author Tony Vaagenes
 * @author bakksjo
 */
public class YApacheFormatter {

    private AccessLogEntry accessLogEntry;

    public YApacheFormatter(final AccessLogEntry entry) {
        accessLogEntry = entry;
    }

    public String format() {
        // Initial 32-byte fixed block of mandatory, non-prefixed fields.
        setIpV4Address(accessLogEntry.getIpV4Address());
        setTimeStampMillis(accessLogEntry.getTimeStampMillis());
        setDurationBetweenRequestResponseMillis(accessLogEntry.getDurationBetweenRequestResponseMillis());
        setReturnedContentSize(accessLogEntry.getReturnedContentSize());

        // Optional, prefixed fields in arbitrary order.
        setStatusCode(accessLogEntry.getStatusCode());
        setRemoteAddress(accessLogEntry.getRemoteAddress());
        setRemotePort(accessLogEntry.getRemotePort());
        setURI(accessLogEntry.getURI());
        setCookie(accessLogEntry.getCookieType(), accessLogEntry.getCookie());
        setWeekOfRegistration(accessLogEntry.getWeekOfRegistration());
        setProfile(accessLogEntry.getProfile());
        setInternationalInfo(accessLogEntry.getInternationalInfo());
        setContentAttribute(accessLogEntry.getContentAttribute());
        setAdSpaceID(accessLogEntry.getAdSpaceID());
        setErrorMessage(accessLogEntry.getErrorMessage());
        setFileName(accessLogEntry.getFileName());
        setUserAgent(accessLogEntry.getUserAgent());
        setWebfactsDigitalSignature(accessLogEntry.getWebfactsDigitalSignature());
        setReferer(accessLogEntry.getReferer());
        setRequestExtra(accessLogEntry.getRequestExtra());
        setResponseExtra(accessLogEntry.getResponseExtra());
        setResultFromCache(accessLogEntry.getResultFromCache());
        setHttpMethod(accessLogEntry.getHttpMethod());
        setPartner(accessLogEntry.getPartner());
        setAdRationale(accessLogEntry.getAdRationale());
        setIncrementSlotByOneRequest(accessLogEntry.getIncrementSlotByOneRequest());
        setZDataIncrementSlotByOneRequest(accessLogEntry.getZDataIncrementSlotByOneRequest());
        setHostString(accessLogEntry.getHostString());
        setPeerAddress(accessLogEntry.getPeerAddress());
        setPeerPort(accessLogEntry.getPeerPort());
        adInfos = accessLogEntry.getAdInfos();
        keyValues = accessLogEntry.getKeyValues();

        return toYApacheAccessEntry();
    }

    private static final Map<CookieType, Character> cookieTypeFirstCharMap = new HashMap<>();
    static {
        cookieTypeFirstCharMap.put(CookieType.b, '1');
        cookieTypeFirstCharMap.put(CookieType.l, '2');
        cookieTypeFirstCharMap.put(CookieType.n, '3');
        cookieTypeFirstCharMap.put(CookieType.geocookie,'5');
        cookieTypeFirstCharMap.put(CookieType.I, '7');
        cookieTypeFirstCharMap.put(CookieType.R, '9');
        cookieTypeFirstCharMap.put(CookieType.Y, 'c');
        cookieTypeFirstCharMap.put(CookieType.M, 'M');
    }

    private List<AccessLogEntry.AdInfo> adInfos;
    private String spaceID;

    private String ipV4AddressInDotDecimalNotation;
    private String unixTimeStamp;
    private String durationBetweenRequestResponseInMS;
    private String numBytesReturned;
    private String uri;


    private String remoteAddress;
    private int remotePort;
    private String peerAddress;
    private int peerPort;

    private Map<String,List<String>> keyValues;

    private static Logger logger = Logger.getLogger(YApacheFormatter.class.getName());

    private void setCookie(CookieType type, String cookie) {
        final Character firstChar = cookieTypeFirstCharMap.get(type);
        if (firstChar == null) {
            return;
        }
        addField(firstChar, cookie);
    }

    private void setWeekOfRegistration( String weekOfRegistration ) {
        addField('4', weekOfRegistration);
    }

    private void setProfile( String profile ) {
        addField('6', profile);
    }

    private void setInternationalInfo( String intl ) {
        addField('8', intl);
    }

    private void setContentAttribute( String contentAttribute ) {
        addField('a', contentAttribute);
    }

    private void setAdSpaceID(String spaceID) {
        this.spaceID = spaceID;
    }

    private static class AdInfo {

        private void setAdID(String id) {
            add('B', id);
        }

        private void setMatchID(String id) {
            add('C', id);
        }

        private void setPosition(String position) {
            add('D', position);
        }

        private void setProperty(String property) {
            add('F', property);
        }

        private void setCPC(String cpc) {
            add('G', cpc);
        }

        private void setAdClientVersion(String adClientVersion) {
            add('K', adClientVersion);
        }

        private void setLinkID(String id) {
            add('L', id);
        }

        private void setBidPosition(String bidPosition) {
            add('P', bidPosition);
        }

        private StringBuilder adInfo = new StringBuilder();

        AdInfo(final AccessLogEntry.AdInfo model) {
            final String modelAdServerString = model.getAdServerString();
            if (modelAdServerString != null) {
                adInfo.append(modelAdServerString);
            }
            setAdID(model.getAdID());
            setMatchID(model.getMatchID());
            setPosition(model.getPosition());
            setProperty(model.getProperty());
            setCPC(model.getCPC());
            setAdClientVersion(model.getAdClientVersion());
            setLinkID(model.getLinkID());
            setBidPosition(model.getBidPosition());
        }

        String adInfoString() {
            return adInfo.toString();
        }

        private void add(char controlChar, String value) {
            if (value == null) {
                return;
            }
            adInfo.append( controlCharacter(controlChar) );
            adInfo.append( value );
        }
    }

    private void setWebfactsDigitalSignature(String signature) {
        addField('d', signature);
    }

    private void setErrorMessage(String errorMessage) {
        addField('e', errorMessage);
    }

    private void setFileName(String fileName) {
        addField('f', fileName);
    }

    private void setUserAgent(String userAgent) {
        addField('g', userAgent);
    }

    private void setReferer(String referer) {
        addField('r', referer);
    }

    private void setRequestExtra(String requestExtra) {
        if (requestExtra == null || requestExtra.isEmpty()) {
            return;
        }
        if (fieldPrefix != requestExtra.charAt(0)) {
            yApacheEntry.append(fieldPrefix);
        }
        yApacheEntry.append(requestExtra);
    }

    private void setResponseExtra(String responseExtra) {
        if (responseExtra == null || responseExtra.isEmpty()) {
            return;
        }
        if (fieldPrefix != responseExtra.charAt(0)) {
            yApacheEntry.append(fieldPrefix);
        }
        yApacheEntry.append(responseExtra);
    }

    private void setResultFromCache(Boolean fromCache) {
        if (fromCache == null) {
            return;
        }
        addField('h',
                 fromCache ? "1" : "0");
    }

    private void setHttpMethod(String method) {
        addField('m', method);
    }

    private void setPartner(String partner) {
        addField('p', partner);
    }

    private void setAdRationale(String adRationale) {
        addField('R', adRationale);
    }

    private void setIncrementSlotByOneRequest(String slotName) {
        addField('t', slotName);
    }

    private void setZDataIncrementSlotByOneRequest(String slotName) {
        addField('z', slotName);
    }

    private void setHostString(String hostString) {
        addField('w', hostString);
    }

    private StringBuilder yApacheEntry = new StringBuilder();

    private static char fieldPrefix = controlCharacter('E');
    private static char valueSeparator = controlCharacter('A');


    //assumes A <= c <= Z
    private static char controlCharacter(char c) {
        return (char)((c - 'A') + 1);
    }

    private void appendStartOfField(StringBuilder builder, char firstChar) {
        builder.append(fieldPrefix);
        builder.append(firstChar);
    }

    private void addField(char firstChar, String field) {
        if (field == null) {
            return;
        }
        appendStartOfField(yApacheEntry, firstChar);
        yApacheEntry.append(field);
    }

    private void addDecimalField(char firstChar, int field) {
        addField(firstChar, Integer.toString(field));
    }

    private String to8ByteHexString(final long value) {
        Formatter formatter = new Formatter();
        formatter.format("%08x", value);
        return formatter.toString();
    }

    private void appendAdInfo(StringBuilder buf) {
        if (spaceID != null || ! adInfos.isEmpty()) {
            buf.append(fieldPrefix);
            buf.append('b');

            if (spaceID != null) {
                buf.append( controlCharacter('A') );
                buf.append( spaceID );
            }

            for (AccessLogEntry.AdInfo adInfo : adInfos ) {
                buf.append( new AdInfo(adInfo).adInfoString() );
            }
        }
    }

    private void appendFirst32Bytes(StringBuilder buf) {
        buf.append(ipv4AddressToHexString(ipV4AddressInDotDecimalNotation));
        buf.append(unixTimeStamp);
        buf.append(durationBetweenRequestResponseInMS);
        buf.append(numBytesReturned);

        assert(buf.length() == 32);
    }

    private String toYApacheAccessEntry() {
        StringBuilder b = new StringBuilder();

        appendFirst32Bytes(b);
        b.append(uri);

        b.append(yApacheEntry);
        appendIPv6AddressInfo(b);
        appendAdInfo(b);

        appendKeyValues(b);
        return b.toString();
    }

    private void appendIPv6AddressInfo(StringBuilder builder) {
        appendStartOfField(builder, 'A');
        final boolean remoteAddressesAreEqual = Objects.equals(ipV4AddressInDotDecimalNotation, remoteAddress);
        if (!remoteAddressesAreEqual && remoteAddress != null) {
            builder.append('A').append(remoteAddress).append(valueSeparator);
        }

        builder.append('B').append(remotePort);

        if (peerAddress != null) {
            builder.append(valueSeparator).append('C').append(peerAddress);
        }

        if (peerPort > 0 && peerPort != remotePort) {
            builder.append(valueSeparator).append('D').append(peerPort);
        }
    }

    /**
     * Encodes key-values added to this entry at the "property extension" key 'X' as
     * <code>^EY[key1]^F[value1.1]^B[value1.2]^A[key2]^F[value2]</code>,
     */
    private void appendKeyValues(StringBuilder b) {
        if (keyValues==null) return;
        b.append(fieldPrefix);
        b.append('X');
        for (Map.Entry<String,List<String>> entry : keyValues.entrySet()) {
            b.append(entry.getKey());
            b.append(controlCharacter('F'));
            for (Iterator<String> i=entry.getValue().iterator(); i.hasNext(); ) {
                b.append(i.next());
                if (i.hasNext())
                    b.append(controlCharacter('B'));
            }
            b.append(controlCharacter('A'));
        }
        b.deleteCharAt(b.length()-1); // Deletes the last ^A to be able to do foreach looping  :-)
    }

    private String ipv4AddressToHexString(String ipv4AddressInDotDecimalNotation) {
        try {
            String[] parts = ipv4AddressInDotDecimalNotation.split("\\.");
            if (parts.length != 4) {
                throw new Exception();
            } else {
                Formatter byteHexFormatter = new Formatter();

                for (String part : parts) {
                    int i = Integer.parseInt(part);
                    if ( i > 0xff || i < 0)
                        throw new Exception();
                    byteHexFormatter.format("%02x", i);
                }

                return byteHexFormatter.toString();
            }
        } catch( Exception e ) {
            logger.log(Level.WARNING, "IPv4 address not in dot decimal notation: " +
                       ipv4AddressInDotDecimalNotation);
            return "00000000";
        }
    }

    private void setIpV4Address(String ipV4AddressInDotDecimalNotation) {
        this.ipV4AddressInDotDecimalNotation = ipV4AddressInDotDecimalNotation;
    }

    private void setTimeStampMillis(long numMillisSince1Jan1970AtMidnightUTC) {
        int unixTime = (int)(numMillisSince1Jan1970AtMidnightUTC/1000);

        if (numMillisSince1Jan1970AtMidnightUTC/1000 > 0x7fffffff) {
            logger.log(Level.WARNING, "A year 2038 problem occurred.");
            logger.log(Level.INFO, "numMillisSince1Jan1970AtMidnightUTC: "
                       + numMillisSince1Jan1970AtMidnightUTC);
            unixTime = (int)(numMillisSince1Jan1970AtMidnightUTC/1000 % 0x7fffffff);
        }

        unixTimeStamp = to8ByteHexString(unixTime);
    }

    private void setDurationBetweenRequestResponseMillis(long timeInMillis) {
        long timeInMicroSeconds = timeInMillis*1000;
        if (timeInMicroSeconds > 0xffffffffL) {
            logger.log(Level.WARNING, "Duration too long: " + timeInMillis);
            timeInMicroSeconds = 0xffffffffL;
        }

        durationBetweenRequestResponseInMS = to8ByteHexString(timeInMicroSeconds);
    }

    private void setReturnedContentSize(long byteCount) {
        numBytesReturned = to8ByteHexString(byteCount);
    }

    private void setURI(final URI uri) {
        setNormalizedURI(uri.normalize());
    }

    private void setNormalizedURI(final URI normalizedUri) {
        String uriString = normalizedUri.getPath();
        if (normalizedUri.getRawQuery() != null) {
            uriString = uriString + "?" + normalizedUri.getRawQuery();
        }

        this.uri = uriString;
    }

    private void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    private void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    private void setPeerAddress(final String peerAddress) {
        this.peerAddress = peerAddress;
    }

    private void setPeerPort(int peerPort) {
        this.peerPort = peerPort;
    }

    /** Sets the status code, which will end up in the "s" field. If this is 200 the field is not written (by spec). */
    private void setStatusCode(int statusCode) {
        if (statusCode == 0) {
            return;
        }
        if (statusCode!=200)
            addDecimalField('s', statusCode);
    }

}

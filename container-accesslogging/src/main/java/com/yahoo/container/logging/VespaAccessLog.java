// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.container.core.AccessLogConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;

/**
 * @author Bjorn Borud
 * @author Oyvind Bakksjo
 */
public final class VespaAccessLog implements AccessLogInterface {

    private static final SimpleDateFormat dateFormat = createDateFormat();

    private final AccessLogHandler logHandler;

    public VespaAccessLog(AccessLogConfig config) {
        logHandler = new AccessLogHandler(config.fileHandler());
    }

    private static SimpleDateFormat createDateFormat() {
        SimpleDateFormat format = new SimpleDateFormat("[dd/MMM/yyyy:HH:mm:ss Z]");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }

    private String getDate () {
        Date date = new Date();
        return dateFormat.format(date);
    }

    private String getRequest(final String httpMethod, final String rawPath, final String rawQuery, final String httpVersion) {
        return httpMethod + " " + (rawQuery != null ? rawPath + "?" + rawQuery : rawPath) + " " + httpVersion;
    }

    private String getUser(String user) {
        return (user == null) ? "-" : user;
    }

    private void writeLog(String ipAddr, String user, String request, String referer, String agent, long startTime,
                          long durationMillis, long byteCount, HitCounts hitcounts, int returnCode)
    {
        long ms = Math.max(0L, durationMillis);
        StringBuilder sb = new StringBuilder()
                .append(ipAddr)
                .append(" - ")
                .append(getUser(user))
                .append(' ')
                .append(getDate())
                .append(" \"")
                .append(request)
                .append("\" ")
                .append(returnCode)
                .append(' ')
                .append(byteCount)
                .append(" \"")
                .append(referer)
                .append("\" \"")
                .append(agent)
                .append("\" ")
                .append(ms/1000)
                .append('.');
        decimalsOfSecondsFromMilliseconds(ms, sb);
        sb.append(' ')
                .append((hitcounts == null) ? 0 : hitcounts.getTotalHitCount())
                .append(" 0.0 ")
                .append((hitcounts == null) ? 0 : hitcounts.getSummaryCount())
                .append('\n');
        logHandler.access.log(Level.INFO, sb.toString());
    }

    private void decimalsOfSecondsFromMilliseconds(long ms, StringBuilder sb) {
        long dec = ms % 1000;
        String numbers = String.valueOf(dec);
        if (dec <= 9) {
            sb.append("00");
        } else if (dec <= 99) {
            sb.append('0');
        }
        sb.append(numbers);
    }

    /**
     * TODO: This is never called. We should have a DI provider and call this method from its deconstruct.
     */
    public void shutdown() {
        if (logHandler!=null)
            logHandler.shutdown();
    }

    @Override
    public void log(final AccessLogEntry accessLogEntry) {
        writeLog(
                accessLogEntry.getIpV4Address(),
                accessLogEntry.getUser(),
                getRequest(
                        accessLogEntry.getHttpMethod(),
                        accessLogEntry.getRawPath(),
                        accessLogEntry.getRawQuery().orElse(null),
                        accessLogEntry.getHttpVersion()),
                accessLogEntry.getReferer(),
                accessLogEntry.getUserAgent(),
                accessLogEntry.getTimeStampMillis(),
                accessLogEntry.getDurationBetweenRequestResponseMillis(),
                accessLogEntry.getReturnedContentSize(),
                accessLogEntry.getHitCounts(),
                accessLogEntry.getStatusCode());
    }
}

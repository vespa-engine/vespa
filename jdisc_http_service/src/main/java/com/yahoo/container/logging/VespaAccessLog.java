// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.core.AccessLogConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author Bjorn Borud
 * @author Oyvind Bakksjo
 */
public final class VespaAccessLog extends AbstractComponent implements RequestLogHandler, LogWriter<RequestLogEntry> {

    private static final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(VespaAccessLog::createDateFormat);

    private final AccessLogHandler logHandler;

    public VespaAccessLog(AccessLogConfig config) {
        logHandler = new AccessLogHandler(config.fileHandler(), this);
    }

    private static SimpleDateFormat createDateFormat() {
        SimpleDateFormat format = new SimpleDateFormat("[dd/MMM/yyyy:HH:mm:ss Z]");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }

    private static String getDate() {
        return dateFormat.get().format(new Date());
    }

    private String getRequest(final String httpMethod, final String rawPath, final String rawQuery, final String httpVersion) {
        return httpMethod + " " + (rawQuery != null ? rawPath + "?" + rawQuery : rawPath) + " " + httpVersion;
    }

    private String getUser(String user) {
        return (user == null) ? "-" : user;
    }

    private String toLogline(String ipAddr, String user, String request, String referer, String agent,
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
                .append((hitcounts == null) ? 0 : hitcounts.getSummaryCount());
        return sb.toString();
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

    @Override public void deconstruct() { logHandler.shutdown(); }

    @Override
    public void log(RequestLogEntry entry) {
        logHandler.log(entry);
    }

    @Override
    public void write(RequestLogEntry entry, OutputStream outputStream) throws IOException {
        outputStream.write(
                toLogline(
                        entry.peerAddress().get(),
                        null,
                        getRequest(
                                entry.httpMethod().orElse(null),
                                entry.rawPath().orElse(null),
                                entry.rawQuery().orElse(null),
                                entry.httpVersion().orElse(null)),
                        entry.referer().orElse(null),
                        entry.userAgent().orElse(null),
                        entry.duration().get().toMillis(),
                        entry.contentSize().orElse(0L),
                        entry.hitCounts().orElse(null),
                        entry.statusCode().orElse(0)).getBytes(StandardCharsets.UTF_8));
    }
}

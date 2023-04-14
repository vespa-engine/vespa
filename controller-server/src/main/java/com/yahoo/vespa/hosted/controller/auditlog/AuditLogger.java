// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.auditlog;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLog.Entry;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static com.yahoo.yolean.Exceptions.uncheck;
import static java.util.Objects.requireNonNullElse;

/**
 * This provides read and write operations for the audit log.
 *
 * @author mpolden
 */
public class AuditLogger {

    /** The TTL of log entries. Entries older than this will be removed when the log is updated */
    private static final Duration entryTtl = Duration.ofDays(14);
    private static final int maxEntries = 2000;

    private final CuratorDb db;
    private final Clock clock;

    public AuditLogger(CuratorDb db, Clock clock) {
        this.db = Objects.requireNonNull(db, "db must be non-null");
        this.clock = Objects.requireNonNull(clock, "clock must be non-null");
    }

    /** Read the current audit log */
    public AuditLog readLog() {
        return db.readAuditLog();
    }

    /**
     * Write a log entry for given request to the audit log.
     *
     * Note that data contained in the given request may be consumed. Callers should use the returned HttpRequest for
     * further processing.
     */
    public HttpRequest log(HttpRequest request) {
        Optional<AuditLog.Entry.Method> method = auditableMethod(request);
        if (method.isEmpty()) return request; // Nothing to audit, e.g. a GET request

        Principal principal = request.getJDiscRequest().getUserPrincipal();
        if (principal == null) {
            throw new IllegalStateException("Cannot audit " + request.getMethod() + " " + request.getUri() +
                                            " as no principal was found in the request. This is likely caused by a " +
                                            "misconfiguration and should not happen");
        }

        InputStream requestData = requireNonNullElse(request.getData(), InputStream.nullInputStream());
        byte[] data = uncheck(() -> requestData.readNBytes(Entry.maxDataLength));

        AuditLog.Entry.Client client = parseClient(request);
        Instant now = clock.instant();
        AuditLog.Entry entry = new AuditLog.Entry(now, client, principal.getName(), method.get(),
                                                  pathAndQueryOf(request.getUri()), data);
        try (Mutex lock = db.lockAuditLog()) {
            AuditLog auditLog = db.readAuditLog()
                                  .pruneBefore(now.minus(entryTtl))
                                  .with(entry)
                                  .first(maxEntries);
            db.writeAuditLog(auditLog);
        }

        // Create a new input stream to allow callers to consume request body
        return new HttpRequest(request.getJDiscRequest(),
                               new SequenceInputStream(new ByteArrayInputStream(data), requestData),
                               request.propertyMap());
    }

    private static AuditLog.Entry.Client parseClient(HttpRequest request) {
        String userAgent = request.getHeader(HttpHeaders.Names.USER_AGENT);
        if (userAgent != null) {
            if (userAgent.startsWith("Vespa CLI/")) {
                return AuditLog.Entry.Client.cli;
            } else if (userAgent.startsWith("Vespa Hosted Client ")) {
                return AuditLog.Entry.Client.hv;
            }
        }
        if (request.getPort() == 443) {
            return AuditLog.Entry.Client.console;
        }
        return AuditLog.Entry.Client.other;
    }

    /** Returns the auditable method of given request, if any */
    private static Optional<AuditLog.Entry.Method> auditableMethod(HttpRequest request) {
        try {
            return Optional.of(AuditLog.Entry.Method.valueOf(request.getMethod().name()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String pathAndQueryOf(URI url) {
        String pathAndQuery = url.getPath();
        String query = url.getQuery();
        if (query != null) {
            pathAndQuery += "?" + query;
        }
        return pathAndQuery;
    }

}

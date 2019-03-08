// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.auditlog;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * This provides read and write operations for the audit log.
 *
 * @author mpolden
 */
public class AuditLogger {

    /** The TTL of log entries. Entries older than this will be removed when the log is updated */
    private static final Duration entryTtl = Duration.ofDays(14);

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

        byte[] data = new byte[0];
        try {
            if (request.getData() != null) {
                data = request.getData().readAllBytes();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Instant now = clock.instant();
        AuditLog.Entry entry = new AuditLog.Entry(now, principal.getName(), method.get(), request.getUri(),
                                                  Optional.of(new String(data, StandardCharsets.UTF_8)));
        try (Lock lock = db.lockAuditLog()) {
            AuditLog auditLog = db.readAuditLog()
                                  .pruneBefore(now.minus(entryTtl))
                                  .with(entry);
            db.writeAuditLog(auditLog);
        }

        // Create a new input stream to allow callers to consume request body
        return new HttpRequest(request.getJDiscRequest(), new ByteArrayInputStream(data), request.propertyMap());
    }

    /** Returns the auditable method of given request, if any */
    private static Optional<AuditLog.Entry.Method> auditableMethod(HttpRequest request) {
        try {
            return Optional.of(AuditLog.Entry.Method.valueOf(request.getMethod().name()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

}

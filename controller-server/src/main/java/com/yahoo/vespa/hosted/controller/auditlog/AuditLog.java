// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.auditlog;

import com.google.common.collect.Ordering;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * This represents the audit log of a hosted Vespa system. The audit log contains manual actions performed through
 * operator APIs served by the controller.
 *
 * @author mpolden
 */
public class AuditLog {

    public static final AuditLog empty = new AuditLog(List.of());

    private final List<Entry> entries;

    /** DO NOT USE. Public for serialization purposes */
    public AuditLog(List<Entry> entries) {
        this.entries = Ordering.natural().immutableSortedCopy(entries);
    }

    /** Returns a new audit log without entries older than given instant */
    public AuditLog pruneBefore(Instant instant) {
        List<Entry> entries = new ArrayList<>(this.entries);
        entries.removeIf(entry -> entry.at().isBefore(instant));
        return new AuditLog(entries);
    }

    /** Returns an new audit log with given entry added */
    public AuditLog with(Entry entry) {
        List<Entry> entries = new ArrayList<>(this.entries);
        entries.add(entry);
        return new AuditLog(entries);
    }

    /** Returns all entries in this. Entries are sorted descending by their timestamp */
    public List<Entry> entries() {
        return entries;
    }

    /** An entry in the audit log. This describes an HTTP request */
    public static class Entry implements Comparable<Entry> {

        private final static int maxDataLength = 1024;
        private final static Comparator<Entry> comparator = Comparator.comparing(Entry::at).reversed();

        private final Instant at;
        private final String principal;
        private final Method method;
        private final URI url;
        private final Optional<String> data;

        public Entry(Instant at, String principal, Method method, URI url, Optional<String> data) {
            this.at = Objects.requireNonNull(at, "at must be non-null");
            this.principal = Objects.requireNonNull(principal, "principal must be non-null");
            this.method = Objects.requireNonNull(method, "method must be non-null");
            this.url = Objects.requireNonNull(url, "url must be non-null");
            this.data = truncateData(data);
        }

        /** Time of the request */
        public Instant at() {
            return at;
        }

        /** The principal performing the request */
        public String principal() {
            return principal;
        }

        /** Request method */
        public Method method() {
            return method;
        }

        /** Request URL */
        public URI url() {
            return url;
        }

        /** Request data. This may be truncated if request data logged in this entry was too large */
        public Optional<String> data() {
            return data;
        }

        @Override
        public int compareTo(@NotNull Entry that) {
            return comparator.compare(this, that);
        }

        /** HTTP methods that should be logged */
        public enum Method {
            POST,
            PATCH,
            DELETE
        }

        private static Optional<String> truncateData(Optional<String> data) {
            Objects.requireNonNull(data, "data must be non-null");
            return data.filter(Predicate.not(String::isBlank))
                       .map(v -> {
                           if (v.length() > maxDataLength) {
                               return v.substring(0, maxDataLength);
                           }
                           return v;
                       });
        }

    }

}

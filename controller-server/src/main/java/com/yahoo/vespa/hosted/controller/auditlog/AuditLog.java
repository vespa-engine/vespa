// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.auditlog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * This represents the audit log of a hosted Vespa system. The audit log contains manual actions performed through
 * operator APIs served by the controller.
 *
 * Entries of the audit log are sorted by their timestamp, in descending order.
 *
 * @author mpolden
 */
public record AuditLog(List<Entry> entries) {

    public static final AuditLog empty = new AuditLog(List.of());

    /** DO NOT USE. Public for serialization purposes */
    public AuditLog(List<Entry> entries) {
        this.entries = Objects.requireNonNull(entries).stream().sorted().toList();
    }

    /** Returns a new audit log without entries older than given instant */
    public AuditLog pruneBefore(Instant instant) {
        List<Entry> entries = new ArrayList<>(this.entries);
        entries.removeIf(entry -> entry.at().isBefore(instant));
        return new AuditLog(entries);
    }

    /** Returns copy of this with given entry added */
    public AuditLog with(Entry entry) {
        List<Entry> entries = new ArrayList<>(this.entries);
        entries.add(entry);
        return new AuditLog(entries);
    }

    /** Returns the first n entries in this. Since entries are sorted descendingly, this will be the n newest entries */
    public AuditLog first(int n) {
        if (entries.size() < n) return this;
        return new AuditLog(entries.subList(0, n));
    }

    /** An entry in the audit log. This describes an HTTP request */
    public record Entry(Instant at, String principal, Method method, String resource, Optional<String> data,
                        Client client) implements Comparable<Entry> {

        final static int maxDataLength = 1024;
        private final static Comparator<Entry> comparator = Comparator.comparing(Entry::at).reversed();

        public Entry(Instant at, Client client, String principal, Method method, String resource, byte[] data) {
            this(Objects.requireNonNull(at, "at must be non-null"),
                 Objects.requireNonNull(principal, "principal must be non-null"),
                 Objects.requireNonNull(method, "method must be non-null"),
                 Objects.requireNonNull(resource, "resource must be non-null"),
                 sanitize(data),
                 Objects.requireNonNull(client, "client must be non-null"));
        }

        /** Time of the request */
        public Instant at() {
            return at;
        }

        /**
         * The client that performed this request. This may be based on user-controlled input, e.g. User-Agent header
         * and is thus not guaranteed to be accurate.
         */
        public Client client() {
            return client;
        }

        /** The principal performing the request */
        public String principal() {
            return principal;
        }

        /** Request method */
        public Method method() {
            return method;
        }

        /** API resource (URL path) */
        public String resource() {
            return resource;
        }

        /** Request data. This may be truncated if request data logged in this entry was too large */
        public Optional<String> data() {
            return data;
        }

        @Override
        public int compareTo(Entry that) {
            return comparator.compare(this, that);
        }

        /** HTTP methods that should be logged */
        public enum Method {
            POST,
            PATCH,
            PUT,
            DELETE
        }

        /** Known clients of the audit log */
        public enum Client {
            /** The Vespa Cloud Console */
            console,
            /** Vespa CLI */
            cli,
            /** Operator tools */
            hv,
            /** Other clients, e.g. curl */
            other,
        }

        private static Optional<String> sanitize(byte[] data) {
            StringBuilder sb = new StringBuilder();
            for (byte b : data) {
                char c = (char) b;
                if (!printableAscii(c) && !tabOrLineBreak(c)) {
                    return Optional.empty();
                }
                sb.append(c);
                if (sb.length() == maxDataLength) {
                    break;
                }
            }
            return Optional.of(sb.toString()).filter(s -> !s.isEmpty());
        }

        private static boolean printableAscii(char c) {
            return c >= 32 && c <= 126;
        }

        private static boolean tabOrLineBreak(char c) {
            return c == 9 || c == 10 || c == 13;
        }

    }

}

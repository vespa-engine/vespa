// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import java.util.Optional;
import java.util.Set;

/**
 * Interface for a basic DNS name resolver.
 *
 * @author mpolden
 */
public interface NameResolver {

    /** Resolve {@link RecordType#A} and {@link RecordType#AAAA} records for given name */
    Set<String> resolveAll(String name);

    /** Resolve given record type(s) for given name */
    Set<String> resolve(String name, RecordType first, RecordType... rest);

    /** Resolve hostname from given IP address, if any exist */
    Optional<String> resolveHostname(String ipAddress);

    /** DNS record types */
    enum RecordType {

        A("A", "IPv4 address"),
        AAAA("AAAA", "IPv6 address");

        private final String value;
        private final String description;

        public String value() { return value; }
        public String description() { return description; }

        RecordType(String value, String description) {
            this.value = value;
            this.description = description;
        }

    }

}

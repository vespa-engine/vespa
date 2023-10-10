// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.config.provision.NodeType.confighost;

/**
 * DNS configuration for a node.
 *
 * @author hakonhall
 */
public class Dns {
    private Dns() {}

    public enum RecordType { FORWARD, PUBLIC_FORWARD, REVERSE }

    /** Returns the set of DNS record types for a host and its children and the given version (ipv6), host type, etc. */
    public static Set<RecordType> recordTypesFor(IP.Version ipVersion, NodeType hostType, CloudName cloudName, boolean enclave) {
        if (cloudName == CloudName.AWS)
            return enclave ?
                   EnumSet.of(RecordType.FORWARD, RecordType.PUBLIC_FORWARD) :
                   EnumSet.of(RecordType.FORWARD, RecordType.PUBLIC_FORWARD, RecordType.REVERSE);

        if (cloudName == CloudName.GCP) {
            if (enclave) {
                return ipVersion.is6() ?
                       EnumSet.of(RecordType.FORWARD, RecordType.PUBLIC_FORWARD) :
                       EnumSet.noneOf(RecordType.class);
            } else {
                return hostType == confighost && ipVersion.is6() ?
                       EnumSet.of(RecordType.FORWARD, RecordType.REVERSE, RecordType.PUBLIC_FORWARD) :
                       EnumSet.of(RecordType.FORWARD, RecordType.REVERSE);
            }
        }

        throw new IllegalArgumentException("Does not manage DNS for cloud " + cloudName);
    }

    /** Verify DNS configuration of given hostname and IP address */
    public static void verify(String hostname, String ipAddress, NodeType nodeType, NameResolver resolver,
                                 CloudAccount cloudAccount, Zone zone) {
        IP.Version version = IP.Version.fromIpAddress(ipAddress);
        Set<RecordType> recordTypes = recordTypesFor(version, nodeType, zone.cloud().name(), cloudAccount.isEnclave(zone));

        if (recordTypes.contains(RecordType.FORWARD)) {
            NameResolver.RecordType recordType = version.is6() ? NameResolver.RecordType.AAAA : NameResolver.RecordType.A;
            Set<String> addresses = resolver.resolve(hostname, recordType);
            if (!addresses.equals(java.util.Set.of(ipAddress)))
                throw new IllegalArgumentException("Expected " + hostname + " to resolve to " + ipAddress +
                                                   ", but got " + addresses);
        }

        if (recordTypes.contains(RecordType.REVERSE)) {
            Optional<String> reverseHostname = resolver.resolveHostname(ipAddress);
            if (reverseHostname.isEmpty())
                throw new IllegalArgumentException(ipAddress + " did not resolve to a hostname");

            if (!reverseHostname.get().equals(hostname))
                throw new IllegalArgumentException(ipAddress + " resolved to " + reverseHostname.get() +
                                                   ", which does not match expected hostname " + hostname);
        }
    }

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.net.InetAddresses;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of a name resolver that always uses a DNS server to resolve the given name. The intention is to avoid
 * possibly incorrect/incomplete records in /etc/hosts.
 *
 * @author mpolden
 */
public class DnsNameResolver implements NameResolver {

    @Override
    public Set<String> resolveAll(String name) {
        return resolve(name, RecordType.A, RecordType.AAAA);
    }

    @Override
    public Set<String> resolve(String name, RecordType first, RecordType... rest) {
        Set<String> results = new HashSet<>();
        for (var type : EnumSet.of(first, rest)) {
            try {
                results.addAll(lookupName(name, type));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return Collections.unmodifiableSet(results);
    }

    @Override
    public Optional<String> resolveHostname(String ipAddress) {
        try {
            // TODO(mpolden): Use lookupName instead. IP address must be translated to its reverse
            //                notation first.
            String hostname = InetAddress.getByName(ipAddress).getHostName();
            return InetAddresses.isInetAddress(hostname) ? Optional.empty() : Optional.of(hostname);
        } catch (UnknownHostException ignored) {
        }
        return Optional.empty();
    }

    private Set<String> lookupName(String name, RecordType type) throws NamingException {
        DirContext ctx = new InitialDirContext();
        Attributes attributes = ctx.getAttributes("dns:/" + name, new String[]{type.value()});
        Attribute attribute = attributes.get(type.value());
        if (attribute == null) {
            return Set.of();
        }
        Set<String> results = new HashSet<>();
        attribute.getAll().asIterator().forEachRemaining(value -> results.add(Objects.toString(value)));
        return results;
    }

}

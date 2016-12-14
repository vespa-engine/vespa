// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableSet;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of a name resolver that always use a DNS server to resolve the given name. The intention is to avoid
 * possibly incorrect/incomplete records in /etc/hosts.
 *
 * @author mpolden
 */
public class DnsNameResolver implements NameResolver {

    private static final String TYPE_A = "A";
    private static final String TYPE_AAAA = "AAAA";

    /** Resolve IP addresses for given host name */
    @Override
    public Set<String> getAllByNameOrThrow(String hostname) {
        try {
            DirContext ctx = new InitialDirContext();
            Attributes attributes = ctx.getAttributes("dns:/" + hostname, new String[] {TYPE_A, TYPE_AAAA});
            Optional<String> inet4Address = getStringAttribute(attributes, TYPE_A);
            Optional<String> inet6Address = getStringAttribute(attributes, TYPE_AAAA);

            ImmutableSet.Builder<String> ipAddresses = ImmutableSet.builder();
            inet4Address.ifPresent(ipAddresses::add);
            inet6Address.ifPresent(ipAddresses::add);
            return ipAddresses.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<String> getStringAttribute(Attributes attributes, String key) throws NamingException {
        Optional<Attribute> attribute = Optional.ofNullable(attributes.get(key));
        if (attribute.isPresent()) {
            return Optional.ofNullable(attribute.get().get()).map(Object::toString);
        }
        return Optional.empty();
    }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.yahoo.log.LogLevel;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Implementation of a name resolver that always uses a DNS server to resolve the given name. The intention is to avoid
 * possibly incorrect/incomplete records in /etc/hosts.
 *
 * @author mpolden
 */
public class DnsNameResolver implements NameResolver {

    private static final Logger logger = Logger.getLogger(DnsNameResolver.class.getName());

    @Override
    public Set<String> getAllByNameOrThrow(String hostname) {
        try {
            Optional<String> cname = lookupName(hostname, Type.CNAME);
            if (cname.isPresent()) {
                hostname = cname.get();
            }
            Optional<String> inet4Address = lookupName(hostname, Type.A);
            Optional<String> inet6Address = lookupName(hostname, Type.AAAA);

            ImmutableSet.Builder<String> ipAddresses = ImmutableSet.builder();
            inet4Address.ifPresent(ipAddresses::add);
            inet6Address.ifPresent(ipAddresses::add);
            return ipAddresses.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<String> getHostname(String ipAddress) {
        try {
            String hostname = InetAddress.getByName(ipAddress).getHostName();
            return InetAddresses.isInetAddress(hostname) ? Optional.empty() : Optional.of(hostname);
        } catch (UnknownHostException e) {
            // This is not an exceptional state hence the debug level
            logger.log(LogLevel.DEBUG, "Unable to resolve ipaddress", e);
        }
        return Optional.empty();
    }

    private Optional<String> lookupName(String name, Type type) throws NamingException {
        DirContext ctx = new InitialDirContext();
        Attributes attributes = ctx.getAttributes("dns:/" + name, new String[]{type.value});
        Optional<Attribute> attribute = Optional.ofNullable(attributes.get(type.value));
        if (attribute.isPresent()) {
            return Optional.ofNullable(attribute.get().get()).map(Object::toString);
        }
        return Optional.empty();
    }

    private enum Type {

        A("A"),
        AAAA("AAAA"),
        CNAME("CNAME");

        private final String value;

        Type(String value) {
            this.value = value;
        }
    }

}

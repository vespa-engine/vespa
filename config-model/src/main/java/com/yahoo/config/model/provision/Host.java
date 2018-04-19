// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.Version;
import com.yahoo.config.provision.Flavor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A hostname with zero or more aliases. This is immutable.
 *
 * @author hmusum
 */
public class Host {

    private final String hostname;
    private final ImmutableList<String> aliases;
    private final Optional<Flavor> flavor;
    private final Optional<Version> version;

    public Host(String hostname) {
        this(hostname, ImmutableList.of(), Optional.empty());
    }

    public Host(String hostname, List<String> hostAliases) {
        this(hostname, hostAliases, Optional.empty());
    }

    public Host(String hostname, List<String> hostAliases, Optional<Flavor> flavor) {
        this(hostname, hostAliases, flavor, Optional.empty());
    }

    public Host(String hostname, List<String> hostAliases, Optional<Flavor> flavor, Optional<Version> version) {
        this.hostname = hostname;
        this.aliases = ImmutableList.copyOf(hostAliases);
        this.flavor = flavor;
        this.version = version;
    }

    public String hostname() { return hostname; }

    /** Returns an immutable list of the aliases of this node, which may be empty but never null */
    public List<String> aliases() { return aliases; }

    public Optional<Flavor> flavor() { return flavor; }

    /** The current Vespa version running on this host, or empty if not known */
    public Optional<Version> version() { return version; }

    @Override
    public String toString() {
        return hostname + (aliases.size() > 0 ? " (aliases: " + aliases + ")" : "" ) +
                (flavor.isPresent() ? " (flavor: " + flavor.get() + ")" : "");
    }

}

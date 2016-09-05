// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

/**
 * A hostname with zero or more aliases. This is immutable.
 *
 * @author hmusum
 */
public class Host {

    private final String hostname;
    private final ImmutableList<String> aliases;

    public Host(String hostname) {
        this.hostname = hostname;
        this.aliases = ImmutableList.of();
    }

    public Host(String hostname, List<String> hostAliases) {
        this.hostname = hostname;
        this.aliases = ImmutableList.copyOf(hostAliases);
    }

    public String hostname() { return hostname; }

    /** Returns an immutable list of the aliases of this node, which may be empty but never null */
    public List<String> aliases() { return aliases; }

    @Override
    public String toString() {
        return hostname + (aliases.size() > 0 ? " (aliases: " + aliases + ")" : "" );
    }

}

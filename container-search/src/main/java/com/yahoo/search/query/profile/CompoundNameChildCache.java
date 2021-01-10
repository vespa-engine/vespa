// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.CompoundName;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache for compound names created through {@link CompoundName#append(String)}.
 * Creating new {@link CompoundName}s can be expensive, and since they are immutable, they
 * are safe to cache and reuse. Use this if you will create <em>a lot</em> of them, by appending suffixes.
 *
 * @author jonmv
 */
public final class CompoundNameChildCache {

    private final Map<CompoundName, Map<String, CompoundName>> cache = new HashMap<>();

    public CompoundName append(CompoundName prefix, String suffix) {
        return cache.computeIfAbsent(prefix, __ -> new HashMap<>()).computeIfAbsent(suffix, prefix::append);
    }

}

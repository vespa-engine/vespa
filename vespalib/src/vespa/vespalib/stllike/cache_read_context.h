// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib {

/**
 * Controls how a cache read is performed, and reports how it was served.
 *
 * use_no_populate is an input: if set, the read neither inserts into the cache
 * nor updates LRU/LFU state (see cache::read_no_populate()).
 * found_in_cache and was_protected are outputs, set by the read.
 */
struct CacheReadContext {
    bool use_no_populate = false;
    bool found_in_cache = false;
    bool was_protected = false;
};

// A normal read that populates the cache on a miss and updates LRU/LFU state on a hit.
constexpr CacheReadContext normal_cache_read() noexcept {
    return {};
}

// A read that uses a cached value if present, but never populates or reorders the cache.
constexpr CacheReadContext no_populate_cache_read() noexcept {
    CacheReadContext context;
    context.use_no_populate = true;
    return context;
}

} // namespace vespalib

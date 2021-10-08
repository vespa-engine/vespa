// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <sys/types.h>

namespace search {

struct CacheStats {
    size_t hits;
    size_t misses;
    size_t elements;
    size_t memory_used;
    size_t invalidations;

    CacheStats()
        : hits(0),
          misses(0),
          elements(0),
          memory_used(0),
          invalidations(0)
    { }

    CacheStats(size_t hits_, size_t misses_, size_t elements_, size_t memory_used_, size_t invalidations_)
        : hits(hits_),
          misses(misses_),
          elements(elements_),
          memory_used(memory_used_),
          invalidations(invalidations_)
    { }

    CacheStats &
    operator+=(const CacheStats &rhs)
    {
        hits += rhs.hits;
        misses += rhs.misses;
        elements += rhs.elements;
        memory_used += rhs.memory_used;
        invalidations += rhs.invalidations;
        return *this;
    }

    size_t lookups() const { return hits + misses; }
};

}


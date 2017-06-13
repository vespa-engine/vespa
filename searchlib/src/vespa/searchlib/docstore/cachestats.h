// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <sys/types.h>

namespace search {

struct CacheStats {
    size_t hits;
    size_t misses;
    size_t elements;
    size_t memory_used;

    CacheStats()
        : hits(0),
          misses(0),
          elements(0),
          memory_used(0)
    { }

    CacheStats(size_t hit, size_t miss, size_t elem, size_t mem)
        : hits(hit),
          misses(miss),
          elements(elem),
          memory_used(mem)
    { }

    CacheStats &
    operator+=(const CacheStats &rhs)
    {
        hits += rhs.hits;
        misses += rhs.misses;
        elements += rhs.elements;
        memory_used += rhs.memory_used;
        return *this;
    }
};

}  // namespace search


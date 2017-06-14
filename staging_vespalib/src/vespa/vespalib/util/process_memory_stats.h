// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib {

/*
 * Class for linux specific way to get memory stats for current process.
 */
class ProcessMemoryStats
{
    uint64_t _mapped_virt; // virtual size
    uint64_t _mapped_rss;  // resident size
    uint64_t _anonymous_virt; // virtual size
    uint64_t _anonymous_rss;  // resident size
    uint64_t _mappings_count; // number of mappings
                              // (limited by sysctl vm.max_map_count)

public:
    ProcessMemoryStats();
    static ProcessMemoryStats create(); // based on /proc/self/smaps
    uint64_t getMappedVirt() const { return _mapped_virt; }
    uint64_t getMappedRss() const { return _mapped_rss; }
    uint64_t getAnonymousVirt() const { return _anonymous_virt; }
    uint64_t getAnonymousRss() const { return _anonymous_rss; }
    uint64_t getMappingsCount() const { return _mappings_count; }

    /** for unit tests only */
    ProcessMemoryStats(uint64_t, uint64_t, uint64_t, uint64_t, uint64_t);
};

} // namespace vespalib

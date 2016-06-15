// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib
{

/*
 * Class for linux specific way to get memory stats for current process.
 */
class ProcessMemoryStats
{
    uint64_t _mapped_virt; // virtual size
    uint64_t _mapped_rss;  // resident size
    uint64_t _anonymous_virt; // virtual size
    uint64_t _anonymous_rss;  // resident size

public:
    ProcessMemoryStats();
    ProcessMemoryStats(uint64_t mapped_virt, uint64_t mapped_rss,
                       uint64_t anonymous_virt, uint64_t anonymous_rss);
    static ProcessMemoryStats create(); // based on /proc/self/smaps
    uint64_t getMappedVirt() const { return _mapped_virt; }
    uint64_t getMappedRss() const { return _mapped_rss; }
    uint64_t getAnonymousVirt() const { return _anonymous_virt; }
    uint64_t getAnonymousRss() const { return _anonymous_rss; }
};

} // namespace vespalib

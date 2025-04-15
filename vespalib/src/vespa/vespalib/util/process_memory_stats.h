// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <string>
#include <functional>

namespace vespalib {

class asciistream;
/*
 * Class for linux specific way to get memory stats for current process.
 */
class ProcessMemoryStats
{
public:
    enum SamplingStrategy {
        SMAPS,
        STATM
    };

private:
    uint64_t _virt; // virtual size
    uint64_t _mapped_rss;  // resident size
    uint64_t _anonymous_rss;  // resident size

    static ProcessMemoryStats createStatsFromSmaps();
    static ProcessMemoryStats createStatsFromStatm();
    static ProcessMemoryStats sample(SamplingStrategy strategy);

public:
    static size_t PAGE_SIZE;

    ProcessMemoryStats();
    /**
     * Sample memory stats for the current process based on reading the file /proc/self/smaps.
     *
     * Samples are taken until two consecutive memory stats are similar given the size epsilon.
     * This ensures a somewhat consistent memory stats snapshot.
     */
    static ProcessMemoryStats create(double epsilon, SamplingStrategy strategy = STATM);
    uint64_t getVirt() const { return _virt; }
    uint64_t getMappedRss() const { return _mapped_rss; }
    uint64_t getAnonymousRss() const { return _anonymous_rss; }
    bool similarTo(const ProcessMemoryStats &rhs, double epsilon) const;
    std::string toString() const;
    bool operator < (const ProcessMemoryStats & rhs) const { return _anonymous_rss < rhs._anonymous_rss; }

    /** for unit tests only */
    ProcessMemoryStats(uint64_t, uint64_t, uint64_t);
    static ProcessMemoryStats parseStatm(asciistream &statm);
};

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <string>

namespace vespalib {

class asciistream;
/*
 * Class for linux specific way to get memory stats for current process.
 */
class ProcessMemoryStats {
    uint64_t _virt;          // virtual size
    uint64_t _mapped_rss;    // resident size
    uint64_t _anonymous_rss; // resident size
    size_t   _transient_memory;

    [[nodiscard]] static ProcessMemoryStats createStatsFromStatm();
    [[nodiscard]] static ProcessMemoryStats create_stats_from_statm(uint64_t& transient_memory_generation);

public:
    static size_t normal_page_size;

    ProcessMemoryStats() noexcept;
    /**
     * Sample memory stats for the current process based on reading the file /proc/self/statm.
     *
     * Samples are taken until two consecutive memory stats are similar given the size epsilon.
     * This ensures a somewhat consistent memory stats snapshot.
     */
    [[nodiscard]] static ProcessMemoryStats create(double epsilon);
    [[nodiscard]] uint64_t getVirt() const noexcept { return _virt; }
    [[nodiscard]] uint64_t getMappedRss() const noexcept { return _mapped_rss; }
    [[nodiscard]] uint64_t getAnonymousRss() const noexcept { return _anonymous_rss; }
    [[nodiscard]] size_t transient_memory() const noexcept { return _transient_memory; }
    [[nodiscard]] bool similarTo(const ProcessMemoryStats& rhs, double epsilon) const noexcept;
    [[nodiscard]] std::string toString() const;
    bool operator<(const ProcessMemoryStats& rhs) const noexcept { return _anonymous_rss < rhs._anonymous_rss; }

    /** for unit tests only */
    ProcessMemoryStats(uint64_t virt, uint64_t mapped_rss, uint64_t anonymous_rss) noexcept;
    ProcessMemoryStats(uint64_t virt, uint64_t mapped_rss, uint64_t anonymous_rss, size_t transient_memory) noexcept;
    [[nodiscard]] static ProcessMemoryStats parseStatm(asciistream& statm);
};

} // namespace vespalib

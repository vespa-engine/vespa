// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace vespalib::alloc {

/*
 * Collects memory ranges that will be read soon and hints the kernel about them
 * with madvise(MADV_WILLNEED).
 *
 * For file backed memory (e.g. memory from MmapFileAllocator) this starts
 * asynchronous readahead of the pages into the page cache, allowing many disk
 * reads to be in flight at the same time instead of taking one synchronous
 * page fault per range when the memory is later accessed.
 *
 * Ranges are widened to page boundaries, sorted and merged before advising, to
 * use as few system calls as possible. Advising is best effort; failures are
 * ignored.
 */
class WillNeedAdvisor {
public:
    struct Range {
        uintptr_t start;
        uintptr_t end; // exclusive
        bool operator==(const Range& rhs) const noexcept = default;
    };
    struct Result {
        size_t ranges = 0; // number of (merged) ranges advised
        size_t bytes = 0;  // number of bytes advised, after widening to page boundaries
    };

private:
    std::vector<Range> _ranges;

public:
    WillNeedAdvisor();
    ~WillNeedAdvisor();
    void reserve(size_t num_ranges) { _ranges.reserve(num_ranges); }
    void add(const void* buf, size_t size);
    bool empty() const noexcept { return _ranges.empty(); }
    Result advise();

    // Widen ranges to page boundaries, then sort and merge overlapping or adjacent ranges.
    static void merge_page_ranges(std::vector<Range>& ranges, size_t page_size);
};

} // namespace vespalib::alloc

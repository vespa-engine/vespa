// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace vespalib::alloc {

/*
 * Collects memory ranges that will be read soon and hints the kernel about them
 * with MADV_WILLNEED.
 *
 * For file backed memory (e.g. memory from MmapFileAllocator) this starts
 * asynchronous readahead of the pages into the page cache, allowing many disk
 * reads to be in flight at the same time instead of taking one synchronous
 * page fault per range when the memory is later accessed.
 *
 * Ranges are widened to page boundaries, sorted by address and merged, then split
 * into chunks of at most max_range_size bytes. Note that the sorting means the reads
 * are submitted in address order, not in the order the caller will access the
 * memory. That is intentional: it minimizes the number of ranges, and with all
 * reads in flight at once the submission order is not significant.
 *
 * Chunking is needed because the kernel caps the readahead done for a single
 * MADV_WILLNEED range (to the larger of the device's max I/O size and its readahead
 * window); the rest of a longer range would silently not be read ahead. The cap
 * for the device holding a file can be found with max_range_size_for_path().
 *
 * On Linux, all chunks are advised with as few process_madvise() calls as
 * possible (a chunk that fails, e.g. because it is not mapped, is skipped and
 * costs an extra call). If process_madvise() is not supported (old kernel,
 * missing CAP_SYS_NICE before Linux 6.13, seccomp filter, ...) it falls back to
 * one madvise() call per chunk, and stops trying process_madvise() for the rest
 * of the process lifetime.
 *
 * Advising is best effort; failures are ignored except that they are not counted
 * in the result.
 */
class WillNeedAdvisor {
public:
    struct Range {
        uintptr_t start;
        uintptr_t end; // exclusive
        bool operator==(const Range& rhs) const noexcept = default;
    };
    struct Result {
        size_t ranges = 0;   // number of ranges (after merging and chunking) successfully advised
        size_t bytes = 0;    // number of bytes successfully advised, after widening to page boundaries
        size_t syscalls = 0; // number of system calls used
    };
    enum class Method {
        BATCHED,   // process_madvise() if possible, otherwise madvise() per range
        PER_RANGE, // madvise() per range
    };
    // Default readahead window on Linux (read_ahead_kb = 128).
    static constexpr size_t default_max_range_size = 128 * 1024;

private:
    std::vector<Range> _ranges;
    size_t             _max_range_size;

public:
    WillNeedAdvisor() : WillNeedAdvisor(default_max_range_size) {}
    explicit WillNeedAdvisor(size_t max_range_size);
    ~WillNeedAdvisor();
    void reserve(size_t num_ranges) { _ranges.reserve(num_ranges); }
    void add(const void* buf, size_t size);
    bool empty() const noexcept { return _ranges.empty(); }
    Result advise(Method method = Method::BATCHED);

    // Widen ranges to page boundaries, sort and merge overlapping or adjacent ranges, then split
    // ranges longer than max_range_size (rounded down to a page multiple). page_size must be a power of 2.
    static void merge_page_ranges(std::vector<Range>& ranges, size_t page_size,
                                  size_t max_range_size = default_max_range_size);
    // True if a batched (process_madvise) call has failed in this process, and batching is disabled.
    static bool batching_disabled() noexcept;
    // The longest range the kernel will read ahead for a single MADV_WILLNEED range on memory backed by
    // a file at the given path (or below it, if the path does not exist yet): the larger of the readahead
    // window and the max I/O size of the device holding the file. Returns default_max_range_size if the
    // device limits are not known (not Linux, or a file system without a visible block device).
    static size_t max_range_size_for_path(const std::string& path);
};

} // namespace vespalib::alloc

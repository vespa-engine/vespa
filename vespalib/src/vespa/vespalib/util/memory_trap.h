// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace vespalib {

/**
 * Guard for attempting to detect spurious writes (and if possible; reads) to a memory region.
 *
 * If supported by the OS+HW, as much as possible of the buffer will be mapped
 * as non-readable and non-writable. This immediately triggers a SIGSEGV for any
 * spurious read or write to the mapped buffer sub-range.
 *
 * For memory map-backed trapping to be used, all of the following must hold:
 *   - The process must be running on Linux and on hardware with a page size of 4 KiB
 *   - The environment variable VESPA_USE_MPROTECT_TRAP must be set and have the value 'yes'
 *   - The trap buffer must be long enough to fit at least one whole 4 KiB-aligned page
 *   - The buffer passed to the trapper must originally have been allocated via mmap().
 *     This should hold for any reasonable implementation of malloc().
 *
 * Regardless of whether memory map-backed trapping is used, the buffer will always be
 * filled with all zeroes upon construction. If any buffer byte is non-zero upon
 * destruction, the process will be terminated with a corruption error in the logs.
 *
 * If buffer mapping fails during construction, the trapper falls back to just checking
 * buffer contents. This may happen if the kernel has exhausted the bookkeeping-structures
 * for keeping track of separate virtual memory ranges.
 *
 * Note that due to possible interference with things like hugepages etc, VESPA_USE_MPROTECT_TRAP
 * should only be selectively enabled.
 */
class MemoryRangeTrapper {
    char*  _trap_buf;
    size_t _buf_len;
    size_t _trap_offset;
    size_t _trap_len;
public:
    MemoryRangeTrapper(char* trap_buf, size_t buf_len) noexcept;
    ~MemoryRangeTrapper();

    MemoryRangeTrapper(const MemoryRangeTrapper&)     = delete;
    MemoryRangeTrapper(MemoryRangeTrapper&&) noexcept = delete;

    // Exposed for testing only
    char* buffer() const noexcept { return _trap_buf; }
    size_t size() const noexcept { return _buf_len; }

    void check_and_release() noexcept;

    [[nodiscard]] static bool hw_trapping_enabled() noexcept;
private:
    void rw_protect_buffer_if_possible();
    void unprotect_buffer_to_read_only();
    void unprotect_buffer_to_read_and_write();
    void verify_buffer_is_all_zeros();
};

/**
 * Places a memory trap "inline" with other variables in an object. I.e. the trap will
 * be in a memory range that is a sub-range of that taken up by the owning object.
 *
 * Always takes up at least 8 KiB of space.
 */
template <size_t Guard4KPages>
class InlineMemoryTrap {
    static_assert(Guard4KPages > 0);
    constexpr static size_t BufSize = 4096 * (Guard4KPages + 1);
    char _trap_buf[BufSize];
    MemoryRangeTrapper _trapper;
public:
    InlineMemoryTrap() noexcept : _trap_buf(), _trapper(_trap_buf, BufSize) {}
    ~InlineMemoryTrap() = default;

    InlineMemoryTrap(const InlineMemoryTrap&)     = delete;
    InlineMemoryTrap(InlineMemoryTrap&&) noexcept = delete;

    // Exposed for testing only
    const MemoryRangeTrapper& trapper() const noexcept { return _trapper; }
};

/**
 * Allocates a 4 KiB-aligned heap buffer and watches it for spurious access.
 * Useful for distributing traps across various allocation size-classes.
 */
class HeapMemoryTrap {
    char* _trap_buf;
    MemoryRangeTrapper _trapper;
public:
    explicit HeapMemoryTrap(size_t trap_4k_pages);
    ~HeapMemoryTrap();

    HeapMemoryTrap(const HeapMemoryTrap&)     = delete;
    HeapMemoryTrap(HeapMemoryTrap&&) noexcept = delete;

    // Exposed for testing only
    const MemoryRangeTrapper& trapper() const noexcept { return _trapper; }
};

}

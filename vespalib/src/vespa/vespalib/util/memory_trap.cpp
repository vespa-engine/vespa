// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_trap.h"
#include <string_view>
#include <cassert>
#include <cerrno>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <sys/mman.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.util.memory_trap");

using namespace std::string_view_literals;

namespace vespalib {

namespace {

// Have some symbols that provide immediate context in a crash backtrace
[[noreturn]] void abort_due_to_guard_bits_tampered_with() __attribute__((noinline));
[[noreturn]] void abort_due_to_guard_bits_tampered_with() {
    abort();
}

[[noreturn]] void abort_due_to_PROTECTED_guard_bits_tampered_with() __attribute__((noinline));
[[noreturn]] void abort_due_to_PROTECTED_guard_bits_tampered_with() {
    abort();
}

} // anon ns

MemoryRangeTrapper::MemoryRangeTrapper(char* trap_buf, size_t buf_len) noexcept
    : _trap_buf(trap_buf),
      _buf_len(buf_len),
      _trap_offset(0),
      _trap_len(0)
{
    if (_buf_len > 0) {
        memset(trap_buf, 0, _buf_len);
    }
    rw_protect_buffer_if_possible();
}

MemoryRangeTrapper::~MemoryRangeTrapper() {
    check_and_release();
}

void MemoryRangeTrapper::check_and_release() noexcept {
    unprotect_buffer_to_read_only(); // Make sure sanity check can't race with writes
    verify_buffer_is_all_zeros();
    unprotect_buffer_to_read_and_write();
    _trap_len = _buf_len = 0;
}

void MemoryRangeTrapper::verify_buffer_is_all_zeros() {
    for (size_t i = 0; i < _buf_len; ++i) {
        if (_trap_buf[i] != 0) {
            const bool in_protected_area = ((i >= _trap_offset) && (i < _trap_offset + _trap_len));
            LOG(error, "Memory corruption detected! Offset %zu into buffer %p: 0x%.2x != 0x00%s",
                i, _trap_buf, static_cast<unsigned int>(_trap_buf[i]),
                in_protected_area ? ". CORRUPTION IN R/W PROTECTED MEMORY!" : "");
            if (in_protected_area) {
                abort_due_to_PROTECTED_guard_bits_tampered_with();
            } else {
                abort_due_to_guard_bits_tampered_with();
            }
        }
    }
}

#ifdef __linux__

namespace {

bool has_4k_pages() noexcept {
    return (sysconf(_SC_PAGESIZE) == 4096);
}

constexpr bool is_4k_aligned(size_t v) noexcept {
    return (v % 4096) == 0;
}

constexpr size_t align_up_4k(size_t v) noexcept {
    return (v + 4095) & ~4095ULL;
}

constexpr size_t align_down_4k(size_t v) noexcept {
    return v & ~4095ULL;
}

bool env_var_implies_enabled(const char* env_var) noexcept {
    const char* ev = getenv(env_var);
    return ((ev != nullptr) && (("true"sv == ev) || "yes"sv == ev));
}

bool mprotect_trapping_is_enabled() noexcept {
    static const bool enabled = (has_4k_pages() && env_var_implies_enabled("VESPA_USE_MPROTECT_TRAP"));
    return enabled;
}

} // anon ns

void MemoryRangeTrapper::rw_protect_buffer_if_possible() {
    static_assert(std::is_same_v<size_t, uintptr_t>);
    const auto aligned_start = align_up_4k(reinterpret_cast<uintptr_t>(_trap_buf));
    const auto aligned_end   = align_down_4k(reinterpret_cast<uintptr_t>(_trap_buf + _buf_len));
    if ((aligned_end > aligned_start) && mprotect_trapping_is_enabled()) {
        _trap_offset = aligned_start - reinterpret_cast<uintptr_t>(_trap_buf);
        _trap_len    = aligned_end   - aligned_start;
        assert(is_4k_aligned(_trap_len));

        LOG(info, "attempting mprotect(%p + %zu = %p, %zu, PROT_NONE)",
            _trap_buf, _trap_offset, _trap_buf + _trap_offset, _trap_len);
        int ret = mprotect(_trap_buf + _trap_offset, _trap_len, PROT_NONE);
        if (ret != 0) {
            LOG(warning, "Failed to mprotect(%p + %zu, %zu, PROT_NONE). errno = %d. "
                         "Falling back to unprotected mode.",
                _trap_buf, _trap_offset, _trap_len, errno);
            _trap_offset = _trap_len = 0;
        }
    }
}

bool MemoryRangeTrapper::hw_trapping_enabled() noexcept {
    return mprotect_trapping_is_enabled();
}

void MemoryRangeTrapper::unprotect_buffer_to_read_only() {
    if (_trap_len > 0) {
        int ret = mprotect(_trap_buf + _trap_offset, _trap_len, PROT_READ);
        assert(ret == 0 && "failed to un-protect memory region to PROT_READ");
    }
}

void MemoryRangeTrapper::unprotect_buffer_to_read_and_write() {
    if (_trap_len > 0) {
        int ret = mprotect(_trap_buf + _trap_offset, _trap_len, PROT_READ | PROT_WRITE);
        assert(ret == 0 && "failed to un-protect memory region to PROT_READ | PROT_WRITE");
    }
}

#else // Not on Linux, fall back to no-ops

void MemoryRangeTrapper::rw_protect_buffer_if_possible() { /* no-op */ }
bool MemoryRangeTrapper::hw_trapping_enabled() noexcept { return false; }
void MemoryRangeTrapper::unprotect_buffer_to_read_only() { /* no-op */ }
void MemoryRangeTrapper::unprotect_buffer_to_read_and_write() { /* no-op */ }

#endif

HeapMemoryTrap::HeapMemoryTrap(size_t trap_4k_pages)
    : _trap_buf(static_cast<char*>(aligned_alloc(4096, trap_4k_pages * 4096))),
      _trapper(_trap_buf, _trap_buf ? trap_4k_pages * 4096 : 0)
{
}

HeapMemoryTrap::~HeapMemoryTrap() {
    _trapper.check_and_release();
    free(_trap_buf);
}

}

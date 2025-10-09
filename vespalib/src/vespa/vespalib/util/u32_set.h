// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "alloc.h"
#include <vespa/vespalib/stllike/allocator.h>
#include <cstdint>
#include <vector>

#include <cassert>

namespace vespalib {

/**
 * Very simplified set of u32 values (except zero!) that supports only testing
 * for presence and inserting.
 *
 * Initially sparse and hash-based; becomes a dense bit vector once the allocated
 * size no longer makes sense for a sparse structure. Note that it will cost a lot
 * of cycles to start from zero and rehash on resize before hitting this threshold,
 * so use a dense BitVector from the start if you know it's likely you'll hit sizes
 * where a dense bit vector will win.
 *
 * Bit 0 is a sentinel for unset values, and must never be set or queried.
 */
struct U32Set {
    constexpr static uint32_t hash32(uint32_t h) noexcept {
        // This is the public domain Murmurhash3 avalanche routine from
        // https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp
        h ^= h >> 16;
        h *= 0x85ebca6bUL;
        h ^= h >> 13;
        h *= 0xc2b2ae35UL;
        h ^= h >> 16;
        return h;
    }

    // Must have implicit zeroing behavior of elements
    // TODO should have aligned alloc...
    using BufferType = std::vector<uint32_t, allocator_large<uint32_t>>;

    uint32_t _size;
    uint32_t _capacity;
    BufferType _buf;

    explicit U32Set(uint32_t initial_capacity);
    ~U32Set();

    [[nodiscard]] size_t size() const noexcept { return _size; }
    [[nodiscard]] size_t capacity() const noexcept { return _capacity; }

    constexpr static uint32_t dense_bitvector_u32_elem_count() noexcept {
        return UINT32_MAX / 32 / sizeof(uint32_t);
    }

    constexpr static uint32_t dense_set_capacity_threshold() noexcept {
        return dense_bitvector_u32_elem_count();
    }

    [[nodiscard]] bool is_sparse() const noexcept {
        return _capacity < dense_set_capacity_threshold();
    }

    [[nodiscard]] constexpr static size_t max_load_factor_adjusted(size_t capacity) noexcept {
        return (capacity/4 * 3); // max 3/4 load factor
    }

    [[nodiscard]] bool should_grow() const noexcept {
        return _size > max_load_factor_adjusted(_capacity);
    }

    void grow_and_rehash() __attribute__((noinline));

    static void insert_for_rehash(uint32_t* buf, const uint32_t idx, const uint32_t new_capacity) noexcept {
        size_t slot = hash32(idx) & (new_capacity - 1);
        while (buf[slot] != 0) {
            slot = (slot + 1) & (new_capacity - 1); // le cheeky probe
        }
        buf[slot] = idx;
    }

    [[nodiscard]] uint32_t sparse_slot_of(uint32_t idx) const noexcept {
        return hash32(idx) & (_capacity - 1);
    }

    [[nodiscard]] uint32_t probe_count(uint32_t idx) const noexcept {
        uint32_t probes = 1;
        if (is_sparse()) [[likely]] {
            size_t slot = sparse_slot_of(idx);
            while (true) {
                if (_buf[slot] == 0 || _buf[slot] == idx) {
                    return probes;
                }
                // else: keep probing
                slot = (slot + 1) & (_capacity - 1); // rotate around
                ++probes;
            }
        } else {
            return 1;
        }
    }

    void prefetch(uint32_t idx) const noexcept {
        if (is_sparse()) [[likely]] {
            size_t slot = sparse_slot_of(idx);
            __builtin_prefetch(_buf.data() + slot);
        } else {
            __builtin_prefetch(_buf.data() + (idx / 32));
        }
    }

    [[nodiscard]] bool is_set(const uint32_t idx) const noexcept {
        if (is_sparse()) [[likely]] {
            size_t slot = sparse_slot_of(idx);
            while (true) {
                if (_buf[slot] == 0) {
                    return false;
                } else if (_buf[slot] == idx) {
                    return true;
                }
                // else: keep probing
                slot = (slot + 1) & (_capacity - 1); // rotate around
            }
        } else {
            return (_buf[idx / 32] & (1u << (idx % 32))) != 0;
        }
    }

    [[nodiscard]] bool try_set(const uint32_t idx) noexcept {
        if (is_sparse()) [[likely]] {
            size_t slot = sparse_slot_of(idx);
            assert(slot < _buf.size());
            while (true) {
                if (_buf[slot] == 0) {
                    _buf[slot] = idx;
                    ++_size; // This may violate max load factor
                    if (should_grow()) [[unlikely]] {
                        grow_and_rehash();
                    }
                    return true;
                } else if (_buf[slot] == idx) {
                    return false;
                }
                // else: keep probing
                slot = (slot + 1) & (_capacity - 1); // rotate around
            }
        } else {
            const bool was_unset = (_buf[idx / 32] & (1u << (idx % 32))) == 0;
            _size += was_unset ? 1 : 0;
            _buf[idx / 32] |= (1u << (idx % 32));
            return was_unset;
        }
    }

};

}

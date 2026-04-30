// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/allocator.h>

#include <cstdint>
#include <vector>

// We don't use __builtin_rev_crc32 intrinsics since they take an explicit polynomial
// as input, so it's not guaranteed we end up with the native CRC32 instructions. Since
// we only use this for in-process hashing purposes, the actual underlying polynomial
// does not really matter. We just want a cheap way to mix bits.
// This approach is inspired by the integer hash mixing in the Abseil Hash sub-library.
#if defined(__aarch64__) && defined(__ARM_FEATURE_CRC32)
#include <arm_acle.h>
#define VESPA_NATIVE_CRC32_U32 __crc32cw
#elif defined(__x86_64__)
#include <nmmintrin.h>
#define VESPA_NATIVE_CRC32_U32 _mm_crc32_u32
#else
#error Unknown architecture, no known native CRC32 intrinsic
#endif

namespace vespalib {

/**
 * Very simplified unordered set of u32 values (except zero!) that supports only
 * testing for presence and inserting. This set is always sparse regardless of
 * the number of elements contained within it. Use a dense bitvector instead if
 * you know that the number of set elements will be large enough that a sparse
 * representation does not make sense in practice (see: `prefer_bitvector`).
 *
 * The element array stores u32 values verbatim, except the value 0 which is a
 * sentinel for unset values and should not be inserted or queried for existence.
 *
 * The core algorithm used for set lookups and insert is open addressing with
 * quadratic probing. This is chosen to keep the expected number of cache line
 * misses per lookup as low as possible, ideally ~1 (assuming a good hash
 * distribution). Probing starts at the element indicated by its hash, proceeding
 * with a quadratically increasing array index (wrapping around at the array end)
 * until _either_ an existing array slot is found with the value _or_ a
 * zero-valued slot is found. For inserts where the element does not preexist,
 * the very first observed zero valued slot is used to store the value.
 *
 * Through enforcing a maximum load factor, we maintain the invariant that there
 * will _always_ be at least one free slot in the array, i.e. all probes must
 * by definition terminate (no infinite loops).
 *
 * Always using the first available zero slot for inserts forms the inductive
 * property that observing a zero-slot during a probe means that the value can
 * not possibly exist at a _later_ probe point. Consequently, probing for
 * insertion can insert at that slot without introducing duplicates, and probing
 * for lookups can immediately return "not found" since the value cannot exist
 * at any later probe point (it would have been inserted in this slot already).
 *
 * Note that this particular invariant would be violated if we supported deletes
 * (or we would need a tombstone sentinel), so that's the main reasons for why
 * we only support insertion and lookups.
 *
 * This is inspired by the Swisstable approach to hash tables, but without a
 * dedicated metadata section and without SIMD since our slots are 4x the size
 * of Swisstable's 8-bit entries and the overhead of SIMD vector loading and
 * mask management costs more than it gains (at least for AArch64 NEON with
 * 128-bit registers). Since it has explicit per-slot metadata, Swisstable also
 * supports deletes via tombstone metadata entries.
 *
 * As thread safe as a std::vector.
 */
class UnorderedU32Set {
    // Must have implicit zeroing behavior of elements
    using BufferType = std::vector<uint32_t, allocator_large<uint32_t>>;

    struct PrivateCtorTag {};

    size_t _size;
    // Perf note: even though this technically duplicates information that can
    // be gleaned from _buf.size(), having a dedicated variable for this is
    // measurably faster. Possibly due to _buf.size() on libstdc++ needing pointer
    // arithmetic (which will do element size shifting), which introduces a
    // dependency chain for a value we need very early in the lookup process.
    size_t     _capacity;
    BufferType _buf;

public:
    using value_type = uint32_t;
    using size_type = size_t;

    UnorderedU32Set();
    explicit UnorderedU32Set(uint32_t initial_capacity);
    ~UnorderedU32Set();

    [[nodiscard]] size_t size() const noexcept { return _size; }
    [[nodiscard]] bool empty() const noexcept { return _size == 0; }
    [[nodiscard]] size_t capacity() const noexcept { return _capacity; }

    // Given a set with `full_set_size` elements where `expected_subset_size`
    // elements are expected to be used, this function returns whether using
    // a _dense_ bit vector should be preferred instead of a sparse set. Note
    // that this does not take memory buffer size alignment into account.
    [[nodiscard]] constexpr static bool prefer_bitvector(const uint32_t expected_subset_size,
                                                         const uint32_t full_set_size) noexcept {
        // Let m be expected size of set size n. A dense bit vector will have memory use
        // n/8 bytes whereas a sparse set will use 4n + 4(1/4n) = 5n bytes. The extra 1/4n
        // bytes is because we can never exceed a load factor of 3/4, so add 1/4 on top.
        // Intersection at 5m = n/8 ==> 5m/5 = n/(8*5) ==> m = n/40
        return expected_subset_size > (full_set_size / 40);
    }

    // Quadratic probe sequence for power of two array sizes. This uses the common
    // triangular numbers sequence, as each of N slots will be visited exactly once
    // in the course of N sequence increments. Since quadratic probing always starts
    // out close to the original probe point, there is a high likelihood that a slot
    // will be found within the same cache line.
    //
    // See https://en.wikipedia.org/wiki/Quadratic_probing#Quadratic_function:
    // "For m = 2^n, a good choice for the constants are c1 = c2 = 1/2, as the
    //  values of h(k,i) for i in [0, m−1] are all distinct (in fact, it is a
    //  permutation on [0, m−1]). This leads to a probe sequence of h(k), h(k) + 1,
    //  h(k) + 3, h(k) + 6 ... (the triangular numbers), where values increase by
    //  1, 2, 3, ...".
    //
    // Inspired by Abseil `probe_seq` to avoid duplicating logic per function that
    // needs to probe.
    class quadratic_probe_sequence {
        const size_t _mask;
        size_t       _index;
        size_t       _offset;

    public:
        constexpr quadratic_probe_sequence(const uint64_t hash, const size_t capacity) noexcept
            : _mask(capacity - 1), _index(0), _offset(hash & _mask) {}
        [[nodiscard]] size_t offset() const noexcept { return _offset; }
        void next() {
            ++_index;
            _offset += _index;
            _offset &= _mask;
        }
    };

    // Simple linear probing with wrap-around for power of two array sizes.
    // Unsurprisingly also visits each slot exactly once.
    // See https://en.wikipedia.org/wiki/Linear_probing
    class linear_probe_sequence {
        const size_t _mask;
        size_t       _offset;

    public:
        constexpr linear_probe_sequence(const uint64_t hash, const size_t capacity) noexcept
            : _mask(capacity - 1), _offset(hash & _mask) {}
        [[nodiscard]] size_t offset() const noexcept { return _offset; }
        void next() {
            ++_offset;
            _offset &= _mask;
        }
    };

    using probe_seq_type = quadratic_probe_sequence;

    // Precondition: elem != 0
    [[nodiscard]] bool contains(const uint32_t elem) const noexcept {
        probe_seq_type p(hash64(elem), _capacity);
        while (true) {
            const size_t slot = p.offset();
            if (_buf[slot] == elem) {
                return true;
            } else if (_buf[slot] == 0) {
                return false;
            }
            p.next();
        }
    }

    // Precondition: elem != 0
    [[nodiscard]] bool insert(const uint32_t elem) noexcept {
        probe_seq_type p(hash64(elem), _capacity);
        while (true) {
            const size_t slot = p.offset();
            if (_buf[slot] == 0) {
                _buf[slot] = elem;
                ++_size; // This may violate max load factor
                if (should_grow()) [[unlikely]] {
                    grow_and_rehash();
                }
                return true;
            } else if (_buf[slot] == elem) {
                return false;
            }
            p.next();
        }
    }

    class const_iterator {
    public:
        using difference_type = std::ptrdiff_t;
        using value_type = const uint32_t;
        using reference = const uint32_t&;
        using pointer = const uint32_t*;
        using iterator_category = std::forward_iterator_tag;

    private:
        const UnorderedU32Set* _set;
        size_t                 _idx; // Not u32 since set capacities may exceed UINT32_MAX
    public:
        const_iterator(const UnorderedU32Set& set, size_t idx, PrivateCtorTag) noexcept : _set(&set), _idx(idx) {
            skip_to_next_set_element();
        }
        const_iterator& operator++() noexcept {
            ++_idx;
            skip_to_next_set_element();
            return *this;
        }
        const_iterator operator++(int) noexcept {
            const_iterator prev = *this;
            ++(*this);
            return prev;
        }
        [[nodiscard]] constexpr const uint32_t& operator*() const noexcept { return _set->_buf[_idx]; }
        [[nodiscard]] constexpr const uint32_t* operator->() const noexcept { return &_set->_buf[_idx]; }
        // Note: iterator equality comparisons do not consider parent container
        // identity, as crossing the streams is considered undefined behavior
        // (and/or just plain rude) in general.
        constexpr bool operator==(const const_iterator& rhs) const noexcept { return _idx == rhs._idx; }
        constexpr bool operator!=(const const_iterator& rhs) const noexcept { return _idx != rhs._idx; }

    private:
        void skip_to_next_set_element() noexcept {
            while ((_idx < _set->capacity()) && (_set->_buf[_idx] == 0)) {
                ++_idx;
            }
        }
    };

    [[nodiscard]] const_iterator begin() const noexcept { return {*this, 0, PrivateCtorTag{}}; }
    [[nodiscard]] const_iterator end() const noexcept { return {*this, _capacity, PrivateCtorTag{}}; }

private:
    // We need > 32 bits of hash output in the worst case in case our set is at max
    // capacity and our actual element array has a size that is > UINT32_MAX.
    [[nodiscard]] static uint64_t hash64(uint32_t h) noexcept {
        // CRCs are independent and can run in parallel, minimizing the data dependency chain.
        // Magic values are just 32 MSBs from the Murmurhash3 `fmix64` multiplication
        // constants and are distinct in order to decorrelate the high and low outputs.
        // Note that the two outputs must never be XORed together, as the XOR is a fixed
        // value that will collide every single time...! Ask me how I found out.
        // Perf note: XXH3 is measurably slower than CRC32 here.
        return (static_cast<uint64_t>(VESPA_NATIVE_CRC32_U32(0xff51afd7, h)) << 32) |
               VESPA_NATIVE_CRC32_U32(0xc4ceb9fe, h);
    }

    [[nodiscard]] constexpr static size_t max_load_factor_adjusted(const size_t capacity) noexcept {
        return (capacity / 4 * 3); // max 3/4 load factor
    }

    [[nodiscard]] bool should_grow() const noexcept { return _size > max_load_factor_adjusted(_capacity); }

    void grow_and_rehash() __attribute__((noinline));

    // Preconditions:
    //   - Inserting `elem` will not violate the max load factor of the buffer
    //     (this also implies the buffer is guaranteed to contain free slots).
    //   - `elem` does not previously exist in `buf`.
    static void insert_for_rehash(uint32_t* buf, const uint32_t elem, const size_t new_capacity) noexcept {
        probe_seq_type p(hash64(elem), new_capacity);
        while (buf[p.offset()] != 0) {
            p.next();
        }
        buf[p.offset()] = elem;
    }
};

} // namespace vespalib

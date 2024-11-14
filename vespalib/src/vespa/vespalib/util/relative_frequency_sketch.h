// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "alloc.h"
#include <vespa/vespalib/stllike/hash_fun.h>
#include <compare>

namespace vespalib {

/**
 * Adds an implementation of a probabilistic frequency sketch that allows for estimating the
 * relative frequency of of elements from a stream of events. That is, the sketch does not
 * capture the _absolute_ frequency of a given element over time.
 *
 * To reduce the requirement for the number of bits used for the sketch's underlying counters,
 * this sketch uses automatic decaying of counter values once the number of recorded samples
 * reaches a certain point (relative to the sketch's size). Decaying divides all counters by 2.
 *
 * The underlying data structure is a Count-Min sketch [0][1] with automatic decaying of
 * counters based on TinyLFU [2].
 *
 * This implementation has certain changes from a "textbook" CM sketch, inspired by the
 * approach used in [3]. In particular, instead of having `d` logical rows each with width `w`
 * that are accessed with hash-derived indexes (and thus likely triggering `d` cache misses
 * for large values of `w`) we subdivide into w/64 blocks each with fixed number d=4 rows of
 * 32 4-bit counters, i.e. each block is exactly 64 bytes. Counter updates or reads always
 * happen within the scope of a single block. We also ensure the block array is allocated with
 * at least a 64-byte alignment. This ensures that a given sketch access will touch exactly 1
 * cache line of the underlying sketch buffer (not counting cache lines occupied by the sketch
 * object itself, as we assume these are already present in the cache).
 * Similarly, comparing the frequency of two elements will always touch at most 2 cache lines.
 *
 * Unlike [3] we use byte-wise counter accesses and only using a single hash computation per
 * distinct sketch lookup instead of explicitly re-mixing hash bits. We also always divide the
 * decay counter by 2 instead of subtracting the number of odd counters found (TODO reconsider?).
 *
 * The Count-Min sketch (and its cousin, the Counting Bloom Filter) using `k` counters is
 * usually described as requiring k pairwise independent hash functions. This implementation
 * assumes this requirement is unnecessary assuming a hash function with good entropy; we
 * instead extract non-overlapping subsets of bits of a single hash value and use these as
 * indices into our data structure components.
 *
 * Important: this frequency sketch _requires_ a good hash function, i.e. high entropy.
 * Use `RelativeFrequencySketch` with HasGoodEntropyHash=false (default) if this is not the
 * case for the type being counted, as it implicitly mixes the hash bits using XXH3.
 *
 * Thread safety: as thread safe as a std::vector.
 *
 * References:
 *  [0]: The Count-Min Sketch and its Applications (2003)
 *  [1]: https://en.wikipedia.org/wiki/Count%E2%80%93min_sketch
 *  [2]: TinyLFU: A Highly Efficient Cache Admission Policy (2015)
 *  [3]: https://github.com/ben-manes/caffeine/blob/master/caffeine/
 *       src/main/java/com/github/benmanes/caffeine/cache/FrequencySketch.java
 */
class RawRelativeFrequencySketch {
    alloc::Alloc _buf;
    size_t       _estimated_sample_count;
    size_t       _window_size;
    uint32_t     _block_mask_bits;
public:
    explicit RawRelativeFrequencySketch(size_t count);
    ~RawRelativeFrequencySketch();

    void add_by_hash(uint64_t hash) noexcept;
    [[nodiscard]] uint8_t add_and_count_by_hash(uint64_t hash) noexcept;
    // Note: since this compares _hashes_ rather than elements this has strong ordering semantics.
    [[nodiscard]] std::strong_ordering estimate_relative_frequency_by_hash(uint64_t lhs_hash, uint64_t rhs_hash) const noexcept;

    // Gets the raw underlying counter value saturated in [0, 15] for a given hash.
    [[nodiscard]] uint8_t count_min_by_hash(uint64_t hash) const noexcept;

    [[nodiscard]] size_t window_size() const noexcept { return _window_size; }
private:
    void div_all_by_2() noexcept;

    template <bool ReturnMinCount>
    uint8_t add_by_hash_impl(uint64_t hash) noexcept;
};

template <typename H, typename T>
concept SketchHasher = requires(H h, T t) {
    // Hashers should never throw.
    { h(t) } noexcept;
    // We need a 64-bit hash output (not using uint64_t since STL is standardized
    // on returning size_t from hash functions).
    { h(t) } -> std::same_as<size_t>;
};

/**
 * Wrapper of RawRelativeFrequencySketch for an arbitrary hashable type.
 *
 * Only set HasGoodEntropyHash=true if you know that the underlying hash function is
 * of good quality. This _excludes_ std::hash<> hashes, especially those for integers,
 * as the hash function for those is more often than not the identity function.
 *
 * See `RawRelativeFrequencySketch` for algorithm details.
 */
template <typename T, SketchHasher<T> Hash = std::hash<T>, bool HasGoodEntropyHash = false>
class RelativeFrequencySketch {
    RawRelativeFrequencySketch _impl;
    [[no_unique_address]] Hash _hash;
public:
    // Initializes a sketch used for estimating frequencies for an underlying cache
    // (or similar data structure) that can hold a maximum of `count` entries.
    explicit RelativeFrequencySketch(size_t count, Hash hash = Hash{})
        : _impl(count),
          _hash(hash)
    {}
    ~RelativeFrequencySketch() = default;
private:
    [[nodiscard]] uint64_t hash_elem(const T& elem) const noexcept {
        uint64_t hash = _hash(elem);
        if constexpr (!HasGoodEntropyHash) {
            hash = xxhash::xxh3_64(hash); // Mix it up!
        }
        return hash;
    }
public:
    // Increments the estimated frequency for the given element, identified by its hash.
    // Frequency is saturated at 15.
    void add(const T& elem) noexcept {
        _impl.add_by_hash(hash_elem(elem));
    }
    // Same as `add` but returns Count-Min estimate from _after_ `elem` has been added.
    [[nodiscard]] uint8_t add_and_count(const T& elem) noexcept {
        return _impl.add_and_count_by_hash(hash_elem(elem));
    }
    // Returns a frequency estimate for the given element, saturated at 15. Since this is
    // a probabilistic sketch, the frequency may be overestimated. Note that automatic counter
    // decaying will over time reduce the reported frequency of elements that are no longer
    // added to the sketch.
    [[nodiscard]] uint8_t count_min(const T& elem) const noexcept {
        return _impl.count_min_by_hash(hash_elem(elem));
    }
    [[nodiscard]] std::weak_ordering estimate_relative_frequency(const T& lhs, const T& rhs) const noexcept {
        const uint64_t lhs_hash = hash_elem(lhs);
        const uint64_t rhs_hash = hash_elem(rhs);
        return _impl.estimate_relative_frequency_by_hash(lhs_hash, rhs_hash);
    }
    // Sample count required before all counters are automatically divided by 2.
    // Note that invoking `add(v)` for an element `v` whose counters are _all_ fully
    // saturated prior to the invocation will _not_ count towards the sample count.
    [[nodiscard]] size_t window_size() const noexcept { return _impl.window_size(); }
};

} // vespalib

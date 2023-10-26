// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <set>
#include <cstdint>
#include <cassert>

namespace search::predicate {

/**
 * Holds the data used in a cache lookup operation.
 */
struct CurrentZeroRef {
    const uint32_t *buf;
    uint32_t size;
    void set(const uint32_t *b, uint32_t s) {
        buf = b;
        size = s;
    }
};

/**
 * Comparator (less) used in std::set. It holds a reference to a data
 * store, from which it looks up buffers from the "data_ref"-part of the
 * cached references.
 */
template <typename BufferStore, int SIZE_BITS>
class RefCacheComparator {
    enum { DATA_REF_BITS = 32 - SIZE_BITS,
           DATA_REF_MASK = (1 << DATA_REF_BITS) - 1,
           MAX_SIZE = (1 << SIZE_BITS) - 1,
           SIZE_SHIFT = DATA_REF_BITS };
    const BufferStore &_store;
    const CurrentZeroRef &_current_zero_ref;
public:
    RefCacheComparator(const BufferStore &store,
                       const CurrentZeroRef &zero_ref)
        : _store(store),
          _current_zero_ref(zero_ref){
    }

    void getSizeAndBuf(uint32_t ref, uint32_t &size,
                       const uint32_t *&buf) const {
        if (ref) {
            size = ref >> SIZE_SHIFT;
            buf = _store.getBuffer(ref & DATA_REF_MASK);
            if (size == MAX_SIZE) {
                size = *buf++;
            }
        } else {
            size = _current_zero_ref.size;
            buf = _current_zero_ref.buf;
        }
    }

    bool compareWithZeroRef(uint32_t lhs, uint32_t rhs) const {
        uint32_t lhs_size;
        const uint32_t *lhs_buf;
        getSizeAndBuf(lhs, lhs_size, lhs_buf);
        uint32_t rhs_size;
        const uint32_t *rhs_buf;
        getSizeAndBuf(rhs, rhs_size, rhs_buf);

        if (lhs_size != rhs_size) {
            return lhs_size < rhs_size;
        }
        for (uint32_t i = 0; i < lhs_size; ++i) {
            if (lhs_buf[i] != rhs_buf[i]) {
                return lhs_buf[i] < rhs_buf[i];
            }
        }
        return false;
    }

    bool operator() (uint32_t lhs, uint32_t rhs) const {
        if (!lhs || !rhs) {
            return compareWithZeroRef(lhs, rhs);
        }
        uint32_t lhs_size = lhs >> SIZE_SHIFT;
        uint32_t rhs_size = rhs >> SIZE_SHIFT;
        if (lhs_size != rhs_size) {
            return lhs_size < rhs_size;
        }
        if (lhs == rhs) {
            return false;
        }
        const uint32_t *lhs_buf = _store.getBuffer(lhs & DATA_REF_MASK);
        const uint32_t *rhs_buf = _store.getBuffer(rhs & DATA_REF_MASK);
        uint32_t size = lhs_size;
        if (lhs_size == MAX_SIZE) {
            size = lhs_buf[0] + 1;  // Compare sizes and data in loop
                                    // below. If actual size differs
                                    // then loop will exit in first
                                    // iteration.
        }
        for (uint32_t i = 0; i < size; ++i) {
            if (lhs_buf[i] != rhs_buf[i]) {
                return lhs_buf[i] < rhs_buf[i];
            }
        }
        return false;
    }
};

/**
 * Holds a set of refs and a reference to a datastore that is used to
 * lookup data based on the "data_ref"-part of the ref. Each ref also
 * uses the upper bits to hold the size of the data refered to. If the
 * size is too large to represent by the allocated bits, the max size
 * is used, and the actual size is stored in the first 32-bit value of
 * the data buffer.
 *
 * Note that this class is inherently single threaded, and thus needs
 * external synchronization if used from multiple threads. (Both
 * insert and find)
 */
template <typename BufferStore, int SIZE_BITS = 8>
class PredicateRefCache {
    using ComparatorType = RefCacheComparator<BufferStore, SIZE_BITS>;

    mutable CurrentZeroRef _current_zero_ref;
    std::set<uint32_t, ComparatorType> _ref_cache;

public:
    enum { DATA_REF_BITS = 32 - SIZE_BITS,
           DATA_REF_MASK = (1 << DATA_REF_BITS) - 1,
           MAX_SIZE = (1 << SIZE_BITS) - 1,
           SIZE_SHIFT = DATA_REF_BITS,
           SIZE_MASK = MAX_SIZE << SIZE_SHIFT};

    PredicateRefCache(const BufferStore &store)
        : _ref_cache(ComparatorType(store, _current_zero_ref)) {
    }

    /**
     * Inserts a ref into the cache. The ref refers to data already
     * inserted in the underlying data store.
     */
    uint32_t insert(uint32_t ref) {
        assert(ref);
        return *_ref_cache.insert(ref).first;
    }

    /**
     * Checks if a data sequence is already present in the
     * cache. Returns the datastore ref, or 0 if not present.
     */
    uint32_t find(const uint32_t *buf, uint32_t size) const {
        _current_zero_ref.set(buf, size);
        auto it = _ref_cache.find(0);
        if (it != _ref_cache.end()) {
            return *it;
        }
        return 0;
    }
};

}

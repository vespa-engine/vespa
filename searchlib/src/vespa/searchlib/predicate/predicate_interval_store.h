// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_ref_cache.h"
#include <vespa/vespalib/datastore/bufferstate.h>
#include <vespa/vespalib/datastore/datastore.h>
#include <vector>

namespace search::predicate {
struct Interval;

/**
 * Stores interval entries in a memory-efficient way.
 * It works with both Interval and IntervalWithBounds entries.
 */
class PredicateIntervalStore {
    class DataStoreAdapter;
    using RefCacheType = PredicateRefCache<DataStoreAdapter, 8>;
    using DataStoreType = vespalib::datastore::DataStoreT<vespalib::datastore::EntryRefT<18, 6>>;
    using RefType =  DataStoreType::RefType;
    using generation_t = vespalib::GenerationHandler::generation_t;

    DataStoreType _store;
    vespalib::datastore::BufferType<uint32_t> _size1Type;

    class DataStoreAdapter {
        const DataStoreType &_store;
    public:
        DataStoreAdapter(const DataStoreType &store) : _store(store) {}
        const uint32_t *getBuffer(uint32_t ref) const {
            RefType entry_ref = vespalib::datastore::EntryRef(ref);
            return _store.getEntry<uint32_t>(entry_ref);
        }
    };
    DataStoreAdapter _store_adapter;
    RefCacheType     _ref_cache;

    // Return type for private allocation functions
    template <typename T>
    struct Entry {
        RefType ref;
        T *buffer;
    };

    // Allocates a new entry in a datastore buffer.
    template <typename T>
    Entry<T> allocNewEntry(uint32_t type_id, uint32_t size);
    // Returns the size of an interval entry in number of uint32_t.
    template <typename IntervalT>
    static uint32_t entrySize() { return sizeof(IntervalT) / sizeof(uint32_t); }

public:
    PredicateIntervalStore();
    ~PredicateIntervalStore();

    /**
     * Inserts an array of intervals into the store.
     * IntervalT is either Interval or IntervalWithBounds.
     */
    template <typename IntervalT>
    vespalib::datastore::EntryRef insert(const std::vector<IntervalT> &intervals);

    /**
     * Removes an entry. The entry remains accessible until commit
     * is called, and also as long as readers hold the current
     * generation.
     *
     * Remove is currently disabled, as the ref cache is assumed to
     * keep the total number of different entries low.
     */
    void remove(vespalib::datastore::EntryRef ref);

    void reclaim_memory(generation_t oldest_used_gen);

    void assign_generation(generation_t current_gen);

    /**
     * Return memory usage (only the data store is included)
     */
    vespalib::MemoryUsage getMemoryUsage() const {
        return _store.getMemoryUsage();
    }

    /**
     * Retrieves a list of intervals.
     * IntervalT is either Interval or IntervalWithBounds.
     * single_buf is a pointer to a single IntervalT, used by the
     * single interval optimization.
     */
    template <typename IntervalT>
    const IntervalT
    *get(vespalib::datastore::EntryRef btree_ref, uint32_t &size_out, IntervalT *single_buf) const
    {
        uint32_t size = btree_ref.ref() >> RefCacheType::SIZE_SHIFT;
        RefType data_ref(vespalib::datastore::EntryRef(btree_ref.ref() & RefCacheType::DATA_REF_MASK));
        if (__builtin_expect(size == 0, true)) {  // single-interval optimization
            *single_buf = IntervalT();
            single_buf->interval = data_ref.ref();
            size_out = 1;
            return single_buf;
        }
        const uint32_t *buf = _store.getEntry<uint32_t>(data_ref);
        if (size == RefCacheType::MAX_SIZE) {
            size = *buf++;
        }
        size_out = size / entrySize<IntervalT>();
        return reinterpret_cast<const IntervalT *>(buf);
    }
};

}

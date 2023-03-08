// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/util/address_space.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <functional>

namespace vespalib::datastore {
class CompactionStrategy;
}

namespace search::attribute {

/**
 * Base class for mapping from from document id to an array of values.
 */
class MultiValueMappingBase
{
public:
    using CompactionStrategy = vespalib::datastore::CompactionStrategy;
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using EntryRef = vespalib::datastore::EntryRef;
    using RefVector = vespalib::RcuVectorBase<AtomicEntryRef>;

protected:
    std::shared_ptr<vespalib::alloc::MemoryAllocator> _memory_allocator;
    RefVector _indices;
    size_t    _totalValues;

    MultiValueMappingBase(const vespalib::GrowStrategy &gs, vespalib::GenerationHolder &genHolder, std::shared_ptr<vespalib::alloc::MemoryAllocator> memory_allocator);
    virtual ~MultiValueMappingBase();

    void updateValueCount(size_t oldValues, size_t newValues) {
        _totalValues += newValues - oldValues;
    }

    EntryRef acquire_entry_ref(uint32_t docId) const noexcept { return _indices.acquire_elem_ref(docId).load_acquire(); }
public:
    virtual vespalib::MemoryUsage getArrayStoreMemoryUsage() const = 0;
    virtual vespalib::AddressSpace getAddressSpaceUsage() const = 0;
    vespalib::MemoryUsage getMemoryUsage() const;
    size_t getTotalValueCnt() const { return _totalValues; }

    // Called when making snapshot of indices in saver or unit test.
    const RefVector& get_ref_vector() const noexcept { return _indices; }

    /*
     * isFull() should be called from writer only.
     * Const type qualifier removed to prevent call from reader.
     */
    bool isFull() { return _indices.isFull(); }
    void addDoc(uint32_t &docId);
    void shrink(uint32_t docidLimit);
    void reserve(uint32_t lidLimit);
    void clearDocs(uint32_t lidLow, uint32_t lidLimit, std::function<void(uint32_t)> clearDoc);
    /*
     * size() should be called from writer only.
     * Const type qualifier removed to prevent call from reader.
     */
    uint32_t size() { return _indices.size(); }

    /*
     * getNumKeys() should be called from writer only.
     * Const type qualifier removed to prevent call from reader.
     */
    uint32_t getNumKeys() { return _indices.size(); }
    /*
     * getCapacityKeys() should be called from writer only.
     * Const type qualifier removed to prevent call from reader.
     */
    uint32_t getCapacityKeys() { return _indices.capacity(); }
};

}

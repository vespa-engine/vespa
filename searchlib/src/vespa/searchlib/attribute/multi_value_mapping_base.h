// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/address_space.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <functional>

namespace search { class CompactionStrategy; }

namespace search::attribute {

/**
 * Base class for mapping from from document id to an array of values.
 */
class MultiValueMappingBase
{
public:
    using EntryRef = vespalib::datastore::EntryRef;
    using RefVector = vespalib::RcuVectorBase<EntryRef>;

protected:
    RefVector _indices;
    size_t    _totalValues;
    vespalib::MemoryUsage _cachedArrayStoreMemoryUsage;
    vespalib::AddressSpace _cachedArrayStoreAddressSpaceUsage;

    MultiValueMappingBase(const vespalib::GrowStrategy &gs, vespalib::GenerationHolder &genHolder);
    virtual ~MultiValueMappingBase();

    void updateValueCount(size_t oldValues, size_t newValues) {
        _totalValues += newValues - oldValues;
    }
public:
    using RefCopyVector = vespalib::Array<EntryRef>;

    virtual vespalib::MemoryUsage getArrayStoreMemoryUsage() const = 0;
    virtual vespalib::AddressSpace getAddressSpaceUsage() const = 0;
    vespalib::MemoryUsage getMemoryUsage() const;
    vespalib::MemoryUsage updateStat();
    size_t getTotalValueCnt() const { return _totalValues; }
    RefCopyVector getRefCopy(uint32_t size) const;

    bool isFull() const { return _indices.isFull(); }
    void addDoc(uint32_t &docId);
    void shrink(uint32_t docidLimit);
    void reserve(uint32_t lidLimit);
    void clearDocs(uint32_t lidLow, uint32_t lidLimit, std::function<void(uint32_t)> clearDoc);
    uint32_t size() const { return _indices.size(); }

    uint32_t getNumKeys() const { return _indices.size(); }
    uint32_t getCapacityKeys() const { return _indices.capacity(); }
    virtual void compactWorst(bool compatMemory, bool compactAddressSpace) = 0;
    bool considerCompact(const CompactionStrategy &compactionStrategy);
};

}

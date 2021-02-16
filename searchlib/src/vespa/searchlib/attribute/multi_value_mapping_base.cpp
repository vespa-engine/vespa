// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_value_mapping_base.h"
#include <vespa/searchcommon/common/compaction_strategy.h>
#include <cassert>

namespace search::attribute {

namespace {

// minimum dead bytes in multi value mapping before consider compaction
constexpr size_t DEAD_BYTES_SLACK = 0x10000u;
constexpr size_t DEAD_ARRAYS_SLACK = 0x10000u;

}

MultiValueMappingBase::MultiValueMappingBase(const vespalib::GrowStrategy &gs,
                                             vespalib::GenerationHolder &genHolder)
    : _indices(gs, genHolder),
      _totalValues(0u),
      _cachedArrayStoreMemoryUsage(),
      _cachedArrayStoreAddressSpaceUsage(0, 0, (1ull << 32))
{
}

MultiValueMappingBase::~MultiValueMappingBase() = default;

MultiValueMappingBase::RefCopyVector
MultiValueMappingBase::getRefCopy(uint32_t size) const {
    assert(size <= _indices.size());
    return RefCopyVector(&_indices[0], &_indices[0] + size);
}

void
MultiValueMappingBase::addDoc(uint32_t & docId)
{
    uint32_t retval = _indices.size();
    _indices.push_back(EntryRef());
    docId = retval;
}

void
MultiValueMappingBase::reserve(uint32_t lidLimit)
{
    _indices.reserve(lidLimit);
}

void
MultiValueMappingBase::shrink(uint32_t docIdLimit)
{
    assert(docIdLimit < _indices.size());
    _indices.shrink(docIdLimit);
}

void
MultiValueMappingBase::clearDocs(uint32_t lidLow, uint32_t lidLimit, std::function<void(uint32_t)> clearDoc)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= _indices.size());
    for (uint32_t lid = lidLow; lid < lidLimit; ++lid) {
        if (_indices[lid].valid()) {
            clearDoc(lid);
        }
    }
}

vespalib::MemoryUsage
MultiValueMappingBase::getMemoryUsage() const
{
    vespalib::MemoryUsage retval = getArrayStoreMemoryUsage();
    retval.merge(_indices.getMemoryUsage());
    return retval;
}

vespalib::MemoryUsage
MultiValueMappingBase::updateStat()
{
    _cachedArrayStoreAddressSpaceUsage = getAddressSpaceUsage();
    vespalib::MemoryUsage retval = getArrayStoreMemoryUsage();
    _cachedArrayStoreMemoryUsage = retval;
    retval.merge(_indices.getMemoryUsage());
    return retval;
}

bool
MultiValueMappingBase::considerCompact(const CompactionStrategy &compactionStrategy)
{
    size_t usedBytes = _cachedArrayStoreMemoryUsage.usedBytes();
    size_t deadBytes = _cachedArrayStoreMemoryUsage.deadBytes();
    size_t usedArrays = _cachedArrayStoreAddressSpaceUsage.used();
    size_t deadArrays = _cachedArrayStoreAddressSpaceUsage.dead();
    bool compactMemory = ((deadBytes >= DEAD_BYTES_SLACK) &&
                          (usedBytes * compactionStrategy.getMaxDeadBytesRatio() < deadBytes));
    bool compactAddressSpace = ((deadArrays >= DEAD_ARRAYS_SLACK) &&
                                (usedArrays * compactionStrategy.getMaxDeadAddressSpaceRatio() < deadArrays));
    if (compactMemory || compactAddressSpace) {
        compactWorst(compactMemory, compactAddressSpace);
        return true;
    }
    return false;
}

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_value_mapping_base.h"
#include <vespa/vespalib/datastore/compaction_spec.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <cassert>

namespace search::attribute {

using vespalib::datastore::CompactionStrategy;

MultiValueMappingBase::MultiValueMappingBase(const vespalib::GrowStrategy &gs,
                                             vespalib::GenerationHolder &genHolder)
    : _indices(gs, genHolder),
      _totalValues(0u),
      _compaction_spec()
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
MultiValueMappingBase::updateStat(const CompactionStrategy& compaction_strategy)
{
    auto array_store_address_space_usage = getAddressSpaceUsage();
    auto array_store_memory_usage = getArrayStoreMemoryUsage();
    _compaction_spec = compaction_strategy.should_compact(array_store_memory_usage, array_store_address_space_usage);
    auto retval = array_store_memory_usage;
    retval.merge(_indices.getMemoryUsage());
    return retval;
}

bool
MultiValueMappingBase::considerCompact(const CompactionStrategy &compactionStrategy)
{
     if (_compaction_spec.compact()) {
        compactWorst(_compaction_spec, compactionStrategy);
        return true;
    }
    return false;
}

}

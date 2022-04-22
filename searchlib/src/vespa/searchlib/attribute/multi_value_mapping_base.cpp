// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_value_mapping_base.h"
#include <vespa/vespalib/datastore/compaction_spec.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/util/array.hpp>
#include <cassert>

namespace search::attribute {

using vespalib::datastore::CompactionStrategy;

MultiValueMappingBase::MultiValueMappingBase(const vespalib::GrowStrategy &gs,
                                             vespalib::GenerationHolder &genHolder,
                                             std::shared_ptr<vespalib::alloc::MemoryAllocator> memory_allocator)
    : _memory_allocator(std::move(memory_allocator)),
      _indices(gs, genHolder, _memory_allocator ? vespalib::alloc::Alloc::alloc_with_allocator(_memory_allocator.get()) : vespalib::alloc::Alloc::alloc()),
      _totalValues(0u),
      _compaction_spec()
{
}

MultiValueMappingBase::~MultiValueMappingBase() = default;

MultiValueMappingBase::RefCopyVector
MultiValueMappingBase::getRefCopy(uint32_t size) const {
    assert(size <= _indices.get_size());       // Called from writer only
    auto* indices = &_indices.get_elem_ref(0); // Called from writer only
    RefCopyVector result;
    result.reserve(size);
    for (uint32_t lid = 0; lid < size; ++lid) {
        result.push_back(indices[lid].load_relaxed());
    }
    return result;
}

void
MultiValueMappingBase::addDoc(uint32_t & docId)
{
    uint32_t retval = _indices.size();
    _indices.push_back(AtomicEntryRef());
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
        if (_indices[lid].load_relaxed().valid()) {
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
    if (!has_held_buffers() && _compaction_spec.compact()) {
        compactWorst(_compaction_spec, compactionStrategy);
        return true;
    }
    return false;
}

}

namespace vespalib {

template class Array<datastore::AtomicEntryRef>;

}

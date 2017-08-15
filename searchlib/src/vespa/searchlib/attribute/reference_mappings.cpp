// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reference_mappings.h"
#include "reference.h"
#include <vespa/searchlib/datastore/datastore.hpp>
#include <vespa/searchlib/btree/btreestore.hpp>

namespace search::attribute {

ReferenceMappings::ReferenceMappings(GenerationHolder &genHolder)
    : _reverseMappingIndices(genHolder),
      _reverseMapping()
{
}

ReferenceMappings::~ReferenceMappings()
{
}

void
ReferenceMappings::clearMapping(const Reference &entry)
{
    EntryRef revMapIdx = entry.revMapIdx();
    if (revMapIdx.valid()) {
        _reverseMapping.clear(revMapIdx);
    }
}

void
ReferenceMappings::syncReverseMappingIndices(const Reference &entry)
{
    uint32_t referencedLid = entry.lid();
    if (referencedLid != 0u) {
        _reverseMappingIndices.ensure_size(referencedLid + 1);
        _reverseMappingIndices[referencedLid] = entry.revMapIdx();
    }
}

void
ReferenceMappings::removeReverseMapping(const Reference &entry, uint32_t lid)
{
    EntryRef revMapIdx = entry.revMapIdx();
    _reverseMapping.apply(revMapIdx, nullptr, nullptr, &lid, &lid + 1);
    std::atomic_thread_fence(std::memory_order_release);
    entry.setRevMapIdx(revMapIdx);
    syncReverseMappingIndices(entry);
}

void
ReferenceMappings::addReverseMapping(const Reference &entry, uint32_t lid)
{
    EntryRef revMapIdx = entry.revMapIdx();
    ReverseMapping::KeyDataType add(lid, btree::BTreeNoLeafData());
    _reverseMapping.apply(revMapIdx, &add, &add + 1, nullptr, nullptr);
    std::atomic_thread_fence(std::memory_order_release);
    entry.setRevMapIdx(revMapIdx);
    syncReverseMappingIndices(entry);
}

void
ReferenceMappings::buildReverseMapping(const Reference &entry, const std::vector<ReverseMapping::KeyDataType> &adds)
{
    EntryRef revMapIdx = entry.revMapIdx();
    assert(!revMapIdx.valid());
    _reverseMapping.apply(revMapIdx, &adds[0], &adds[adds.size()], nullptr, nullptr);
    entry.setRevMapIdx(revMapIdx);
}

void
ReferenceMappings::notifyGidToLidChange(const Reference &entry, uint32_t referencedLid)
{
    uint32_t oldReferencedLid = entry.lid();
    if (oldReferencedLid != referencedLid) {
        if (oldReferencedLid != 0u && oldReferencedLid < _reverseMappingIndices.size()) {
            _reverseMappingIndices[oldReferencedLid] = EntryRef();
        }
        entry.setLid(referencedLid);
    }
    syncReverseMappingIndices(entry);
}

}

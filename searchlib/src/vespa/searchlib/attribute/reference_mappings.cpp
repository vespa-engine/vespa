// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reference_mappings.h"
#include "reference.h"
#include <vespa/searchlib/datastore/datastore.hpp>
#include <vespa/searchlib/btree/btreestore.hpp>

namespace search::attribute {

ReferenceMappings::ReferenceMappings(GenerationHolder &genHolder, const uint32_t &committedDocIdLimit)
    : _reverseMappingIndices(genHolder),
      _referencedLidLimit(0),
      _reverseMapping(),
      _referencedLids(genHolder),
      _committedDocIdLimit(committedDocIdLimit)
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
ReferenceMappings::syncForwardMapping(const Reference &entry)
{
    uint32_t referencedLid = entry.lid();
    EntryRef revMapIdx = entry.revMapIdx();
    auto &referencedLids = _referencedLids;
    _reverseMapping.foreach_unfrozen_key(revMapIdx,
                                         [&referencedLids, referencedLid](uint32_t lid)
                                         { referencedLids[lid] = referencedLid; });
}

void
ReferenceMappings::syncReverseMappingIndices(const Reference &entry)
{
    uint32_t referencedLid = entry.lid();
    if (referencedLid != 0u) {
        _reverseMappingIndices.ensure_size(referencedLid + 1);
        _reverseMappingIndices[referencedLid] = entry.revMapIdx();
        if (referencedLid >= _referencedLidLimit) {
            std::atomic_thread_fence(std::memory_order_release);
            _referencedLidLimit = referencedLid + 1;
        }
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
    _referencedLids[lid] = 0; // forward mapping
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
    _referencedLids[lid] = entry.lid(); // forward mapping
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
ReferenceMappings::notifyReferencedPut(const Reference &entry, uint32_t referencedLid)
{
    uint32_t oldReferencedLid = entry.lid();
    if (oldReferencedLid != referencedLid) {
        if (oldReferencedLid != 0u && oldReferencedLid < _reverseMappingIndices.size()) {
            _reverseMappingIndices[oldReferencedLid] = EntryRef();
        }
        entry.setLid(referencedLid);
    }
    syncReverseMappingIndices(entry);
    syncForwardMapping(entry);
}

void
ReferenceMappings::notifyReferencedRemove(const Reference &entry)
{
    uint32_t oldReferencedLid = entry.lid();
    if (oldReferencedLid != 0) {
        if (oldReferencedLid < _reverseMappingIndices.size()) {
            _reverseMappingIndices[oldReferencedLid] = EntryRef();
        }
        entry.setLid(0);
    }
    syncReverseMappingIndices(entry);
    syncForwardMapping(entry);
}

void
ReferenceMappings::onAddDocs(uint32_t docIdLimit)
{
    _referencedLids.reserve(docIdLimit);
}

void
ReferenceMappings::addDoc()
{
    _referencedLids.push_back(0);
}

void
ReferenceMappings::onLoad(uint32_t docIdLimit)
{
    _referencedLids.clear();
    _referencedLids.unsafe_reserve(docIdLimit);
    _referencedLids.ensure_size(docIdLimit);
}

void
ReferenceMappings::shrink(uint32_t docIdLimit)
{
    _referencedLids.shrink(docIdLimit);
}

MemoryUsage
ReferenceMappings::getMemoryUsage()
{
    MemoryUsage usage = _reverseMapping.getMemoryUsage();
    usage.merge(_reverseMappingIndices.getMemoryUsage());
    usage.merge(_referencedLids.getMemoryUsage());
    return usage;
}

}

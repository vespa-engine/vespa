// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reference_mappings.h"
#include "reference.h"
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/util/rcuvector.hpp>

namespace search::attribute {

ReferenceMappings::ReferenceMappings(GenerationHolder& genHolder,
                                     const std::atomic<uint32_t>& committedDocIdLimit,
                                     const vespalib::alloc::Alloc& initial_alloc)
    : _reverseMappingIndices(vespalib::GrowStrategy(16, 1.0, 0, 0), genHolder, initial_alloc),
      _targetLidLimit(0),
      _reverseMapping(),
      _targetLids(vespalib::GrowStrategy(16, 1.0, 0, 0), genHolder, initial_alloc),
      _committedDocIdLimit(committedDocIdLimit)
{
}

ReferenceMappings::~ReferenceMappings() = default;

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
    uint32_t targetLid = entry.lid();
    EntryRef revMapIdx = entry.revMapIdx();
    auto &targetLids = _targetLids;
    _reverseMapping.foreach_unfrozen_key(revMapIdx,
                                         [&targetLids, targetLid](uint32_t lid)
                                         { targetLids[lid].store_release(targetLid); });
}

void
ReferenceMappings::syncReverseMappingIndices(const Reference &entry)
{
    uint32_t targetLid = entry.lid();
    if (targetLid != 0u) {
        _reverseMappingIndices.ensure_size(targetLid + 1);
        _reverseMappingIndices[targetLid].store_release(entry.revMapIdx());
        if (targetLid >= _targetLidLimit.load(std::memory_order_relaxed)) {
            _targetLidLimit.store(targetLid + 1, std::memory_order_release);
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
    _targetLids[lid].store_release(0); // forward mapping
}

void
ReferenceMappings::addReverseMapping(const Reference &entry, uint32_t lid)
{
    EntryRef revMapIdx = entry.revMapIdx();
    ReverseMapping::KeyDataType add(lid, vespalib::btree::BTreeNoLeafData());
    _reverseMapping.apply(revMapIdx, &add, &add + 1, nullptr, nullptr);
    std::atomic_thread_fence(std::memory_order_release);
    entry.setRevMapIdx(revMapIdx);
    syncReverseMappingIndices(entry);
    _targetLids[lid].store_release(entry.lid()); // forward mapping
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
ReferenceMappings::notifyReferencedPut(const Reference &entry, uint32_t targetLid)
{
    uint32_t oldTargetLid = entry.lid();
    if (oldTargetLid != targetLid) {
        if (oldTargetLid != 0u && oldTargetLid < _reverseMappingIndices.size()) {
            _reverseMappingIndices[oldTargetLid].store_release(EntryRef());
        }
        entry.setLid(targetLid);
    }
    syncReverseMappingIndices(entry);
    syncForwardMapping(entry);
}

void
ReferenceMappings::notifyReferencedRemove(const Reference &entry)
{
    uint32_t oldTargetLid = entry.lid();
    if (oldTargetLid != 0) {
        if (oldTargetLid < _reverseMappingIndices.size()) {
            _reverseMappingIndices[oldTargetLid].store_release(EntryRef());
        }
        entry.setLid(0);
    }
    syncReverseMappingIndices(entry);
    syncForwardMapping(entry);
}

void
ReferenceMappings::onAddDocs(uint32_t docIdLimit)
{
    _targetLids.reserve(docIdLimit);
}

void
ReferenceMappings::addDoc()
{
    _targetLids.push_back(AtomicTargetLid(0));
}

void
ReferenceMappings::onLoad(uint32_t docIdLimit)
{
    _targetLids.clear();
    _targetLids.unsafe_reserve(docIdLimit);
    _targetLids.ensure_size(docIdLimit);
}

void
ReferenceMappings::shrink(uint32_t docIdLimit)
{
    _targetLids.shrink(docIdLimit);
}

vespalib::MemoryUsage
ReferenceMappings::getMemoryUsage()
{
    vespalib::MemoryUsage usage = _reverseMapping.getMemoryUsage();
    usage.merge(_reverseMappingIndices.getMemoryUsage());
    usage.merge(_targetLids.getMemoryUsage());
    return usage;
}

}

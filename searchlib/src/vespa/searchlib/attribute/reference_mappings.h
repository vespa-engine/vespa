// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/btree/btreestore.h>
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <atomic>

namespace search::attribute {

class Reference;

/*
 * Class representing mappings in a reference attribute.
 */
class ReferenceMappings
{
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using AtomicTargetLid = vespalib::datastore::AtomicValueWrapper<uint32_t>;
    using GenerationHolder = vespalib::GenerationHolder;
    using EntryRef = vespalib::datastore::EntryRef;
    // Classes used to map from target lid to source lids
    using ReverseMappingIndices = vespalib::RcuVectorBase<AtomicEntryRef>;
    using ReverseMapping = vespalib::btree::BTreeStore<uint32_t, vespalib::btree::BTreeNoLeafData,
                                             vespalib::btree::NoAggregated,
                                             std::less<uint32_t>,
                                             vespalib::btree::BTreeDefaultTraits,
                                             vespalib::btree::NoAggrCalc>;
    using generation_t = vespalib::GenerationHandler::generation_t;

    // Vector containing references to trees of lids referencing given
    // target lid.
    ReverseMappingIndices _reverseMappingIndices;
    // limit for target lid when accessing _reverseMappingIndices
    std::atomic<uint32_t> _targetLidLimit;
    // Store of B-Trees, used to map from gid or target lid to
    // source lids.
    ReverseMapping _reverseMapping;
    // vector containing target lid given source lid
    vespalib::RcuVectorBase<AtomicTargetLid> _targetLids;
    const std::atomic<uint32_t>& _committedDocIdLimit;

    void syncForwardMapping(const Reference &entry);
    void syncReverseMappingIndices(const Reference &entry);

public:
    using TargetLids = vespalib::ConstArrayRef<AtomicTargetLid>;
    // Class used to map from target lid to source lids
    using ReverseMappingRefs = vespalib::ConstArrayRef<AtomicEntryRef>;

    ReferenceMappings(GenerationHolder &genHolder, const std::atomic<uint32_t>& committedDocIdLimit,
                      const vespalib::alloc::Alloc& initial_alloc);

    ~ReferenceMappings();

    // Cleanup helpers, to free resources
    void clearBuilder() { _reverseMapping.clearBuilder(); }
    void clearMapping(const Reference &entry);

    // Hold list management & freezing
    void reclaim_memory(generation_t oldest_used_gen) { _reverseMapping.reclaim_memory(oldest_used_gen); }
    void freeze() { _reverseMapping.freeze(); }
    void assign_generation(generation_t current_gen) { _reverseMapping.assign_generation(current_gen); }

    // Handle mapping changes
    void notifyReferencedPut(const Reference &entry, uint32_t targetLid);
    void notifyReferencedRemove(const Reference &entry);
    void removeReverseMapping(const Reference &entry, uint32_t lid);
    void addReverseMapping(const Reference &entry, uint32_t lid);

    // Maintain size of mapping from lid to target lid
    void onAddDocs(uint32_t docIdLimit);
    void addDoc();
    void onLoad(uint32_t docIdLimit);
    void shrink(uint32_t docIdLimit);

    // Setup mapping after load
    void buildReverseMapping(const Reference &entry, const std::vector<ReverseMapping::KeyDataType> &adds);

    vespalib::MemoryUsage getMemoryUsage();

    // Reader API, reader must hold generation guard
    template <typename FunctionType>
    void
    foreach_lid(uint32_t targetLid, FunctionType &&func) const;

    TargetLids getTargetLids() const {
        uint32_t committedDocIdLimit = _committedDocIdLimit.load(std::memory_order_acquire);
        return TargetLids(&_targetLids.acquire_elem_ref(0), committedDocIdLimit);
    }
    uint32_t getTargetLid(uint32_t doc) const {
        // Check limit to avoid reading memory beyond end of valid mapping array
        uint32_t committed_doc_id_limit = _committedDocIdLimit.load(std::memory_order_acquire);
        return doc < committed_doc_id_limit ? _targetLids.acquire_elem_ref(doc).load_acquire() : 0u;
    }
    ReverseMappingRefs getReverseMappingRefs() const {
        uint32_t targetLidLimit = _targetLidLimit.load(std::memory_order_acquire);
        return ReverseMappingRefs(&_reverseMappingIndices.acquire_elem_ref(0), targetLidLimit);
    }
    const ReverseMapping &getReverseMapping() const { return _reverseMapping; }
};

template <typename FunctionType>
void
ReferenceMappings::foreach_lid(uint32_t targetLid, FunctionType &&func) const
{
    if (targetLid < _targetLidLimit.load(std::memory_order_acquire)) {
        EntryRef revMapIdx = _reverseMappingIndices.acquire_elem_ref(targetLid).load_acquire();
        _reverseMapping.foreach_frozen_key(revMapIdx, std::forward<FunctionType>(func));
    }
}

}

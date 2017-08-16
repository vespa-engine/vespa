// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/btree/btreestore.h>
#include <vespa/searchlib/common/rcuvector.h>

namespace search::attribute {

class Reference;

/*
 * Class representing mappings in a reference attribute.
 */
class ReferenceMappings
{
    using GenerationHolder = vespalib::GenerationHolder;
    using EntryRef = search::datastore::EntryRef;
    using ReverseMappingIndices = RcuVectorBase<EntryRef>;
    using ReverseMapping = btree::BTreeStore<uint32_t, btree::BTreeNoLeafData,
                                             btree::NoAggregated,
                                             std::less<uint32_t>,
                                             btree::BTreeDefaultTraits,
                                             btree::NoAggrCalc>;
    using generation_t = vespalib::GenerationHandler::generation_t;

    // Vector containing references to trees of lids referencing given
    // referenced lid.
    ReverseMappingIndices _reverseMappingIndices;
    // Store of B-Trees, used to map from gid or referenced lid to
    // referencing lids.
    ReverseMapping _reverseMapping;


public:
    ReferenceMappings(GenerationHolder &genHolder);

    ~ReferenceMappings();

    // Cleanup helpers, to free resources
    void clearBuilder() { _reverseMapping.clearBuilder(); }
    void clearMapping(const Reference &entry);

    // Hold list management & freezing
    void trimHoldLists(generation_t usedGen) { _reverseMapping.trimHoldLists(usedGen); }
    void freeze() { _reverseMapping.freeze(); }
    void transferHoldLists(generation_t generation) { _reverseMapping.transferHoldLists(generation); }

    // Handle mapping changes
    void notifyGidToLidChange(const Reference &entry, uint32_t referencedLid);
    void syncReverseMappingIndices(const Reference &entry);
    void removeReverseMapping(const Reference &entry, uint32_t lid);
    void addReverseMapping(const Reference &entry, uint32_t lid);

    // Setup mapping after load
    void buildReverseMapping(const Reference &entry, const std::vector<ReverseMapping::KeyDataType> &adds);

    // Reader API, reader must hold generation guard
    template <typename FunctionType>
    void
    foreach_lid(uint32_t referencedLid, FunctionType &&func) const;
};

template <typename FunctionType>
void
ReferenceMappings::foreach_lid(uint32_t referencedLid, FunctionType &&func) const
{
    if (referencedLid < _reverseMappingIndices.size()) {
        EntryRef revMapIdx = _reverseMappingIndices[referencedLid];
        _reverseMapping.foreach_frozen_key(revMapIdx, std::forward<FunctionType>(func));
    }
}

}

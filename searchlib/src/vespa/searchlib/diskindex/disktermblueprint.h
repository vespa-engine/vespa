// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "diskindex.h"
#include <vespa/searchlib/queryeval/blueprint.h>

namespace search::diskindex {

/**
 * Blueprint implementation for term searching in a disk index.
 **/
class DiskTermBlueprint : public queryeval::SimpleLeafBlueprint
{
private:
    queryeval::FieldSpec             _field;
    const DiskIndex               &  _diskIndex;
    vespalib::string                 _query_term;
    DiskIndex::LookupResult::UP      _lookupRes;
    bool                             _useBitVector;
    bool                             _fetchPostingsDone;
    index::PostingListHandle::UP     _postingHandle;
    BitVector::UP                    _bitVector;

public:
    /**
     * Create a new blueprint.
     *
     * @param field        the field to search in.
     * @param diskIndex    the disk index used to read the bit vector or posting list.
     * @param lookupRes    the result after disk dictionary lookup.
     * @param useBitVector whether or not we should use bit vector.
     **/
    DiskTermBlueprint(const queryeval::FieldSpec & field,
                      const DiskIndex & diskIndex,
                      const vespalib::string& query_term,
                      DiskIndex::LookupResult::UP lookupRes,
                      bool useBitVector);

    queryeval::FlowStats calculate_flow_stats(uint32_t docid_limit) const override;
    
    // Inherit doc from Blueprint.
    // For now, this DiskTermBlueprint instance must have longer lifetime than the created iterator.
    std::unique_ptr<queryeval::SearchIterator> createLeafSearch(const fef::TermFieldMatchDataArray & tfmda) const override;

    void fetchPostings(const queryeval::ExecuteInfo &execInfo) override;

    std::unique_ptr<queryeval::SearchIterator> createFilterSearch(FilterConstraint) const override;

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
};

}

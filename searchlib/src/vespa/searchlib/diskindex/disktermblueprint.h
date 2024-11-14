// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_index.h"
#include <vespa/searchlib/queryeval/blueprint.h>

namespace search::diskindex {

/**
 * Blueprint implementation for term searching in a disk index.
 **/
class DiskTermBlueprint : public queryeval::SimpleLeafBlueprint
{
private:
    queryeval::FieldSpec             _field;
    const FieldIndex&                _field_index;
    std::string                 _query_term;
    index::DictionaryLookupResult          _lookupRes;
    index::BitVectorDictionaryLookupResult _bitvector_lookup_result;
    bool                             _useBitVector;
    bool                             _fetchPostingsDone;
    index::PostingListHandle         _postingHandle;
    std::shared_ptr<BitVector>       _bitVector;

public:
    /**
     * Create a new blueprint.
     *
     * @param field        the field to search in.
     * @param field_index    the field index used to read the bit vector or posting list.
     * @param lookupRes    the result after disk dictionary lookup.
     * @param useBitVector whether or not we should use bit vector.
     **/
    DiskTermBlueprint(const queryeval::FieldSpec & field,
                      const FieldIndex& field_index,
                      const std::string& query_term,
                      index::DictionaryLookupResult lookupRes,
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

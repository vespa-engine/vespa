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
    std::string                      _query_term;
    index::DictionaryLookupResult    _lookupRes;
    index::BitVectorDictionaryLookupResult _bitvector_lookup_result;
    bool                             _is_filter_field;
    bool                             _fetchPostingsDone;
    index::PostingListHandle         _postingHandle;
    std::shared_ptr<BitVector>       _bitVector;
    mutable std::mutex               _mutex;
    mutable std::shared_ptr<BitVector> _late_bitvector;

    bool use_bitvector() const;
    const BitVector* get_bitvector() const;
    void log_bitvector_read() const __attribute__((noinline));
    void log_posting_list_read() const __attribute__((noinline));
public:
    /**
     * Create a new blueprint.
     *
     * The filter threshold setting for the field determines whether bitvector is used for searching.
     * If the field is a filter: force use of bitvector.
     * Otherwise the filter threshold is compared against the hit estimate of the query term after dictionary lookup.
     * If the hit estimate is above the filter threshold: force use of bitvector.
     * If no bitvector exists for the term, a fake bitvector wrapping the posocc iterator is used.
     *
     * @param field           The field to search in.
     * @param field_index     The field index used to read the bit vector or posting list.
     * @param query_term      The query term to search for.
     * @param lookupRes       The result after disk dictionary lookup.
     **/
    DiskTermBlueprint(const queryeval::FieldSpec & field,
                      const FieldIndex& field_index,
                      const std::string& query_term,
                      index::DictionaryLookupResult lookupRes);

    queryeval::FlowStats calculate_flow_stats(uint32_t docid_limit) const override;
    
    // Inherit doc from Blueprint.
    // For now, this DiskTermBlueprint instance must have longer lifetime than the created iterator.
    std::unique_ptr<queryeval::SearchIterator> createLeafSearch(const fef::TermFieldMatchDataArray & tfmda) const override;

    void fetchPostings(const queryeval::ExecuteInfo &execInfo) override;

    std::unique_ptr<queryeval::SearchIterator> createFilterSearch(FilterConstraint) const override;

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
};

}

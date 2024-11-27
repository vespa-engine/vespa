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
    double                           _bitvector_limit;
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
     * @param field           The field to search in.
     * @param field_index     The field index used to read the bit vector or posting list.
     * @param lookupRes       The result after disk dictionary lookup.
     * @param is_filter_field Whether this field is filter and we should force use of bit vector.
     * @param bitvector_limit The hit estimate limit for whether bitvector should be used for searching this term.
                              This can be used to tune performance at the cost of quality.
                              If no bitvector exists for the term, a fake bitvector wrapping the posocc iterator is used.
     **/
    DiskTermBlueprint(const queryeval::FieldSpec & field,
                      const FieldIndex& field_index,
                      const std::string& query_term,
                      index::DictionaryLookupResult lookupRes,
                      bool is_filter_field,
                      double bitvector_limit);

    queryeval::FlowStats calculate_flow_stats(uint32_t docid_limit) const override;
    
    // Inherit doc from Blueprint.
    // For now, this DiskTermBlueprint instance must have longer lifetime than the created iterator.
    std::unique_ptr<queryeval::SearchIterator> createLeafSearch(const fef::TermFieldMatchDataArray & tfmda) const override;

    void fetchPostings(const queryeval::ExecuteInfo &execInfo) override;

    std::unique_ptr<queryeval::SearchIterator> createFilterSearch(FilterConstraint) const override;

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
};

}

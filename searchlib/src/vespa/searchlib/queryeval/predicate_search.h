// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/predicate/predicate_posting_list.h>
#include <vespa/searchlib/common/condensedbitvectors.h>
#include <vespa/searchlib/predicate/predicate_tree_annotator.h>
#include <vector>

namespace search::fef {
    class TermFieldMatchData;
    class TermFieldMatchDataArray;
}

namespace search::queryeval {

class SkipMinFeature
{
public:
    using UP = std::unique_ptr<SkipMinFeature>;
    virtual ~SkipMinFeature() { }
    VESPA_DLL_LOCAL virtual uint32_t next() = 0;
    static SkipMinFeature::UP create(const uint8_t * min_feature, const uint8_t * kv, size_t sz);
};

/**
 * Search iterator implementing the interval algorithm for boolean
 * search. It operates on PredicatePostingLists, as defined above.
 */
using IntervalRange = uint16_t;

class PredicateSearch : public SearchIterator {
    SkipMinFeature::UP _skip;
    std::vector<predicate::PredicatePostingList::UP> _posting_lists;
    std::vector<uint16_t> _sorted_indexes;
    std::vector<uint16_t> _sorted_indexes_merge_buffer;
    std::vector<uint32_t> _doc_ids;
    std::vector<uint32_t> _intervals;
    std::vector<uint64_t> _subqueries;
    uint64_t *_subquery_markers;
    bool * _visited;
    fef::TermFieldMatchData *_termFieldMatchData;
    const uint8_t * _min_feature_vector;
    const IntervalRange * _interval_range_vector;

    VESPA_DLL_LOCAL bool advanceOneTo(uint32_t doc_id, size_t index);
    VESPA_DLL_LOCAL void advanceAllTo(uint32_t doc_id);
    VESPA_DLL_LOCAL bool evaluateHit(uint32_t doc_id, uint32_t k);
    VESPA_DLL_LOCAL size_t sortIntervals(uint32_t doc_id, uint32_t k);
    VESPA_DLL_LOCAL void skipMinFeature(uint32_t doc_id) __attribute__((noinline));

public:
    PredicateSearch(const uint8_t * minFeature,
                    const IntervalRange * interval_range_vector,
                    IntervalRange max_interval_range,
                    CondensedBitVector::CountVector kV,
                    std::vector<predicate::PredicatePostingList::UP> posting_lists,
                    const fef::TermFieldMatchDataArray &tfmda);
    ~PredicateSearch();

    void doSeek(uint32_t doc_id) override;
    void doUnpack(uint32_t doc_id) override;
};

}

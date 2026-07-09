// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "numeric_posting_search_context.h"

namespace search::attribute {

template <typename BaseSC, typename AttrT, typename DataT>
NumericPostingSearchContext<BaseSC, AttrT, DataT>::NumericPostingSearchContext(BaseSC&&      base_sc,
                                                                               const Params& params_in,
                                                                               const AttrT&  toBeSearched)
    : Parent(std::move(base_sc), params_in.useBitVector(), toBeSearched), _params(params_in) {
    if (valid()) {
        if (_low == _high) {
            auto comp = _enumStore.make_comparator(_low);
            this->lookupTerm(comp);
        } else if (_low < _high) {
            bool shouldApplyRangeLimit = (params().diversityAttribute() == nullptr) && (this->getRangeLimit() != 0);
            getIterators(shouldApplyRangeLimit);
        }
        if (this->_uniqueValues == 1u) {
            this->lookupSingle();
        }
    }
}

template <typename BaseSC, typename AttrT, typename DataT>
void NumericPostingSearchContext<BaseSC, AttrT, DataT>::getIterators(bool shouldApplyRangeLimit) {
    bool isFloat =
        _toBeSearched.getBasicType() == BasicType::FLOAT || _toBeSearched.getBasicType() == BasicType::DOUBLE;
    search::Range<BaseType> capped = this->template cappedRange<BaseType>(isFloat);

    auto compLow = _enumStore.make_comparator(capped.lower());
    auto compHigh = _enumStore.make_comparator(capped.upper());

    this->lookupRange(compLow, compHigh);
    if (!this->_dictionary.get_has_btree_dictionary()) {
        _low = capped.lower();
        _high = capped.upper();
        return;
    }
    if (shouldApplyRangeLimit) {
        this->applyRangeLimit(this->getRangeLimit());
    }

    if (this->_lowerDictItr != this->_upperDictItr) {
        _low = _enumStore.get_value(this->_lowerDictItr.getKey().load_acquire());
        auto last = this->_upperDictItr;
        --last;
        _high = _enumStore.get_value(last.getKey().load_acquire());
    }
}

template <typename BaseSC, typename AttrT, typename DataT>
bool NumericPostingSearchContext<BaseSC, AttrT, DataT>::use_posting_lists_when_non_strict(
    const queryeval::ExecuteInfo& info) const {
    // The following constants are derived after running parts of
    // the range search performance test with 10M documents on an Apple M1 Pro with 32 GB memory.
    // This code was compiled with two different behaviors:
    //   1) 'lookup matching' (never use posting lists).
    //   2) 'posting list matching' (always use posting lists).
    // https://github.com/vespa-engine/system-test/tree/master/tests/performance/range_search
    //
    // The following test cases were used:
    // range_hits_ratio=[100, 200], values_in_range=100, fast_search=true, filter_hits_ratio=[1, 2, 4, 6, 8, 10, 20,
    // 40, 60, 80, 100, 120, 140, 160, 200].
    //
    // By comparing the avg query latency between 1) 'lookup matching' and 2) 'posting list matching'
    // we find the crossover point between the two strategies.
    //
    // Excerpt of results for range_hits_ratio=[100] and filter_hits_ratio=[10, 20, 40, 60]:
    //   1) 'lookup matching':       [7.1, 8.4, 14.1, 17.6]
    //   2) 'posting list matching': [7.3, 8.8, 7.4, 8.1]
    // With filter_hits_ratio=20, lookup matching is best.
    // With filter_hits_ratio=40, posting list matching is best.
    //
    // The extra cost and difference between the two strategies is modelled as follows:
    //   1) lookup matching: exp_doc_hits * lookup_match_constant (LMC)
    //   2) posting list matching: estimated_hits_in_range() * posting_list_merge_constant (PLMC)
    //
    // At the crossover point (filter_hits_ratio=20) the following costs are calculated:
    //   1) 10M*20/100 * LMC = 200k * LMC
    //   2) 10M*100/1000 * PLMC = 1M * PLMC
    //
    // Based on this we see that LMC = 5 * PLMC.
    // The same relationship is found with the test case range_hits_ratio=[200].

    constexpr double lookup_match_constant = 5.0;
    constexpr double posting_list_merge_constant = 1.0;

    uint32_t exp_doc_hits = this->_docIdLimit * info.hit_rate();
    float    lookup_match_cost = exp_doc_hits * this->avg_values_per_document() * lookup_match_constant;
    float    posting_list_cost = this->estimated_hits_in_range() * posting_list_merge_constant;
    return posting_list_cost < lookup_match_cost;
}

template <typename BaseSC, typename AttrT, typename DataT>
size_t NumericPostingSearchContext<BaseSC, AttrT, DataT>::calc_estimated_hits_in_range() const {
    size_t exact_sum = 0;
    size_t estimated_sum = 0;

    // Sample lower range
    auto it_forward = this->_lowerDictItr;
    for (uint32_t count = 0; (it_forward != this->_upperDictItr) && (count < this->max_posting_lists_to_count);
         ++it_forward, ++count)
    {
        exact_sum += this->_posting_store.frozenSize(it_forward.getData().load_acquire());
    }
    if (it_forward != this->_upperDictItr) {
        // Sample upper range
        auto it_backward = this->_upperDictItr;
        for (uint32_t count = 0; (it_backward != it_forward) && (count < this->max_posting_lists_to_count); ++count) {
            --it_backward;
            exact_sum += this->_posting_store.frozenSize(it_backward.getData().load_acquire());
        }
        if (it_forward != it_backward) {
            // Estimate the rest
            uint32_t remaining_posting_lists = it_backward - it_forward;
            double   measured_hits_per_posting_list =
                static_cast<double>(exact_sum) / (this->max_posting_lists_to_count * 2);
            // Let measure and global rate count equally, to reduce the effect of outlayers.
            estimated_sum =
                remaining_posting_lists * (measured_hits_per_posting_list + this->avg_postinglist_size()) / 2;
        }
    }
    return exact_sum + estimated_sum;
}

} // namespace search::attribute

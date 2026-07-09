// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "postinglistsearchcontext.h"

namespace search::attribute {

template <typename BaseSC, typename AttrT, typename DataT>
class NumericPostingSearchContext : public PostingSearchContext<BaseSC, PostingListSearchContextT<DataT>, AttrT> {
private:
    using ExecuteInfo = queryeval::ExecuteInfo;
    using Parent = PostingSearchContext<BaseSC, PostingListSearchContextT<DataT>, AttrT>;
    using BaseType = typename AttrT::T;
    using Params = attribute::SearchContextParams;
    using Parent::_enumStore;
    using Parent::_high;
    using Parent::_low;
    using Parent::_toBeSearched;
    Params _params;

    void getIterators(bool shouldApplyRangeLimit);
    bool valid() const override { return this->isValid(); }

    HitEstimate calc_hit_estimate() const override {
        HitEstimate        estimate = PostingListSearchContextT<DataT>::calc_hit_estimate();
        const unsigned int limit = std::abs(this->getRangeLimit());
        return ((limit > 0) && (limit < estimate.est_hits())) ? HitEstimate(limit) : estimate;
    }
    void fetchPostings(const ExecuteInfo& execInfo, bool strict) override {
        if (params().diversityAttribute() != nullptr) {
            bool   forward = (this->getRangeLimit() > 0);
            size_t wanted_hits = std::abs(this->getRangeLimit());
            PostingListSearchContextT<DataT>::diversify(forward, wanted_hits, *(params().diversityAttribute()),
                                                        this->getMaxPerGroup(), params().diversityCutoffGroups(),
                                                        params().diversityCutoffStrict());
        } else {
            PostingListSearchContextT<DataT>::fetchPostings(execInfo, strict);
        }
    }

    bool use_posting_lists_when_non_strict(const ExecuteInfo& info) const override;
    size_t calc_estimated_hits_in_range() const override;

public:
    NumericPostingSearchContext(BaseSC&& base_sc, const Params& params, const AttrT& toBeSearched);
    const Params& params() const { return _params; }
};

} // namespace search::attribute

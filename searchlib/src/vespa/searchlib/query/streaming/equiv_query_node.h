// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_term.h"

namespace search::streaming {

/**
   N-ary "EQUIV" operator that merges terms from nodes below.
*/
class EquivQueryNode : public MultiTerm
{
public:
    EquivQueryNode(std::unique_ptr<QueryNodeResultBase> result_base, uint32_t num_terms);
    ~EquivQueryNode() override;
    bool evaluate() const override;
    const HitList & evaluateHits(HitList & hl) const override;
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data) override;
    EquivQueryNode* as_equiv_query_node() noexcept override;
    const EquivQueryNode* as_equiv_query_node() const noexcept override;
    std::vector<std::unique_ptr<QueryTerm>> steal_terms();
};

}

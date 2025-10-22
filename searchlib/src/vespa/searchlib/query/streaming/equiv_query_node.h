// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_term.h"

namespace search::streaming {

class QueryVisitor;

/**
   N-ary "EQUIV" operator that merges terms from nodes below.
*/
class EquivQueryNode : public MultiTerm
{
public:
    EquivQueryNode(std::unique_ptr<QueryNodeResultBase> result_base, uint32_t num_terms);
    ~EquivQueryNode() override;
    bool evaluate() override;
    const HitList & evaluateHits(HitList & hl) override;
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data,
                           const fef::IIndexEnvironment& index_env, search::common::ElementIds element_ids) override;
    bool multi_index_terms() const noexcept override;
    const EquivQueryNode* as_equiv_query_node() const noexcept override;
    std::vector<std::unique_ptr<QueryTerm>> steal_terms();
    void accept(QueryVisitor &visitor);
};

}

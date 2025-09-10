// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_term.h"

namespace search::streaming {

/**
   N-ary Same element operator. All terms must be within the same element.
*/
class SameElementQueryNode : public QueryTerm
{
    QueryNodeList _children;
public:
    SameElementQueryNode(std::unique_ptr<QueryNodeResultBase> result_base, string index, uint32_t num_terms) noexcept;
    ~SameElementQueryNode() override;
    bool evaluate() const override;
    const HitList & evaluateHits(HitList & hl) const override;
    void get_element_ids(std::vector<uint32_t>& element_ids) const override;
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env) override;
    void reset() override;
    void add_child(std::unique_ptr<QueryNode> child);
    bool is_same_element_query_node() const noexcept override;
    SameElementQueryNode* as_same_element_query_node() noexcept override;
    const SameElementQueryNode* as_same_element_query_node() const noexcept override;
    void get_hidden_leaves(QueryTermList & tl);
    void get_hidden_leaves(ConstQueryTermList & tl) const;
    const QueryNodeList& get_children() const noexcept { return _children; }
};

}

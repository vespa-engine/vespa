// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_term.h"

namespace search::streaming {

/**
   N-ary Same element operator. All terms must be within the same element.
*/
class SameElementQueryNode : public MultiTerm
{
public:
    SameElementQueryNode(std::unique_ptr<QueryNodeResultBase> result_base, string index, uint32_t num_terms) noexcept;
    ~SameElementQueryNode() override;
    bool evaluate() const override;
    const HitList & evaluateHits(HitList & hl) const override;
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env) override;
    bool multi_index_terms() const noexcept override;
    bool is_same_element_query_node() const noexcept override;
};

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_term.h"

namespace search::streaming {

/*
 * A weighted set query term for streaming search.
 */
class WeightedSetTerm : public MultiTerm {
public:
    WeightedSetTerm(std::unique_ptr<QueryNodeResultBase> result_base, string index, uint32_t num_terms);
    ~WeightedSetTerm() override;
    void get_element_ids(std::vector<uint32_t>& element_ids) const override;
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env) override;
};

}

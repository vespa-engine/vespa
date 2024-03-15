// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dot_product_term.h"

namespace search::streaming {

/*
 * A wand query term for streaming search.
 */
class WandTerm : public DotProductTerm {
    double _score_threshold;
public:
    WandTerm(std::unique_ptr<QueryNodeResultBase> result_base, const string& index, uint32_t num_terms);
    ~WandTerm() override;
    void set_score_threshold(double value) { _score_threshold = value; }
    bool evaluate() const override;
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env) override;
};

}

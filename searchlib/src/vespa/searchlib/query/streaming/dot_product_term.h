// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_term.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <optional>

namespace search::streaming {

/*
 * A dot product query term for streaming search.
 */
class DotProductTerm : public MultiTerm {
protected:
    using Scores = vespalib::hash_map<uint32_t,double>;
    void build_scores(Scores& scores) const;
    void unpack_scores(Scores& scores, std::optional<double> score_threshold, uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data);
public:
    DotProductTerm(std::unique_ptr<QueryNodeResultBase> result_base, const string& index, uint32_t num_terms);
    ~DotProductTerm() override;
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env) override;
};

}

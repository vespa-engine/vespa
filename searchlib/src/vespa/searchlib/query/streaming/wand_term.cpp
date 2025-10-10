// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wand_term.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>

using search::fef::ITermData;
using search::fef::MatchData;

namespace search::streaming {

WandTerm::WandTerm(std::unique_ptr<QueryNodeResultBase> result_base, string index, uint32_t num_terms)
    : DotProductTerm(std::move(result_base), std::move(index), num_terms),
      _score_threshold(0.0)
{
}

WandTerm::~WandTerm() = default;

bool
WandTerm::evaluate()
{
    if (_score_threshold <= 0.0) {
        return DotProductTerm::evaluate();
    }
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    Scores scores;
    build_scores(scores);
    bool result = false;
    for (auto &field_and_score : scores) {
        if (field_and_score.second > _score_threshold) {
            result = true;
            break;
        }
    }
    _cached_evaluate_result.emplace(result);
    return result;
}

void
WandTerm::unpack_match_data(uint32_t docid, const ITermData& td, MatchData& match_data, const fef::IIndexEnvironment&)
{
    Scores scores;
    build_scores(scores);
    unpack_scores(scores, _score_threshold, docid, td, match_data);
}

}

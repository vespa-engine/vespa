// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wand_term.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>

using search::fef::ITermData;
using search::fef::MatchData;

namespace search::streaming {

WandTerm::WandTerm(std::unique_ptr<QueryNodeResultBase> result_base, const string & index, uint32_t num_terms)
    : DotProductTerm(std::move(result_base), index, num_terms),
      _score_threshold(0.0)
{
}

WandTerm::~WandTerm() = default;

bool
WandTerm::evaluate() const
{
    if (_score_threshold <= 0.0) {
        return DotProductTerm::evaluate();
    }
    Scores scores;
    build_scores(scores);
    for (auto &field_and_score : scores) {
        if (field_and_score.second > _score_threshold) {
            return true;
        }
    }
    return false;
}

void
WandTerm::unpack_match_data(uint32_t docid, const ITermData& td, MatchData& match_data, const fef::IIndexEnvironment&)
{
    Scores scores;
    build_scores(scores);
    unpack_scores(scores, _score_threshold, docid, td, match_data);
}

}

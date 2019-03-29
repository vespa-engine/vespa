// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_scorer.h"
#include <cassert>

using search::feature_t;
using search::fef::FeatureResolver;
using search::fef::RankProgram;
using search::fef::LazyValue;
using search::queryeval::SearchIterator;

namespace proton::matching {

namespace {

LazyValue
extractScoreFeature(const RankProgram &rankProgram)
{
    FeatureResolver resolver(rankProgram.get_seeds());
    assert(resolver.num_features() == 1u);
    return resolver.resolve(0);
}

}

DocumentScorer::DocumentScorer(RankProgram &rankProgram,
                               SearchIterator &searchItr)
    : _searchItr(searchItr),
      _scoreFeature(extractScoreFeature(rankProgram))
{
}

feature_t
DocumentScorer::score(uint32_t docId)
{
    return doScore(docId);
}

}

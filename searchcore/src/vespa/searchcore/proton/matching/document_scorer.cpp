// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_scorer.h"
#include <algorithm>
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

void
DocumentScorer::score(TaggedHits &hits)
{
    auto sort_on_docid = [](const TaggedHit &a, const TaggedHit &b){ return (a.first.first < b.first.first); };
    std::sort(hits.begin(), hits.end(), sort_on_docid);
    for (auto &hit: hits) {
        hit.first.second = doScore(hit.first.first);
    }
}

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_scorer.h"

#include <vespa/searchlib/fef/rank_program.h>

#include <algorithm>
#include <cassert>
#include <vector>

using search::feature_t;
using search::fef::FeatureResolver;
using search::fef::LazyValue;
using search::fef::RankProgram;
using search::queryeval::SearchIterator;

namespace proton::matching {

namespace {

LazyValue extractScoreFeature(const RankProgram& rankProgram) {
    FeatureResolver resolver(rankProgram.get_seeds());
    assert(resolver.num_features() == 1u);
    return resolver.resolve(0);
}

} // namespace

DocumentScorer::DocumentScorer(RankProgram& rankProgram, SearchIterator& searchItr,
                               PrefetchAttributes prefetch_attributes)
    : _searchItr(searchItr),
      _scoreFeature(extractScoreFeature(rankProgram)),
      _prefetch_attributes(prefetch_attributes),
      _prefetch_stats() {
}

void DocumentScorer::prefetch(const TaggedHits& hits) {
    std::vector<uint32_t> docids;
    docids.reserve(hits.size());
    for (const auto& hit : hits) {
        docids.push_back(hit.first.first);
    }
    _prefetch_stats.clear();
    _prefetch_stats.reserve(_prefetch_attributes.size());
    for (const auto* attr : _prefetch_attributes) {
        auto start = vespalib::steady_clock::now();
        auto result = attr->prefetch_docs(docids);
        _prefetch_stats.push_back(PrefetchStats{result, vespalib::steady_clock::now() - start});
    }
}

void DocumentScorer::score(TaggedHits& hits) {
    if (hits.empty()) {
        return;
    }
    auto sort_on_docid = [](const TaggedHit& a, const TaggedHit& b) { return (a.first.first < b.first.first); };
    std::sort(hits.begin(), hits.end(), sort_on_docid);
    if (!_prefetch_attributes.empty()) {
        prefetch(hits);
    }
    _searchItr.initRange(hits.front().first.first, hits.back().first.first + 1);
    for (auto& hit : hits) {
        hit.first.second = doScore(hit.first.first);
    }
}

} // namespace proton::matching

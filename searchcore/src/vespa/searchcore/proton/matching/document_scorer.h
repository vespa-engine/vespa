// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/rank_program.h>
#include <vespa/searchlib/queryeval/hitcollector.h>
#include <vespa/searchlib/queryeval/searchiterator.h>

namespace proton {
namespace matching {

/**
 * Class used to calculate the rank score for a set of documents using
 * a rank program for calculation and a search iterator for unpacking match data.
 * The calculateScore() function is always called in increasing docId order.
 */
class DocumentScorer : public search::queryeval::HitCollector::DocumentScorer
{
private:
    search::queryeval::SearchIterator &_searchItr;
    search::fef::LazyValue _scoreFeature;

public:
    DocumentScorer(search::fef::RankProgram &rankProgram,
                   search::queryeval::SearchIterator &searchItr);

    search::feature_t doScore(uint32_t docId) {
        _searchItr.unpack(docId);
        return _scoreFeature.as_number(docId);
    }

    virtual search::feature_t score(uint32_t docId) override;
};

} // namespace proton::matching
} // namespace proton

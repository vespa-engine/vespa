// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_match_loop_communicator.h"
#include <vespa/searchlib/fef/rank_program.h>
#include <vespa/searchlib/queryeval/searchiterator.h>

namespace proton::matching {

/**
 * Class used to calculate the rank score for a set of documents using
 * a rank program for calculation and a search iterator for unpacking
 * match data. The doScore function must be called with increasing
 * docid.
 */
class DocumentScorer
{
private:
    search::queryeval::SearchIterator &_searchItr;
    search::fef::LazyValue _scoreFeature;

public:
    using TaggedHit = IMatchLoopCommunicator::TaggedHit;
    using TaggedHits = IMatchLoopCommunicator::TaggedHits;

    DocumentScorer(search::fef::RankProgram &rankProgram,
                   search::queryeval::SearchIterator &searchItr);

    search::feature_t doScore(uint32_t docId) {
        _searchItr.unpack(docId);
        return _scoreFeature.as_number(docId);
    }

    // annotate hits with rank score, may change order
    void score(TaggedHits &hits);
};

}

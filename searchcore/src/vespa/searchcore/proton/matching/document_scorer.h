// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_match_loop_communicator.h"

#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/queryeval/searchiterator.h>

#include <span>

namespace search::attribute {
class IAttributeVector;
}
namespace search::fef {
class RankProgram;
}

namespace proton::matching {

/**
 * Class used to calculate the rank score for a set of documents using
 * a rank program for calculation and a search iterator for unpacking
 * match data. The doScore function must be called with increasing
 * docid.
 *
 * Optionally, a set of attributes is told to prefetch the values of
 * all hits before the hits are scored (cf. IAttributeVector::prefetch_docs).
 */
class DocumentScorer {
public:
    using PrefetchAttributes = std::span<const search::attribute::IAttributeVector* const>;

private:
    search::queryeval::SearchIterator& _searchItr;
    search::fef::LazyValue             _scoreFeature;
    PrefetchAttributes                 _prefetch_attributes;

    void prefetch(const IMatchLoopCommunicator::TaggedHits& hits) const;

public:
    using TaggedHit = IMatchLoopCommunicator::TaggedHit;
    using TaggedHits = IMatchLoopCommunicator::TaggedHits;

    DocumentScorer(search::fef::RankProgram& rankProgram, search::queryeval::SearchIterator& searchItr,
                   PrefetchAttributes prefetch_attributes = {});

    search::feature_t doScore(uint32_t docId) {
        _searchItr.unpack(docId);
        return _scoreFeature.as_number(docId);
    }

    // annotate hits with rank score, may change order
    void score(TaggedHits& hits);
};

} // namespace proton::matching

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_match_loop_communicator.h"

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/vespalib/util/time.h>

#include <span>
#include <vector>

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
    using IAttributeVector = search::attribute::IAttributeVector;
    using PrefetchAttributes = std::span<const IAttributeVector* const>;
    // What was prefetched for one attribute, and how long the call took.
    struct PrefetchStats {
        IAttributeVector::PrefetchResult result;
        vespalib::duration               time;
    };

private:
    search::queryeval::SearchIterator& _searchItr;
    search::fef::LazyValue             _scoreFeature;
    PrefetchAttributes                 _prefetch_attributes;
    std::vector<PrefetchStats>         _prefetch_stats;

    void prefetch(const IMatchLoopCommunicator::TaggedHits& hits);

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

    // Stats for the last score() call, one entry per prefetch attribute (empty if nothing was prefetched).
    const std::vector<PrefetchStats>& prefetch_stats() const noexcept { return _prefetch_stats; }
};

} // namespace proton::matching

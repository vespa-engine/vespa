// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/wand/weak_and_search.h>
#include <vespa/searchlib/queryeval/wand/wand_parts.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <functional>

using search::queryeval::wand::DotProductScorer;
using search::queryeval::wand::TermFrequencyScorer;
using namespace search::queryeval;

namespace rise {

struct TermFreqScorer
{
    static int64_t calculateMaxScore(const wand::Term &term) {
        return TermFrequencyScorer::calculateMaxScore(term);
    }
    static int64_t calculateScore(const wand::Term &term, uint32_t docId) {
        term.search->unpack(docId);
        return term.maxScore;
    }
};

template <typename Scorer, typename Cmp>
class RiseWand : public search::queryeval::SearchIterator
{
public:
    typedef uint32_t docid_t;
    typedef uint64_t score_t;
    typedef search::queryeval::wand::Terms Terms;
    typedef search::queryeval::SearchIterator *PostingStreamPtr;

private:
    // comparator class that compares two streams. The variables a and b are
    // logically indices into the streams vector.
    class StreamComparator
    {
    private:
        const docid_t *_streamDocIds;
        //const addr_t *const *_streamPayloads;

    public:
        StreamComparator(const docid_t *streamDocIds);
        //const addr_t *const *streamPayloads);
        inline bool operator()(const uint16_t a, const uint16_t b);
    };

    // number of streams present in the query
    uint32_t _numStreams;

    // we own our substreams
    std::vector<PostingStreamPtr> _streams;

    size_t _lastPivotIdx;

    // array of current doc ids for the various streams
    docid_t *_streamDocIds;

    // two arrays of indices into the _streams vector. This is used for merge.
    // inplace_merge is not as efficient as the copy merge.
    uint16_t *_streamIndices;
    uint16_t *_streamIndicesAux;

    // comparator that compares two streams
    StreamComparator _streamComparator;

    //-------------------------------------------------------------------------
    // variables used for scoring and pruning

    size_t                           _n;
    score_t                          _limit;
    score_t                         *_streamScores;
    vespalib::PriorityQueue<score_t> _scores;
    Terms                            _terms;

    //-------------------------------------------------------------------------

    /**
     * Find the pivot feature index
     *
     * @param threshold  score threshold
     * @param pivotIdx   pivot index
     *
     * @return  whether a valid pivot index is found
     */
    bool _findPivotFeatureIdx(const score_t threshold, uint32_t &pivotIdx);

    /**
     * let the first numStreamsToMove streams in the stream
     * vector move to the next doc, and sort them.
     *
     * @param numStreamsToMove  the number of streams that should move
     */
    void _moveStreamsAndSort(const uint32_t numStreamsToMove);

    /**
     * let the first numStreamsToMove streams in the stream
     * vector move to desiredDocId or to the first docId greater than
     * desiredDocId if desiredDocId does not exist in this stream,
     * and sort them.
     *
     * @param numStreamsToMove  the number of streams that should move
     * @param desiredDocId  desired doc id
     *
     */
    void _moveStreamsToDocAndSort(const uint32_t numStreamsToMove, const docid_t desiredDocId);

    /**
     * do sort and merge for WAND
     *
     * @param numStreamsToSort  the number of streams (starting from the first one) should
     *                                           be sorted and then merge sort with the rest
     *
     */
    void _sortMerge(const uint32_t numStreamsToSort);

public:
    RiseWand(const Terms &terms, uint32_t n);
    ~RiseWand();
    void next();
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
};

typedef RiseWand<TermFreqScorer, std::greater_equal<uint64_t> > TermFrequencyRiseWand;
typedef RiseWand<DotProductScorer, std::greater<uint64_t> > DotProductRiseWand;

} // namespacve rise


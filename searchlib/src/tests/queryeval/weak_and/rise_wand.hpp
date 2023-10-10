// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rise_wand.h"
#include <vespa/searchlib/queryeval/wand/wand_parts.h>
#include <cmath>

using search::queryeval::wand::TermFrequencyScorer;

namespace rise {

template <typename Scorer, typename Cmp>
RiseWand<Scorer, Cmp>::RiseWand(const Terms &terms, uint32_t n)
    : _numStreams(0),
      _streams(),
      _lastPivotIdx(0),
      _streamDocIds(new docid_t[terms.size()]),
      _streamIndices(new uint16_t[terms.size()]),
      _streamIndicesAux(new uint16_t[terms.size()]),
      _streamComparator(_streamDocIds),
      _n(n),
      _limit(1),
      _streamScores(new score_t[terms.size()]),
      _scores(),
      _terms(terms)
{
    for (size_t i = 0; i < terms.size(); ++i) {
        _terms[i].maxScore = Scorer::calculateMaxScore(terms[i]);
        _streamScores[i] = _terms[i].maxScore;
        _streams.push_back(terms[i].search);
    }
    _numStreams = _streams.size();
    if (_numStreams == 0) {
        setAtEnd();
    }
    for (uint32_t i=0; i<_numStreams; ++i) {
        _streamIndices[i] = i;
    }
    for (uint32_t i=0; i<_numStreams; ++i) {
        _streamDocIds[i] = _streams[i]->getDocId();
    }
    std::sort(_streamIndices, _streamIndices+_numStreams, _streamComparator);
}

template <typename Scorer, typename Cmp>
RiseWand<Scorer, Cmp>::~RiseWand()
{
    for (size_t i = 0; i < _streams.size(); ++i) {
        delete _streams[i];
    }
    delete [] _streamScores;
    delete [] _streamIndicesAux;
    delete [] _streamIndices;
    delete [] _streamDocIds;
}

template <typename Scorer, typename Cmp>
void
RiseWand<Scorer, Cmp>::next()
{

    // We do not check whether the stream is already at the end
    // here based on the assumption that application won't call
    // next() for streams that are already at the end, or atleast
    // won't do this frequently.

    uint32_t pivotIdx;
    docid_t pivotDocId = search::endDocId;
    score_t threshold = _limit;

    while (true) {

        if (!_findPivotFeatureIdx(threshold, pivotIdx)) {
            setAtEnd();
            return;
        }

        pivotDocId = _streamDocIds[_streamIndices[pivotIdx]];

        if (_streamDocIds[_streamIndices[0]] == _streamDocIds[_streamIndices[pivotIdx]]) {

            // Found candidate. All cursors before (*_streams)[pivotIdx] point to
            // the same doc and this doc is the candidate for full evaluation.
            setDocId(pivotDocId);

            // Advance pivotIdx sufficiently so that all instances of pivotDocId are included
            while (pivotIdx < _numStreams-1 && _streamDocIds[_streamIndices[pivotIdx+1]] == pivotDocId) {
                ++pivotIdx;
            }

            _lastPivotIdx = pivotIdx;
            return; // scoring and threshold adjustment is done in doUnpack

        } else { // not all cursors upto the pivot are aligned at the same doc yet

            // decreases pivotIdx to the first stream pointing at the pivotDocId
            while (pivotIdx && _streamDocIds[_streamIndices[pivotIdx-1]] == pivotDocId) {
                --pivotIdx;
            }

            _moveStreamsToDocAndSort(pivotIdx, pivotDocId);
        }

    }  /* while (true) */
}

template <typename Scorer, typename Cmp>
bool
RiseWand<Scorer, Cmp>::_findPivotFeatureIdx(const score_t threshold, uint32_t &pivotIdx)
{
    uint32_t idx;
    score_t accumUB = 0;
    for (idx=0;
         !Cmp()(accumUB, threshold) && idx < _numStreams;
         ++idx) {
        accumUB += _streamScores[_streamIndices[idx]];
    }

    if( Cmp()(accumUB, threshold) ) {
        pivotIdx = idx - 1;
        return true;
    }
    return false;
}

template <typename Scorer, typename Cmp>
void
RiseWand<Scorer, Cmp>::_moveStreamsAndSort(const uint32_t numStreamsToMove)
{
    for (uint32_t i=0; i<numStreamsToMove; ++i) {
        _streams[_streamIndices[i]]->seek(_streams[_streamIndices[i]]->getDocId() + 1);
        _streamDocIds[_streamIndices[i]] = _streams[_streamIndices[i]]->getDocId();
    }
    _sortMerge(numStreamsToMove);
}

template <typename Scorer, typename Cmp>
void
RiseWand<Scorer, Cmp>::_moveStreamsToDocAndSort(const uint32_t numStreamsToMove,
                                   const docid_t desiredDocId)
{
    for (uint32_t i=0; i<numStreamsToMove; ++i) {
        _streams[_streamIndices[i]]->seek(desiredDocId);
        _streamDocIds[_streamIndices[i]] = _streams[_streamIndices[i]]->getDocId();
    }
    _sortMerge(numStreamsToMove);
}

template <typename Scorer, typename Cmp>
void RiseWand<Scorer, Cmp>::_sortMerge(const uint32_t numStreamsToMove)
{
    for (uint32_t i=0; i<numStreamsToMove; ++i) {
        _streamIndicesAux[i] = _streamIndices[i];
    }
    std::sort(_streamIndicesAux, _streamIndicesAux+numStreamsToMove, _streamComparator);

    uint16_t j=numStreamsToMove, k=0, i=0;
    while (i < numStreamsToMove && j < _numStreams) {
        if (_streamComparator(_streamIndicesAux[i], _streamIndices[j])) {
            _streamIndices[k++] = _streamIndicesAux[i++];
        }
        else {
            _streamIndices[k++] = _streamIndices[j++];
        }
    }

    if (j == _numStreams) {
        while (i < numStreamsToMove) {
            _streamIndices[k++] = _streamIndicesAux[i++];
        }
    }

    while (_numStreams &&
            _streamDocIds[_streamIndices[_numStreams-1]] == search::endDocId) {
        --_numStreams;
    }
}

template <typename Scorer, typename Cmp>
void
RiseWand<Scorer, Cmp>::doSeek(uint32_t docid)
{
    if (getDocId() != beginId() && (docid - 1) == getDocId()) {
        _moveStreamsAndSort(_lastPivotIdx + 1);
    } else {
        _moveStreamsToDocAndSort(_numStreams, docid);
    }
    next();
}

template <typename Scorer, typename Cmp>
void
RiseWand<Scorer, Cmp>::doUnpack(uint32_t docid)
{
    score_t score = 0;
    for (size_t i = 0; i <= _lastPivotIdx; ++i) {
        score += Scorer::calculateScore(_terms[_streamIndices[i]], docid);
    }
    if (_scores.size() < _n || _scores.front() < score) {
        _scores.push(score);
        if (_scores.size() > _n) {
            _scores.pop_front();
        }
        if (_scores.size() == _n) {
            _limit = _scores.front();
        }
    }
}

/**
 ************ BEGIN STREAM COMPARTOR *********************
 */
template <typename Scorer, typename Cmp>
RiseWand<Scorer, Cmp>::StreamComparator::StreamComparator(
        const docid_t *streamDocIds)
    : _streamDocIds(streamDocIds)
{
}

template <typename Scorer, typename Cmp>
inline bool
RiseWand<Scorer, Cmp>::StreamComparator::operator()(const uint16_t a,
                                       const uint16_t b)
{
    if (_streamDocIds[a] < _streamDocIds[b]) return true;
    return false;
}

/**
 ************ END STREAM COMPARTOR *********************
 */

} // namespace rise


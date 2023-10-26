// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metrics.h"
#include "computer.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <algorithm>
#include <cstdlib>
#include <cmath>
#include <cstdlib>

namespace search::features::fieldmatch {

Metrics::Metrics(const Computer *source) :
    _source(source),
    _complete(false),
    _outOfOrder(0),
    _segments(0),
    _gaps(0),
    _gapLength(0),
    _longestSequence(1),
    _head(-1),
    _tail(-1),
    _matches(0),
    _proximity(0),
    _unweightedProximity(0),
    _segmentDistance(0),
    _pairs(0),
    _weight(0),
    _significance(0),
    _occurrence(0), // default not given
    _weightedOccurrence(0), // default not given
    _absoluteOccurrence(0), // default not given
    _weightedAbsoluteOccurrence(0), // default not given
    _significantOccurrence(0), // default not given
    _currentSequence(0),
    _segmentStarts(),
    _queryLength(_source->getNumQueryTerms())
{
    _segmentStarts.reserve(100);
}

Metrics::Metrics(const Metrics &rhs) :
    _source(rhs._source),
    _complete(rhs._complete),
    _outOfOrder(rhs._outOfOrder),
    _segments(rhs._segments),
    _gaps(rhs._gaps),
    _gapLength(rhs._gapLength),
    _longestSequence(rhs._longestSequence),
    _head(rhs._head),
    _tail(rhs._tail),
    _matches(rhs._matches),
    _proximity(rhs._proximity),
    _unweightedProximity(rhs._unweightedProximity),
    _segmentDistance(rhs._segmentDistance),
    _pairs(rhs._pairs),
    _weight(rhs._weight),
    _significance(rhs._significance),
    _occurrence(rhs._occurrence),
    _weightedOccurrence(rhs._weightedOccurrence),
    _absoluteOccurrence(rhs._absoluteOccurrence),
    _weightedAbsoluteOccurrence(rhs._weightedAbsoluteOccurrence),
    _significantOccurrence(rhs._significantOccurrence),
    _currentSequence(rhs._currentSequence),
    _segmentStarts(rhs._segmentStarts),
    _queryLength(rhs._queryLength)
{
}

Metrics &
Metrics::operator=(const Metrics & rhs)
{
    if (this != &rhs) {
        _source = rhs._source;
        _complete = rhs._complete;
        _outOfOrder = rhs._outOfOrder;
        _segments = rhs._segments;
        _gaps = rhs._gaps;
        _gapLength = rhs._gapLength;
        _longestSequence = rhs._longestSequence;
        _head = rhs._head;
        _tail = rhs._tail;
        _matches = rhs._matches;
        _proximity = rhs._proximity;
        _unweightedProximity = rhs._unweightedProximity;
        _segmentDistance = rhs._segmentDistance;
        _pairs = rhs._pairs;
        _weight = rhs._weight;
        _significance = rhs._significance;
        _occurrence = rhs._occurrence;
        _weightedOccurrence = rhs._weightedOccurrence;
        _absoluteOccurrence = rhs._absoluteOccurrence;
        _weightedAbsoluteOccurrence = rhs._weightedAbsoluteOccurrence;
        _significantOccurrence = rhs._significantOccurrence;
        _currentSequence = rhs._currentSequence;
        _segmentStarts = rhs._segmentStarts;
        _queryLength = rhs._queryLength;
    }
    return *this;
}

void
Metrics::reset()
{
    _complete = false;
    _outOfOrder = 0;
    _segments = 0;
    _gaps = 0;
    _gapLength = 0;
    _longestSequence = 1;
    _head = -1;
    _tail = -1;
    _matches = 0;
    _proximity = 0;
    _unweightedProximity = 0;
    _segmentDistance = 0;
    _pairs = 0;
    _weight = 0;
    _significance = 0;
    _occurrence = 0;
    _weightedOccurrence = 0;
    _absoluteOccurrence = 0;
    _weightedAbsoluteOccurrence = 0;
    _significantOccurrence = 0;
    _currentSequence = 0;
    _segmentStarts.clear();
    _queryLength = _source->getNumQueryTerms();
}

feature_t
Metrics::getQueryCompleteness() const
{
    return _queryLength > 0 ? (feature_t)_matches / _queryLength : 0.0f;
}

feature_t
Metrics::getFieldCompleteness() const
{
    if (_source->getFieldLength() == 0) {
        return 0; // default
    }
    return (feature_t)_matches / _source->getFieldLength();
}

feature_t
Metrics::getCompleteness() const
{
    feature_t importance = _source->getParams().getFieldCompletenessImportance();
    return getQueryCompleteness() * (1 - importance) + (importance * getFieldCompleteness());
}

feature_t
Metrics::getRelatedness() const
{
    if (_matches == 0) {
        return 0;
    }
    else if (_matches == 1) {
        return 1;
    }
    else {
        return 1 - (feature_t)(_segments - 1) / (_matches - 1);
    }
}

feature_t
Metrics::getSegmentProximity() const
{
    if (_source->getFieldLength() == 0) {
        return 0; // default
    }
    return _matches == 0 ? 0.0f : 1 - (feature_t)_segmentDistance / _source->getFieldLength();
}

feature_t
Metrics::getProximity() const
{
    feature_t totalConnectedness = 0;
    for (uint32_t i = 1; i < _queryLength; i++) {
        totalConnectedness += std::max(0.1, _source->getQueryTermData(i).connectedness());
    }
    feature_t averageConnectedness = 0.1f;
    if (_queryLength > 1) {
        averageConnectedness = totalConnectedness / (_queryLength - 1);
    }
    return getAbsoluteProximity() / averageConnectedness;
}

feature_t
Metrics::getEarliness() const
{
    if (_matches == 0) {
        return 0; // covers (field.length == 0) too
    }
    else if (_source->getFieldLength() == 1) {
        return 1;
    }
    else {
        return 1 - (feature_t)_head / (std::max(6u, _source->getFieldLength()) - 1);
    }
}

feature_t
Metrics::getMatch() const
{
    feature_t proximityCompletenessImportance = _source->getParams().getProximityCompletenessImportance();
    feature_t earlinessImportance = _source->getParams().getEarlinessImportance();
    feature_t relatednessImportance = _source->getParams().getRelatednessImportance();
    feature_t segmentProximityImportance = _source->getParams().getSegmentProximityImportance();
    feature_t occurrenceImportance = _source->getParams().getOccurrenceImportance();

    feature_t scaledRelatedness = 1 - relatednessImportance + relatednessImportance * getRelatedness();

    return
        (proximityCompletenessImportance * scaledRelatedness * getProximity() * getCompleteness()*getCompleteness()
         + earlinessImportance * getEarliness()
         + segmentProximityImportance * getSegmentProximity()
         + occurrenceImportance * getOccurrence()) /
        (proximityCompletenessImportance + earlinessImportance + segmentProximityImportance + occurrenceImportance);
}

feature_t
Metrics::getSegmentationScore() const
{
    feature_t retval = 0.0f;
    if (_segments > 0) {
        retval = getAbsoluteProximity() / (_segments * _segments);
    }
    return retval;
}

void
Metrics::onMatch(uint32_t i)
{
    if (_matches >= _source->getFieldLength()) {
        return;
    }
    _matches++;
    _weight += _source->getTotalTermWeight() > 0 ?
        (feature_t)_source->getQueryTermData(i).termData()->getWeight().percent() / _source->getTotalTermWeight() : 0.0f;
    _significance += _source->getTotalTermSignificance() > 0.0f ?
        _source->getQueryTermData(i).significance() / _source->getTotalTermSignificance() : 0.0f;
}

void
Metrics::onSequenceStart(uint32_t j)
{
    if (_head == -1 || (int)j < _head) {
        _head = j;
    }
    _currentSequence = 1;
}

void
Metrics::onSequenceEnd(uint32_t j)
{
    int sequenceTail = _source->getFieldLength() - j - 1;
    if (_tail == -1 || sequenceTail < _tail) {
        _tail = sequenceTail;
    }
    if (_currentSequence > _longestSequence) {
        _longestSequence = _currentSequence;
    }
    _currentSequence = 0;
}

void
Metrics::onComplete()
{
    if (_segmentStarts.size() <= 1) {
        _segmentDistance = 0;
    }
    else {
        std::sort(_segmentStarts.begin(), _segmentStarts.end());
        for (uint32_t i = 1; i < _segmentStarts.size(); i++) {
            _segmentDistance += _segmentStarts[i] - _segmentStarts[i - 1] + 1;
        }
    }
    if (_head == -1) {
        _head = 0;
    }
    if (_tail == -1) {
        _tail = 0;
    }
}

void
Metrics::onPair(uint32_t i, uint32_t j, uint32_t previousJ)
{
    int distance = j - previousJ - 1;
    if (distance < 0) {
        distance++; // discontinuity if two letters are in the same position
    }
    if (((unsigned int)std::abs(distance)) > _source->getParams().getProximityLimit()) {
        return; // no contribution
    }
    feature_t pairProximity = _source->getParams().getProximityTable()[distance +
                                                                   _source->getParams().getProximityLimit()];
    _unweightedProximity += pairProximity;

    feature_t connectedness = _source->getQueryTermData(i).connectedness();
    _proximity += std::pow(pairProximity, connectedness / 0.1) * std::max(0.1, connectedness);
    _pairs++;
}

void
Metrics::onInSequence(uint32_t, uint32_t, uint32_t)
{
    _currentSequence++;
}

void
Metrics::onInSegmentGap(uint32_t, uint32_t j, uint32_t previousJ)
{
    _gaps++;
    if (j > previousJ) {
        _gapLength += std::abs((int)j - (int)previousJ) - 1; // gap length may be 0 if the gap was in the query
    }
    else {
        _outOfOrder++;
        _gapLength += std::abs((int)j - (int)previousJ);
    }
}

void
Metrics::onNewSegment(uint32_t, uint32_t j, uint32_t)
{
    _segments++;
    _segmentStarts.push_back(j);
}

vespalib::string
Metrics::toString() const
{
    return vespalib::make_string("Metrics(match %f)", getMatch());
}

}

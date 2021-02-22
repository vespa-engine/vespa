// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/feature.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <memory>

namespace search::features::fieldmatch {

class Computer;

/**
 * The collection of metrics calculated by the string match metric calculator.
 *
 * @author Jon Bratseth
 * @author Simon Thoresen Hult
 */
class Metrics {
public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<Metrics> UP;
    typedef std::shared_ptr<Metrics> SP;

public:
    /**
     * Constructs a new metrics object.
     *
     * @param source The source of this.
     */
    Metrics(const Computer *source);

    /**
     * Implements the copy constructor.
     *
     * @param rhs The metrics to copy.
     */
    Metrics(const Metrics &rhs);

    /**
     * Implements the assignment operator.
     */
    Metrics & operator=(const Metrics & rhs);

    /**
     * Resets this object.
     */
    void reset();

    /**
     * Are these metrics representing a complete match.
     *
     * @return Whether or not this represents a complete match.
     */
    bool isComplete() const {
        return _complete;
    }

    /**
     * Sets whether or not these metrics represent a complete match.
     *
     * @param complete Whether or not this represents a complete match.
     * @return This, to allow chaining.
     */
    Metrics &setComplete(bool complete) {
        _complete = complete;
        return *this;
    }

    /**
     * Returns the segment start points.
     *
     * @return The start point list.
     */
    std::vector<uint32_t> &getSegmentStarts() {
        return _segmentStarts;
    }

    /**
     * Returns the total number of out of order token sequences within field segments.
     *
     * @return The number of tokens.
     */
    uint32_t getOutOfOrder() const {
        return _outOfOrder;
    }

    /**
     * Returns the number of field text segments which are needed to match the query as completely as possible.
     *
     * @return The number of segments.
     */
    uint32_t getSegments() const {
        return _segments;
    }

    /**
     * Returns the total number of position jumps (backward or forward) within document segments.
     *
     * @return The number of position jumps.
     */
    uint32_t getGaps() const {
        return _gaps;
    }

    /**
     * Returns the summed size of all gaps within segments.
     *
     * @return The summed size.
     */
    uint32_t getGapLength() const {
        return _gapLength;
    }

    /**
     * Returns the size of the longest matched continuous, in-order sequence in the document.
     *
     * @return The size of the sequence.
     */
    uint32_t getLongestSequence() const {
        return _longestSequence;
    }

    /**
     * Returns the number of tokens in the field preceding the start of the first matched segment.
     *
     * @return The number of tokens.
     */
    int getHead() const {
        return _head;
    }

    /**
     * Returns the number of tokens in the field following the end of the last matched segment.
     *
     * @return The number of tokens.
     */
    int getTail() const {
        return _tail;
    }

    /**
     * Returns the number of query terms which was matched in this field.
     *
     * @return The number of matched terms.
     */
    uint32_t getMatches() const {
        return _matches;
    }

    /**
     * Returns the number of in-segment token pairs.
     *
     * @return The number of token pairs.
     */
    uint32_t getPairs() const {
        return _pairs;
    }

    /**
     * Returns the normalized proximity of the matched terms, weighted by the connectedness of the query terms.  This
     * number is 0.1 if all the matched terms are and have default or lower connectedness, close to 1 if they are
     * following in sequence and have a high connectedness, and close to 0 if they are far from each other in the
     * segment or out of order.
     *
     * @return The proximity.
     */
    feature_t getAbsoluteProximity() const {
        return _pairs < 1 ? 0.1f : _proximity / _pairs;
    }

    /**
     * Returns the normalized proximity of the matched terms, not taking term connectedness into account.  This number
     * is close to 1 if all the matched terms are following each other in sequence, and close to 0 if they are far from
     * each other or out of order
     *
     * @return The proximity.
     */
    feature_t getUnweightedProximity() const {
        return _pairs < 1 ? 1.0f : _unweightedProximity / _pairs;
    }

    /**
     * Returns the sum of the distance between all segments making up a match to the query, measured as the sum of the
     * number of token positions separating the <i>start</i> of each field adjacent segment.
     *
     * @return The sum distance.
     */
    feature_t getSegmentDistance() const {
        return _segmentDistance;
    }

    /**
     * <p>Returns the normalized weight of this match relative to the whole query: The sum of the weights of all
     * <i>matched</i> terms/the sum of the weights of all <i>query</i> terms If all the query terms were matched, this
     * is 1. If no terms were matched, or these matches has weight zero, this is 0.</p>
     *
     * <p>As the sum of this number over all the terms of the query is always 1, sums over all fields of normalized rank
     * features for each field multiplied by this number for the same field will produce a normalized number.</p>
     *
     * <p>Note that this scales with the number of matched query terms in the field. If you want a component which does
     * not, divide by matches.</p>
     *
     * @return The normalized weight.
     */
    feature_t getWeight() const {
        return _weight;
    }

    /**
     * <p>Returns the normalized term significance (1-frequency) of the terms of this match relative to the whole query:
     * The sum of the significance of all <i>matched</i> terms/the sum of the significance of all <i>query</i> terms If
     * all the query terms were matched, this is 1. If no terms were matched, or if the significance of all the matched
     * terms is zero (they are present in all (possible) documents), this number is zero.</p>
     *
     * <p>As the sum of this number over all the terms of the query is always 1, sums over all fields of normalized rank
     * features for each field multiplied by this number for the same field will produce a normalized number.</p>
     *
     * <p>Note that this scales with the number of matched query terms in the field. If you want a component which does
     * not, divide by matches.</p>
     *
     * @return The normalized significance.
     */
    feature_t getSignificance() const {
        return _significance;
    }

    /**
     * <p>Returns a normalized measure of the number of occurrence of the terms of the query.  This number is 1 if there
     * are many occurences of the query terms <i>in absolute terms, or relative to the total content of the field</i>,
     * and 0 if there are none.</p>
     *
     * <p>This is suitable for occurence in fields containing regular text.</p>
     *
     * @return The normalized number of occurences.
     */
    feature_t getOccurrence() const {
        return _occurrence;
    }

    /**
     * <p>Returns a normalized measure of the number of occurrence of the terms of the query:
     *
     * <code>sum over all query terms(min(number of occurences of the term, maxOccurrences)) / (query term count *
     * 100)</code>
     *
     * <p>This number is 1 if there are many occurrences of the query terms, and 0 if there are none.  This number does
     * not take the actual length of the field into account, so it is suitable for uses of occurrence to denote
     * importance across multiple terms.</p>
     *
     * @return The normalized number of occurences.
     */
    feature_t getAbsoluteOccurrence() const {
        return _absoluteOccurrence;
    }

    /**
     * <p>Returns a normalized measure of the number of occurrence of the terms of the query, weighted by term weight.
     * This number is close to 1 if there are many occurrences of highly weighted query terms, in absolute terms, or
     * relative to the total content of the field, and 0 if there are none.</p>
     *
     * @return The normalized measure of weighted occurences.
     */
    feature_t getWeightedOccurrence() const {
        return _weightedOccurrence;
    }

    /**
     * <p>Returns a normalized measure of the number of occurrence of the terms of the query, taking weights into
     * account so that occurrences of higher weighted query terms has more impact than lower weighted terms.</p>
     *
     * <p>This number is 1 if there are many occurrences of the highly weighted terms, and 0 if there are none.  This
     * number does not take the actual length of the field into account, so it is suitable for uses of occurrence to
     * denote importance across multiple terms.</p>
     *
     * @return The normalized measure of weighted occurences.
     */
    feature_t getWeightedAbsoluteOccurrence() const {
        return _weightedAbsoluteOccurrence;
    }

    /**
     * <p>Returns a normalized measure of the number of occurrence of the terms of the query <i>in absolute terms, or
     * relative to the total content of the field</i>, weighted by term significance.
     *
     * <p>This number is 1 if there are many occurrences of the highly significant terms, and 0 if there are none.</p>
     *
     * @return The normalized measure of occurences, weighted by significance.
     */
    feature_t getSignificantOccurrence() const {
        return _significantOccurrence;
    }

    /**
     * The ratio of query tokens which was matched in the field: <code>matches/queryLength</code>.
     *
     * @return The query completeness.
     */
    feature_t getQueryCompleteness() const;

    /**
     * The ratio of query tokens which was matched in the field: <code>matches/fieldLength</code>.
     *
     * @return The field completeness.
     */
    feature_t getFieldCompleteness() const;

    /**
     * Total completeness, where field completeness is more important: <code>queryCompleteness * ( 1 -
     * fieldCompletenessImportancy + fieldCompletenessImportancy * fieldCompleteness )</code>
     *
     * @return The total completeness.
     */
    feature_t getCompleteness() const;

    /**
     * Returns how well the order of the terms agreed in segments: <code>1-outOfOrder/pairs</code>.
     *
     * @return The orderness of terms.
     */
    feature_t getOrderness() const {
        return _pairs < 1 ? 1.0f : 1 - (feature_t)_outOfOrder / _pairs;
    }

    /**
     * Returns the degree to which different terms are related (occurring in the same segment):
     * <code>1-segments/(matches-1)</code>.
     *
     * @return The relatedness of terms.
     */
    feature_t getRelatedness() const;

    /**
     * Returns <code>longestSequence/matches</code>
     *
     * @return The longest sequence ratio.
     */
    feature_t getLongestSequenceRatio() const {
        return _matches == 0 ? 0.0f : (feature_t)_longestSequence / _matches;
    }

    /**
     * Returns the closeness of the segments in the field: <code>1-segmentDistance/fieldLength</code>.
     *
     * @return The segment proximity.
     */
    feature_t getSegmentProximity() const;

    /**
     * Returns a value which is close to 1 when matched terms are close and close to zero when they are far apart in the
     * segment. Relatively more connected terms influence this value more.  This is absoluteProximity/average
     * connectedness.
     *
     * @return The matched term proximity.
     */
    feature_t getProximity() const;

    /**
     * <p>Returns the average of significance and weight.</p>
     *
     * <p>As the sum of this number over all the terms of the query is always 1, sums over all fields of normalized rank
     * features for each field multiplied by this number for the same field will produce a normalized number.</p>
     *
     * <p>Note that this scales with the number of matched query terms in the field. If you want a component which does
     * not, divide by matches.</p>
     *
     * @return The importance.
     */
    feature_t getImportance() const {
        return (getSignificance() + getWeight()) / 2;
    }

    /**
     * A normalized measure of how early the first segment occurs in this field:
     * <code>1-(head+1)/max(6,field.length)</code>.
     *
     * @return The earliness of the first segment.
     */
    feature_t getEarliness() const;

    /**
     * <p>A ready-to-use aggregate match score. Use this if you don't have time to find a better application specific
     * aggregate score of the fine grained match metrics.</p>
     *
     * <p>The current forumla is
     *
     * <code> ( proximityCompletenessImportance * (1-relatednessImportance + relatednessImportance*relatedness)
     * proximity * completeness^2 + earlinessImportance * earliness + segmentProximityImportance * segmentProximity ) /
     * (proximityCompletenessImportance + earlinessImportance + relatednessImportance)</code>
     *
     * but this is subject to change (i.e improvement) at any time.  </p>
     *
     * <p>Weight and significance are not taken into account because this is mean to capture tha quality of the match in
     * this field, while those measures relate this match to matches in other fields. This number can be multiplied with
     * those values when combining with other field match scores.</p>
     *
     * @return The match score.
     */
    feature_t getMatch() const;

    /**
     * <p>The metric use to select the best segments during execution of the string match metric algoritm.</p>
     *
     * <p>This metric, and any metric it dependends on, must be correct each time a segment is completed, not only when
     * the metrics are complete, because this metric is used to choose segments during calculation.</p>
     *
     * @return The score of the segmentation.
     */
    feature_t getSegmentationScore() const;

    /**
     * Called once for every match.
     *
     * @param i The index of the matched query term.
     */
    void onMatch(uint32_t i);


    /**
     * Called once per sequence, when the sequence starts.
     *
     * @param j Sequence starts at this position.
     */
    void onSequenceStart(uint32_t j);

    /**
     * Called once per sequence when the sequence ends.
     *
     * @param j Sequence ends at this position.
     */
    void onSequenceEnd(uint32_t j) ;

    /**
     * Called once when this value is calculated, before onComplete.
     *
     * @param occurence The new occurence value.
     */
    void setOccurrence(feature_t occurrence) {
        _occurrence = occurrence;
    }

    /**
     * Called once when this value is calculated, before onComplete.
     *
     * @param weightedOccurence The new occurence weight.
     */
    void setWeightedOccurrence(feature_t weightedOccurrence) {
        _weightedOccurrence = weightedOccurrence;
    }

    /**
     * Called once when this value is calculated, before onComplete.
     *
     * @param absoluteOccurence The new absolute occurence value.
     */
    void setAbsoluteOccurrence(feature_t absoluteOccurrence) {
        _absoluteOccurrence = absoluteOccurrence;
    }

    /**
     * Called once when this value is calculated, before onComplete.
     *
     * @param weightedAbsoluteOccurence The new absolute occurence weight.
     */
    void setWeightedAbsoluteOccurrence(feature_t weightedAbsoluteOccurrence) {
        _weightedAbsoluteOccurrence = weightedAbsoluteOccurrence;
    }

    /**
     * Called once when this value is calculated, before onComplete.
     *
     * @param significantOccurence The new significant occurence value.
     */
    void setSignificantOccurrence(feature_t significantOccurrence) {
        _significantOccurrence = significantOccurrence;
    }

    /**
     * Called once when matching is complete.
     */
    void onComplete();

    /**
     * Called when <i>any</i> pair is encountered.
     *
     * @param i         The query term matched.
     * @param j         The field term index.
     * @param previousJ The end of the previous segment, or -1 if this is the first segment.
     */
    void onPair(uint32_t i, uint32_t j, uint32_t previousJ);

    /**
     * Called when an in-sequence pair is encountered.
     *
     * @param i         The query term matched.
     * @param j         The field term index.
     * @param previousJ The end of the previous segment, or -1 if this is the first segment.
     */
    void onInSequence(uint32_t i, uint32_t j, uint32_t previousJ);

    /**
     * Called when a gap (within a sequence) is encountered.
     *
     * @param i         The query term matched.
     * @param j         The field term index.
     * @param previousJ The end of the previous segment, or -1 if this is the first segment.
     */
    void onInSegmentGap(uint32_t i, uint32_t j, uint32_t previousJ);

    /**
     * Called when a new segment is started
     *
     * @param i         The query term matched.
     * @param j         The field term index.
     * @param previousJ The end of the previous segment, or -1 if this is the first segment.
     * */
    void onNewSegment(uint32_t i, uint32_t j, uint32_t previousJ);

    /**
     * Returns a string representation of this.
     *
     * @return A string representation.
     */
    vespalib::string toString() const;

private:
    const Computer *_source;
    bool            _complete;

    // Metrics
    uint32_t  _outOfOrder;
    uint32_t  _segments;
    uint32_t  _gaps;
    uint32_t  _gapLength;
    uint32_t  _longestSequence;
    int       _head;
    int       _tail;
    uint32_t  _matches;
    feature_t _proximity;
    feature_t _unweightedProximity;
    feature_t _segmentDistance;
    uint32_t  _pairs;
    feature_t _weight;
    feature_t _significance;
    feature_t _occurrence;
    feature_t _weightedOccurrence;
    feature_t _absoluteOccurrence;
    feature_t _weightedAbsoluteOccurrence;
    feature_t _significantOccurrence;

    // Temporary variables
    uint32_t              _currentSequence;
    std::vector<uint32_t> _segmentStarts;
    uint32_t              _queryLength; // num terms searching this field
};

}

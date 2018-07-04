// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/feature.h>
#include <vector>
#include <cstdint>

namespace search {
namespace features {
namespace fieldmatch {

/**
 * The parameters to a string match metric calculator.
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon Bratseth</a>
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class Params {
public:
    /**
     * Creates a marcg metrics object initialized to the default values.
     */
    Params();

    /**
     * Returns whether or not this parameter object contains valid content. If it is NOT valid, a descriptive string
     * will be logged for reference.
     *
     * @return Whether or not this object is valid.
     */
    bool valid();

    /**
     * Sets the number of tokens within which proximity matters. Default: 10
     *
     * @param proximityLimit The number of tokens.
     * @param This, to allow chaining.
     */
    Params &setProximityLimit(uint32_t proximityLimit) {
        _proximityLimit = proximityLimit;
        return *this;
    }

    /**
     * Returns the number of tokens within which proximity matters. Default: 10
     *
     * @return The number of tokens.
     */
    uint32_t getProximityLimit() const {
        return _proximityLimit;
    }

    /**
     * Sets the proximity table deciding the importance of separations of various distances, The table must have size
     * proximityLimit*2+1, where the first half is for reverse direction distances. The table must only contain values
     * between 0 and 1, where 1 is "perfect" and 0 is "worst".
     *
     * @param proximityTable The proximity table.
     * @return This, to allow chaining.
     */
    Params &setProximityTable(const std::vector<feature_t> &proximityTable) {
        _proximityTable = proximityTable;
        return *this;
    }

    /**
     * Returns the current proxmity table.  The default table is calculated by <code>1/2^(n/2)</code> on the right order
     * side, and <code>1/2^(n/2) /3</code> on the reverse order side where n is the distance between the tokens.
     *
     * @return The proximity table.
     */
    const std::vector<feature_t> &getProximityTable() const {
        return _proximityTable;
    }

    /**
     * Returns the maximal number of <i>alternative</i> segmentations allowed in addition to the first one found.
     * Default is 10000. This will prefer to not consider iterations on segments that are far out in the field, and
     * which starts late in the query.
     *
     * @return The max number of alternative iterations.
     */
    uint32_t getMaxAlternativeSegmentations() const {
        return _maxAlternativeSegmentations;
    }

    /**
     * Sets the maximal number of alternative segmentations allowed in addition to the first one found.
     *
     * @param maxAlternativeSegmentations The max number of alternative iterations.
     * @return This, to allow chaining.
     */
    Params &setMaxAlternativeSegmentations(uint32_t maxAlternativeSegmentations) {
        _maxAlternativeSegmentations = maxAlternativeSegmentations;
        return *this;
    }

    /**
     * Returns the number of occurrences each word is normalized against.  This should be set as the number above which
     * additional occurrences of the term has no real significance.  The default is 100.
     *
     * @return The max number of occurences.
     */
    uint32_t getMaxOccurrences() const {
        return _maxOccurrences;
    }

    /**
     * Sets the number occurences each word is normalized against.
     *
     * @params maxOccurences The max number of occurences.
     * @return This, to allow chaining.
     */
    Params &setMaxOccurrences(uint32_t maxOccurrences) {
        _maxOccurrences = maxOccurrences;
        return *this;
    }

    /**
     * Returns a number between 0 and 1 which determines the importance of field completeness in relation to query
     * completeness in the <code>match</code> and <code>completeness</code> metrics. Default is 0.05
     *
     * @return The importance of field completeness.
     */
    feature_t getFieldCompletenessImportance() const {
        return _fieldCompletenessImportance;
    }

    /**
     * Sets the importance of this field's completeness.
     *
     * @param fieldCompletenessImportance The importance of field completeness.
     * @return This, to allow chaining.
     */
    Params &setFieldCompletenessImportance(feature_t fieldCompletenessImportance) {
        _fieldCompletenessImportance = fieldCompletenessImportance;
        return *this;
    }

    /**
     * Returns the importance of the match having high proximity and being complete, relative to
     * segmentProximityImportance, occurrenceImportance and earlinessImportance in the <code>match</code>
     * metric. Default: 0.9
     *
     * @return The importance of proximity AND completeness.
     */
    feature_t getProximityCompletenessImportance() const {
        return _proximityCompletenessImportance;
    }

    /**
     * Sets the importance of this fiel's proximity AND completeness.
     *
     * @param proximityCompletenessImportance The importance of proximity AND completeness.
     * @return This, to allow chaining.
     */
    Params &setProximityCompletenessImportance(feature_t proximityCompletenessImportance) {
        _proximityCompletenessImportance = proximityCompletenessImportance;
        return *this;
    }

    /**
     * Returns the importance of the match occuring early in the query, relative to segmentProximityImportance,
     * occurrenceImportance and proximityCompletenessImportance in the <code>match</code> metric. Default: 0.05
     *
     * @return The importance of earliness.
     */
    feature_t getEarlinessImportance() const {
        return _earlinessImportance;
    }

    /**
     * Sets the importance of the match occuring early in the query.
     *
     * @param earlinessImportance The importance of earliness.
     * @return This, to allow chaining.
     */
    Params &setEarlinessImportance(feature_t earlinessImportance) {
        _earlinessImportance = earlinessImportance;
        return *this;
    }

    /**
     * Returns the importance of multiple segments being close to each other, relative to earlinessImportance,
     * occurrenceImportance and proximityCompletenessImportance in the <code>match</code> metric. Default: 0.05
     *
     * @return The importance of segment proximity.
     */
    feature_t getSegmentProximityImportance() const {
        return _segmentProximityImportance;
    }

    /**
     * Sets the importance of multiple segments being close to each other.
     *
     * @param segmentProximityImportance The importance of segment proximity.
     * @return This, to allow chaining.
     */
    Params &setSegmentProximityImportance(feature_t segmentProximityImportance) {
        _segmentProximityImportance = segmentProximityImportance;
        return *this;
    }

    /**
     * Returns the importance of having many occurrences of the query terms, relative to earlinessImportance,
     * segmentProximityImportance and proximityCompletenessImportance in the <code>match</code> metric. Default: 0.05
     *
     * @return The importance of many occurences.
     */
    feature_t getOccurrenceImportance() const {
        return _occurrenceImportance;
    }

    /**
     * Sets the importance of having many occurences of the query terms.
     *
     * @param occurenceImportance The importance of many occurences.
     * @return This, to allow chaining.
     */
    Params &setOccurrenceImportance(feature_t occurrenceImportance) {
        _occurrenceImportance = occurrenceImportance;
        return *this;
    }

    /**
     * Returns the normalized importance of relatedness used in the <code>match</code> metric. Default: 0.9
     *
     * @return The importance of relatedness.
     */
    feature_t getRelatednessImportance() const {
        return _relatednessImportance;
    }

    /**
     * Sets the normalized importance of relatedness used in the <code>match</code> metric.
     *
     * @param relatednessImportance The importance of relatedness.
     * @return This, to allow chaining.
     */
    Params &setRelatednessImportance(feature_t relatednessImportance) {
        _relatednessImportance = relatednessImportance;
        return *this;
    }

private:
    uint32_t  _proximityLimit;
    uint32_t  _maxAlternativeSegmentations;
    uint32_t  _maxOccurrences;
    feature_t _proximityCompletenessImportance;
    feature_t _relatednessImportance;
    feature_t _earlinessImportance;
    feature_t _segmentProximityImportance;
    feature_t _occurrenceImportance;
    feature_t _fieldCompletenessImportance;
    std::vector<feature_t> _proximityTable;
};

} // fieldmatch
} // features
} // search


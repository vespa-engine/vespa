// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/feature.h>
#include <vespa/vespalib/stllike/string.h>
#include "params.h"

namespace search::features::fieldmatch {

/**
 * The collection of simple metrics calculated when traversing the query terms of the query environment.
 **/
class SimpleMetrics {
private:
    const Params & _params;
    uint32_t _matches;
    uint32_t _matchesWithPosOcc;
    bool     _matchWithInvalidFieldLength; // 0 or UNKNOWN_LENGTH
    uint32_t _numTerms;
    uint32_t _matchedWeight;
    uint32_t _totalWeightInField;
    uint32_t _totalWeightInQuery;

public:
    /**
     * Constructs a new object.
     **/
    SimpleMetrics(const Params & params);

    /**
     * Resets the match data of this object.
     **/
    void resetMatchData() {
        _matches = 0;
        _matchesWithPosOcc = 0;
        _matchWithInvalidFieldLength = false;
        _matchedWeight = 0;
    }

    /**
     * Registers a match in the field in question.
     *
     * @param weight The weight of the term matching.
     **/
    void addMatch(uint32_t weight) {
        ++_matches;
        _matchedWeight += weight;
    }

    /**
     * Registers a match in the field in question.
     * We have position information for this term match.
     *
     * @param weight The weight of the term matching.
     **/
    void addMatchWithPosOcc(uint32_t weight) {
        addMatch(weight);
        ++_matchesWithPosOcc;
    }

    /**
     * Registers that a match has invalid field length.
     **/
    void hasMatchWithInvalidFieldLength() {
        _matchWithInvalidFieldLength = true;
    }


    /**
     * Registers a term that is searching in the field in question.
     *
     * @param weight The weight of the term.
     **/
    void addSearchedTerm(uint32_t weight) {
        ++_numTerms;
        _totalWeightInField += weight;
    }

    /**
     * Registers a query term with the given weight.
     *
     * @param weight The weight of the term.
     **/
    void addQueryTerm(uint32_t weight) {
        _totalWeightInQuery += weight;
    }

    /**
     * Overrides the total weight for all query terms.
     *
     * @param weight The total weight.
     **/
    void setTotalWeightInQuery(uint32_t weight) {
        _totalWeightInQuery = weight;
    }

    /**
     * Returns the normalized score for this object.
     * <code> total weight of matched terms in the field / total weight of searched terms in the field </code>
     *
     * @return The score.
     **/
    feature_t getScore() const {
        return _totalWeightInField > 0 ? _matchedWeight / static_cast<feature_t>(_totalWeightInField) : 0;
    }

    /**
     * Returns the completeness score for this object.
     * <code> <code>queryCompleteness * ( 1 - fieldCompletenessImportance ) </code>
     *
     * @return The completeness.
     **/
    feature_t getCompleteness() const {
        return getQueryCompleteness() * (1 - _params.getFieldCompletenessImportance());
    }

    /**
     * Returns the query completeness score for this object.
     * <code> total number of matched terms in the field / total number of searched terms in the field </code>
     *
     * @return The query completeness.
     **/
    feature_t getQueryCompleteness() const {
        return _numTerms > 0 ? _matches / static_cast<feature_t>(_numTerms) : 0;
    }

    /**
     * Returns the weight score for this object.
     * <code> total weight of matched terms in the field / total weight of all query terms </code>
     *
     * @return The weight.
     **/
    feature_t getWeight() const {
        return _totalWeightInQuery > 0 ? _matchedWeight / static_cast<feature_t>(_totalWeightInQuery) : 0;
    }

    /**
     * Returns the number of matches in the field in question.
     *
     * @return The number of matches.
     **/
    uint32_t getMatches() const {
        return _matches;
    }

    /**
     * Returns the number of matches in the field in question with position information.
     *
     * @return The number of matches with position information.
     **/
    uint32_t getMatchesWithPosOcc() const {
        return _matchesWithPosOcc;
    }

    /**
     * Returns the number of degraded matches (no position information) in the field in question.
     *
     * @return The number of degraded matches.
     **/
    uint32_t getDegradedMatches() const {
        return getMatches() - getMatchesWithPosOcc();
    }

    /**
     * Returns whether we have a match in the field in question with invalid field length.
     *
     * @return Whether we have seen an invalid field length.
     **/
    bool getMatchWithInvalidFieldLength() const {
        return _matchWithInvalidFieldLength;
    }

    /**
     * Returns a string representation of this object.
     *
     * @return String representation.
     **/
    vespalib::string toString() const;
};

}

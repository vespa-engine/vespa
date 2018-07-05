// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/phrasesplitter.h>
#include <vespa/searchlib/features/queryterm.h>
#include <vespa/searchlib/common/allocatedbitvector.h>
#include <string>
#include <vector>
#include "metrics.h"
#include "params.h"
#include "segmentstart.h"
#include "simplemetrics.h"

namespace search {
namespace features {
namespace fieldmatch {

/**
 * <p>Calculates a set of metrics capturing information about the degree of agreement between a query and a field
 * string. This algorithm attempts to capture the property of text that very close tokens are usuall part of the same
 * semantic structure, while tokens farther apart are much more loosely related.  The algorithm will locate alternative
 * such regions containing multiple query tokens (segments), do a more detailed analysis of these segments and choose
 * the ones producing the best overall set of match metrics.</p>
 *
 * <p>Such segments are found by looking at query terms in sequence from left top right and finding matches in the
 * field. All alternative segment start points are explored, and the segmentation achieving the best overall string
 * match metric score is preferred. The dynamic programming paradigm is used to avoid redoing work on segmentations.</p>
 *
 * <p>When a segment start point is found, subsequenc tokens from the query are searched in the field from this starting
 * point in "semantic order". This search order can be defined independently of the algorithm. The current order
 * searches <i>proximityLimit tokens ahead first, then the same distance backwards (so if you need to go two steps
 * backwards in the field from the segment starting point, the real distance is -2, but the "semantic distance" is
 * proximityLimit+2.</p>
 *
 * <p>The actual metrics are calculated during execution of this algorithm by the {@link Metrics} class, by
 * receiving events emitted from the algorithm. Any set of metrics derivable from these events a computable using this
 * algorithm.</p>
 *
 * <p>Terminology:
 * <ul>
 * <li><b>Sequence</b> - A set of adjacent matched tokens in the field.</li>
 * <li><b>Segment</b> - A field area containing matches to a continuous section of the query.</li>
 * <li><b>Gap</b> - A chunk of adjacent tokens <i>inside a segment</i> separating two matched characters.</li>
 * <li><b>Semantic distance</b> - A non-continuous distance between tokens in j.</li>
 * </ul>
 *
 * <p>Notation: A position index in the query is denoted <code>i</code>. A position index in the field is denoted
 * <code>j</code>.</p>
 *
 * <p>This class is not multithread safe, but is reusable across queries for a single thread.</p>
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon Bratseth</a>
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class Computer {
public:
    /**
     * Constructs a new computer object.
     *
     * @param propertyNamespace The namespace used in query properties.
     * @param splitter          The environment that holds all query information.
     * @param fieldInfo         The info object of the matched field.
     * @param params            The parameter object for this computer.
     */
    Computer(const vespalib::string &propertyNamespace, const search::fef::PhraseSplitter &splitter,
             const search::fef::FieldInfo &fieldInfo, const Params &params);

    /**
     * Resets this object according to the given document id
     *
     * @param docid The local document id to be evaluated
     */
    void reset(uint32_t docId);

    /**
     * Runs this computer using the environment, match and parameters given to the constructor.
     *
     * @return The final metrics.
     */
    const Metrics & run();

    /**
     * Returns the final metrics.
     *
     * @return The final metrics.
     */
    const Metrics & getFinalMetrics() const {
        return _finalMetrics;
    }

    /**
     * Implements the prefered search order for finding a match to a query item - first
     * looking close in the right order, then close in the reverse order, then far in the right order
     * and lastly far in the reverse order.
     *
     * @param i                     The query term index.
     * @param previousJ             The previous field index.
     * @param startSemanticDistance The semantic distance we must be larger than or equal to.
     * @return The semantic distance of the next mathing j larger than startSemanticDistance, or -1 if
     *         there are no matches larger than startSemanticDistance
     */
    int findClosestInFieldBySemanticDistance(int i, int previousJ, uint32_t startSemanticDistance);

    /**
     * Returns the field index (j) from a starting point zeroJ and the distance form zeroJ in the
     * semantic distance space.
     *
     * @param semanticDistance The semantic distance to transform to field index.
     * @param zeroJ            The starting point.
     * @returns The field index, or -1 (undefined) if the semanticDistance is -1.
     */
    int semanticDistanceToFieldIndex(int semanticDistance, uint32_t zeroJ) const;

    /**
     * Returns the semantic distance from a starting point zeroJ to a field index j.
     *
     * @param j     The field index to transform to semantic distance.
     * @param zeroJ The starting point.
     * @returns The semantic distance, or -1 (undefined) if j is -1.
     */
    int fieldIndexToSemanticDistance(int j, uint32_t zeroJ) const;

    /**
     * Returns the query environment of this. This contains information about the query.
     *
     * @return The query environment.
     */
    const search::fef::IQueryEnvironment &getQueryEnvironment() const {
        return _splitter;
    }

    /**
     * Returns the id of the searched field.
     *
     * @return The field id.
     */
    uint32_t getFieldId() const {
        return _fieldId;
    }

    /**
     * Returns the number of terms present in the searched field.
     *
     * @return The field length.
     */
    uint32_t getFieldLength() const {
        return _fieldLength;
    }

    /**
     * Returns the parameter object that was used to instantiate this.
     *
     * @return The parameters.
     */
    const Params &getParams() const {
        return _params;
    }

    /**
     * Adds the given string to the trace of this, if tracing is enabled.
     *
     * @param str The string to trace.
     * @return This, to allow chaining.
     */
    Computer &trace(const vespalib::string &str);

    /**
     * Returns a textual trace of the last execution of this algorithm, if tracing is on.
     *
     * @return The trace string.
     */
    vespalib::string getTrace() const;

    /**
     * Set to true to collect a textual trace from the computation, which can be retrieved using {@link #getTrace}.
     *
     * @param tracing Whether or not to trace.
     * @return This, to allow chaining.
     */
    Computer &setTracing(bool tracing) {
        _tracing = tracing;
        return *this;
    }

    /**
     * Returns whether tracing is on.
     *
     * @return True if tracing is on.
     */
    bool isTracing() const { return _tracing; }

    /**
     * Returns the number of terms searching on this field.
     *
     * @return The number of terms.
     */
    uint32_t getNumQueryTerms() const {
        return _queryTerms.size();
    }

    /**
     * Returns the query term data for a specified term.
     *
     * @param  The index of the term to return.
     * @return The query term data.
     */
    const QueryTerm & getQueryTermData(int term) const {
        return _queryTerms[term];
    }

    /**
     * Returns the term match for a specified term.
     *
     * @param  The index of the term match to return.
     * @return The term match.
     */
    const search::fef::TermFieldMatchData *getQueryTermFieldMatch(int term) const {
        return _queryTermFieldMatch[term];
    }

    /**
     * Returns the total weight of all query terms.
     *
     * @return The total weight.
     */
    uint32_t getTotalTermWeight() const {
        return _totalTermWeight;
    }

    /**
     * Returns the total significance of all query terms.
     *
     * @return The total significance.
     */
    feature_t getTotalTermSignificance() const {
        return _totalTermSignificance;
    }

    /**
     * Returns a string representation of this computer.
     *
     * @return A string representation.
     */
    vespalib::string toString() const;

    /**
     * Returns the simple metrics computed while traversing the list of query terms in the constructor.
     *
     * @return the simple metrics object.
     */
    const SimpleMetrics & getSimpleMetrics() const {
        return _simpleMetrics;
    }


private:
    /**
     * Finds segment candidates and explores them until we have the best segmentation history of the entire query.
     */
    void exploreSegments();

    /**
     * Find correspondences from a segment starting point startI.
     *
     * @param segment The segment starting point.
     * @return True if a segment was found, false if none could be found.
     */
    bool findAlternativeSegmentFrom(SegmentStart *segment);

    /**
     * A match occured within a segment, report this to the metric as appropriate.
     *
     * @param i         The current query term index.
     * @param j         The current field term index.
     * @param previousJ The previous field term index.
     * @param previousI The previous query term index.
     */
    void inSegment(int i, int j, int previousJ, int previousI);

    /**
     * Returns whether this segment was accepted as a starting point.
     *
     * @param i         The current query term index.
     * @param j         The current field term index.
     * @param previousJ The previous field term index.
     * @return Whether this segment was accepted or not.
     */
    bool segmentStart(int i, int j, int previousJ);

    /**
     * Registers an end of a segment.
     *
     * @param i The i at which this segment ends.
     * @param j The j at which this segment ends.
     */
    void segmentEnd(int i, int j);

    /**
     * Returns the next open segment to explore, or null if no more segments exists or should be explored.
     *
     * @param The i to start searching from.
     * @return The next open segment, or null.
     */
    SegmentStart *findOpenSegment(uint32_t startI);

    /**
     * Returns the last segment start point in the internal list.
     *
     * @return The last segment start.
     */
    SegmentStart *findLastStartPoint();

    /**
     * Counts all occurrences of terms of the query in the field and set those metrics.
     *
     * @param metrics The metrics to update.
     */
    void setOccurrenceCounts(Metrics &metrics);

    void handleError(uint32_t fieldPos, uint32_t docId) const __attribute__((noinline));


private:
    typedef std::shared_ptr<search::BitVector> BitVectorPtr;
    typedef std::vector<const search::fef::TermFieldMatchData *> TermFieldMatchDataVector;

    struct SegmentData {
        SegmentData() : segment(), valid(false) {}
        SegmentData(const SegmentStart::SP & ss, bool v = false) : segment(ss), valid(v) {}
        SegmentStart::SP segment;
        bool valid;
    };

    struct BitVectorData {
        BitVectorData() : bitvector(0), valid(false) {}
        search::AllocatedBitVector bitvector;
        bool valid;
    };

    // per query
    const search::fef::PhraseSplitter        & _splitter;
    uint32_t                                   _fieldId;
    Params                                     _params;
    bool                                       _tracing;
    std::vector<vespalib::string>              _trace;
    bool                                       _useCachedHits;

    QueryTermVector                            _queryTerms;
    TermFieldMatchDataVector                   _queryTermFieldMatch;
    uint32_t                                   _totalTermWeight;
    feature_t                                  _totalTermSignificance;

    // per docid
    uint32_t                                   _fieldLength;
    Metrics                                    _currentMetrics; // The metrics of the currently explored segmentation.
    Metrics                                    _finalMetrics;   // The final metrics, null during and before metric computation.
    SimpleMetrics                              _simpleMetrics;  // The metrics used to compute simple features.
    std::vector<SegmentData>                   _segments;       // Known segment starting points.
    uint32_t                                   _alternativeSegmentationsTried;
    std::vector<BitVectorData>                 _cachedHits;
};

} // fieldmatch
} // features
} // search


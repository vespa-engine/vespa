// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <limits>
#include "metrics.h"

namespace search::features::fieldmatch {

/**
 * <p>Information on segment start points stored temporarily during string match metric calculation.</p>
 *
 * <p>Given that we want to start a segment at i, this holdes the best known metrics up to i and the end of the previous
 * segment. In addition it holds information on how far we have tried to look for alternative segments from this
 * starting point (skipI and previousJ).</p>
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon Bratseth</a>
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class SegmentStart {
public:
    /**
     * Convenience typedefs.
     */
    using UP = std::unique_ptr<SegmentStart>;
    using SP = std::shared_ptr<SegmentStart>;

public:
    /**
     * Creates a segment start point for any i position where the j is not known.
     *
     * @param owner     The computar that pwns th1s.
     * @param metrics   The best known metric.
     * @param previousJ The previous j.
     * @param i         The start position.
     * @param j         The end position.
     */
    SegmentStart(Computer *owner, const Metrics & metrics,
                 uint32_t previousJ = 0, uint32_t i = 0,
                 uint32_t j = std::numeric_limits<uint32_t>::max());

    /**
     * Resets this object.
     *
     * @param metrics   The best known metric.
     * @param previousJ The previous j.
     * @param i         The start position.
     * @param j         The end position.
     */
    void reset(const Metrics & metrics, uint32_t previousJ = 0, uint32_t i = 0,
               uint32_t j = std::numeric_limits<uint32_t>::max());

    /**
     * Returns the current best metrics for this starting point
     *
     * @return The metrics.
     */
    const Metrics & getMetrics() const {
        return _metrics;
    }

    /**
     * Stores that we have explored to a certain j from the current previousJ.
     *
     * @param j The new position we have explored to.
     * @return This, to allow chaining.
     */
    SegmentStart &exploredTo(uint32_t j);

    /**
     * Offers an alternative history leading up to this point, which is accepted and stored if it is better than the
     * current history
     *
     * @param previousJ The previous j offered.
     * @param metrics The offered metrics.
     * @return Whether or not the new history was accepted.
     */
    bool offerHistory(int previousJ, const Metrics & metrics);

    /**
     * Returns whether there are still unexplored j's for this i.
     *
     * @return Whether or not there are unexplored j's.
     */
    bool isOpen() const {
        return _open;
    }

    /**
     * Sets whether there are still unexplored j's for this i.
     *
     * @param open Whehter or not there are unexplored j's.
     * @return This, to allow chaining.
     */
    SegmentStart &setOpen(bool open) {
        _open = open;
        return *this;
    }

    /**
     * Returns the i for which this is the possible segment starting points.
     *
     * @return The i value.
     */
    uint32_t getI() const {
        return _i;
    }

    /**
     * Returns the j ending the previous segmentation producing those best metrics.
     *
     * @return The previous j value.
     */
    uint32_t getPreviousJ() const {
        return _previousJ;
    }

    /**
     * Returns the semantic distance from the previous j which is explored so far, exclusive
     * (meaning, if the value is 0, 0 is <i>not</i> explored yet)
     *
     * @return The distance explored.
     */
    uint32_t getSemanticDistanceExplored() const {
        return _semanticDistanceExplored;
    }

    /**
     * Sets the semantic distance from the previous j which is explored so far, exclusive.
     *
     * @param distance The distance explored.
     * @return This, to allow chaining.
     */
    SegmentStart &setSemanticDistanceExplored(uint32_t distance) {
        _semanticDistanceExplored = distance;
        return *this;
    }

    /**
     * Returns the position startI we should start at from this start point i.  startI==i except when there are i's from
     * this starting point which are not found anywhere in the field. In that case, startI==i+the number of terms
     * following i which are known not to be present.
     *
     * @return The start i value.
     */
    uint32_t getStartI() const {
        return _i + _skipI;
    }

    /**
     * Increments the startI by one because we have discovered that the term at the current startI is not present in the
     * field.
     *
     * @return This, to allow chaining.
     */
    SegmentStart &incrementStartI() {
        _skipI++;
        return *this;
    }

    /**
     * Returns a string representation of this.
     *
     * @return A string representation.
     */
    vespalib::string toString();

private:
    Computer   *_owner;
    Metrics    _metrics;                  // The best known metrics up to this starting point.

    uint32_t    _i;                        // The i for which this is the possible segment starting points.
    uint32_t    _skipI;
    uint32_t    _previousJ;                // The j ending the previous segmentation producing those best metrics.
    uint32_t    _semanticDistanceExplored; // The semantic distance from the current previousJ which is already explored.
    bool        _open;                     // There are possibly more j's to try at this starting point.
};

}

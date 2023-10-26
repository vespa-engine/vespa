// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryterm.h"
#include <cstdint>

namespace search::fef {
    class TermFieldMatchData;
    class MatchData;
}

namespace search::features {

/**
 * This class is used to calculate the minimal forward and reverse term distance
 * between two terms matching in the same field using the position information for both terms.
 *
 * The terms 'a' and 'b' matching the field 'a b x a' will give the following result:
 *   - forwardDist = 1
 *   - forwardTermPos = 0
 *   - reverseDist = 2
 *   - reverseTermPos = 1
 *
 * Note that if we have a phrase 'a b' and term 'c' matching the field 'a b x c' we will get:
 *   - forwardDist = 2 (between b and c)
 *   - forwardTermPos = 0 (pos of first word)
 **/
class TermDistanceCalculator {
public:
    /**
     * Represents an undefined value.
     **/
    static const uint32_t UNDEFINED_VALUE;

    /**
     * Contains the result from running the calculator.
     **/
    struct Result {
        uint32_t forwardDist;    // min distance between term X and term Y in the field
        uint32_t forwardTermPos; // the position of term X for that distance
        uint32_t reverseDist;    // min distance between term Y and term X in the field
        uint32_t reverseTermPos; // the position of term Y for that distance

        /**
         * Creates a new object with undefined values.
         **/
        Result() { reset(); }

        /**
         * Creates a new object with the given values.
         **/
        Result(uint32_t fd, uint32_t ftp, uint32_t rd, uint32_t rtp) :
            forwardDist(fd), forwardTermPos(ftp), reverseDist(rd), reverseTermPos(rtp) {}

        /**
         * Sets all variables to the undefined value.
         **/
        void reset() {
            forwardDist = UNDEFINED_VALUE;
            forwardTermPos = UNDEFINED_VALUE;
            reverseDist = UNDEFINED_VALUE;
            reverseTermPos = UNDEFINED_VALUE;
        }
    };

private:
    static void findBest(const fef::TermFieldMatchData *tmdX,
                         const fef::TermFieldMatchData *tmdY,
                         uint32_t numTermsX,
                         uint32_t & bestDist,
                         uint32_t & bestPos);

public:
    /**
     * Calculates the min forward and reverse distances based on the given
     * match data and field id. The calculated values are stored in the given result object.
     * NB: Both query terms must have attached term fields with valid term field handles.
     **/
    static void run(const QueryTerm &termX, const QueryTerm &termY,
                    const fef::MatchData & match, uint32_t docId, Result & r);
};

}

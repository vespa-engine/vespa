// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termdistancecalculator.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/itermdata.h>

using namespace search::fef;

namespace search {
namespace features {

const uint32_t TermDistanceCalculator::UNDEFINED_VALUE(1000000);


void
TermDistanceCalculator::run(const QueryTerm &termX, const QueryTerm &termY,
                            const MatchData & match, uint32_t docId, Result & r)
{
    const TermFieldMatchData *tmdX = match.resolveTermField(termX.fieldHandle());
    const TermFieldMatchData *tmdY = match.resolveTermField(termY.fieldHandle());
    if (tmdX->getDocId() != docId || tmdY->getDocId() != docId) {
        return;
    }
    findBest(tmdX, tmdY, termX.termData()->getPhraseLength(), r.forwardDist, r.forwardTermPos);
    findBest(tmdY, tmdX, termY.termData()->getPhraseLength(), r.reverseDist, r.reverseTermPos);
}


void
TermDistanceCalculator::findBest(const TermFieldMatchData *tmdX,
                                 const TermFieldMatchData *tmdY,
                                 uint32_t numTermsX,
                                 uint32_t & bestDist,
                                 uint32_t & bestPos)
{
    search::fef::TermFieldMatchData::PositionsIterator itA, itB, epA, epB;
    itA = tmdX->begin();
    epA = tmdX->end();

    itB = tmdY->begin();
    epB = tmdY->end();

    uint32_t addA = numTermsX - 1;

    while (itB != epB) {
        uint32_t eid = itB->getElementId();
        while (itA != epA && itA->getElementId() < eid) {
            ++itA;
        }
        if (itA != epA && itA->getElementId() == eid) {
            // there is a pair somewhere here
            while (itA != epA &&
                   itB != epB &&
                   itA->getElementId() == eid &&
                   itB->getElementId() == eid)
            {
                uint32_t a = itA->getPosition();
                uint32_t b = itB->getPosition();
                if (a < b) {
                    if (b - a < bestDist + addA) {
                        bestDist = b - (a + addA);
                        bestPos = a;
                    }
                    itA++;
                } else {
                    itB++;
                }
            }
        } else {
            ++itB;
        }
    }

}


} // namespace features
} // namespace search

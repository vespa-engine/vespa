// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termdistancecalculator.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/itermdata.h>

using search::fef::ElementGap;
using search::fef::MatchData;
using search::fef::TermFieldMatchData;

namespace search::features {

const uint32_t TermDistanceCalculator::UNDEFINED_VALUE(1000000);

void
TermDistanceCalculator::run(const QueryTerm &termX, const QueryTerm &termY, ElementGap element_gap,
                            const MatchData & match, uint32_t docId, Result & r)
{
    const TermFieldMatchData *tmdX = match.resolveTermField(termX.fieldHandle());
    const TermFieldMatchData *tmdY = match.resolveTermField(termY.fieldHandle());
    if (!tmdX->has_ranking_data(docId) || !tmdY->has_ranking_data(docId)) {
        return;
    }
    findBest(tmdX, tmdY, element_gap, termX.termData()->getPhraseLength(), r.forwardDist, r.forwardTermPos);
    findBest(tmdY, tmdX, element_gap, termY.termData()->getPhraseLength(), r.reverseDist, r.reverseTermPos);
}

void
TermDistanceCalculator::findBest(const TermFieldMatchData *tmdX,
                                 const TermFieldMatchData *tmdY,
                                 ElementGap element_gap,
                                 uint32_t numTermsX,
                                 uint32_t & bestDist,
                                 uint32_t & bestPos)
{
    search::fef::TermFieldMatchData::PositionsIterator itA, itB, epA, epB;
    itA = tmdX->begin();
    epA = tmdX->end();

    itB = tmdY->begin();
    epB = tmdY->end();

    // Calculate bias to convert from position of first term in a phrase to position of last term in the phrase.
    uint32_t addA = numTermsX - 1;

    while (itA != epA && itB != epB) {
        uint32_t eid_b = itB->getElementId();
        uint32_t eid_a = itA->getElementId();
        if (eid_a > eid_b || (eid_a == eid_b && itA->getPosition() + addA >= itB->getPosition())) {
            ++itB;
        } else if (eid_a + (element_gap.has_value() ? 1 : 0) < eid_b) {
            ++itA;
        } else {
            uint32_t a = itA->getPosition() + addA;
            uint32_t b = itB->getPosition() + ((eid_a == eid_b) ? 0 : (itA->getElementLen() + element_gap.value()));
            if (b - a < bestDist) {
                bestDist = b - a;
                bestPos = a - addA;
            }
            ++itA;
        }
    }
}

}

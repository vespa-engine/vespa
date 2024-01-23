// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_query_node.h"
#include <cassert>

namespace search::streaming {

bool
SameElementQueryNode::evaluate() const {
    HitList hl;
    return ! evaluateHits(hl).empty();
}

void
SameElementQueryNode::addChild(QueryNode::UP child) {
    assert(dynamic_cast<const QueryTerm *>(child.get()) != nullptr);
    AndQueryNode::addChild(std::move(child));
}

const HitList &
SameElementQueryNode::evaluateHits(HitList & hl) const
{
    hl.clear();
    if ( !AndQueryNode::evaluate()) return hl;

    HitList tmpHL;
    const auto & children = getChildren();
    unsigned int numFields = children.size();
    unsigned int currMatchCount = 0;
    std::vector<unsigned int> indexVector(numFields, 0);
    auto curr = static_cast<const QueryTerm *> (children[currMatchCount].get());
    bool exhausted( curr->evaluateHits(tmpHL).empty());
    for (; !exhausted; ) {
        auto next = static_cast<const QueryTerm *>(children[currMatchCount+1].get());
        unsigned int & currIndex = indexVector[currMatchCount];
        unsigned int & nextIndex = indexVector[currMatchCount+1];

        const auto & currHit = curr->evaluateHits(tmpHL)[currIndex];
        uint32_t currElemId = currHit.element_id();

        const HitList & nextHL = next->evaluateHits(tmpHL);

        size_t nextIndexMax = nextHL.size();
        while ((nextIndex < nextIndexMax) && (nextHL[nextIndex].element_id() < currElemId)) {
            nextIndex++;
        }
        if ((nextIndex < nextIndexMax) && (nextHL[nextIndex].element_id() == currElemId)) {
            currMatchCount++;
            if ((currMatchCount+1) == numFields) {
                Hit h = nextHL[indexVector[currMatchCount]];
                hl.emplace_back(h.field_id(), h.element_id(), h.element_weight(), 0);
                currMatchCount = 0;
                indexVector[0]++;
            }
        } else {
            currMatchCount = 0;
            indexVector[currMatchCount]++;
        }
        curr = static_cast<const QueryTerm *>(children[currMatchCount].get());
        exhausted = (nextIndex >= nextIndexMax) || (indexVector[currMatchCount] >= curr->evaluateHits(tmpHL).size());
    }
    return hl;
}

}

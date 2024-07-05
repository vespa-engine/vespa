// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_query_node.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <cassert>

namespace search::streaming {

SameElementQueryNode::SameElementQueryNode(std::unique_ptr<QueryNodeResultBase> result_base, const string& index, uint32_t num_terms) noexcept
    : MultiTerm(std::move(result_base), index, num_terms)
{
}

SameElementQueryNode::~SameElementQueryNode() = default;

bool
SameElementQueryNode::evaluate() const {
    HitList hl;
    return ! evaluateHits(hl).empty();
}

const HitList &
SameElementQueryNode::evaluateHits(HitList & hl) const
{
    hl.clear();
    const auto & children = get_terms();
    for (auto& child : children) {
        if ( ! child->evaluate() ) {
            return hl;
        }
    }
    HitList tmpHL;
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

void
SameElementQueryNode::unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const fef::IIndexEnvironment&)
{
    HitList list;
    const HitList & hit_list = evaluateHits(list);
    if (!hit_list.empty()) {
        auto num_fields = td.numFields();
        /*
         * Currently reports hit for all fields for query node instead of
         * just the fields where the related subfields had matches.
         */
        for (size_t field_idx = 0; field_idx < num_fields; ++field_idx) {
            auto& tfd = td.field(field_idx);
            auto field_id = tfd.getFieldId();
            auto tmd = match_data.resolveTermField(tfd.getHandle());
            tmd->setFieldId(field_id);
            tmd->reset(docid);
        }
    }
}

bool
SameElementQueryNode::multi_index_terms() const noexcept
{
    return true;
}

bool
SameElementQueryNode::is_same_element_query_node() const noexcept
{
    return true;
}

}

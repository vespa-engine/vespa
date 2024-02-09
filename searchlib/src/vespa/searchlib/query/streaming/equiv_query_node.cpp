// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "equiv_query_node.h"
#include "phrase_query_node.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <algorithm>
#include <cassert>

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;
using search::fef::ITermFieldData;

namespace search::streaming {

namespace {

class HitWithFieldLength : public Hit
{
    uint32_t _field_length;
public:
    HitWithFieldLength(const Hit& hit, uint32_t field_length) noexcept
        : Hit(hit),
          _field_length(field_length)
    {
    }
    uint32_t get_field_length() const noexcept { return _field_length; }
};

uint16_t
cap_16_bits(uint32_t value)
{
    return std::min(value, static_cast<uint32_t>(std::numeric_limits<uint16_t>::max()));
}

uint32_t
extract_field_length(const QueryTerm& term, uint32_t field_id)
{
    return (field_id < term.getFieldInfoSize()) ? term.getFieldInfo(field_id).getFieldLength() : search::fef::FieldPositionsIterator::UNKNOWN_LENGTH;
}

void
set_interleaved_features(TermFieldMatchData& tmd, uint32_t field_length, uint32_t num_occs)
{
    tmd.setFieldLength(cap_16_bits(field_length));
    tmd.setNumOccs(cap_16_bits(num_occs));
}

template <typename HitType>
void merge_hits_from_children(std::vector<HitType>& hl, const MultiTerm& mt)
{
    HitList sub_hl_store;
    for (auto& subterm : mt.get_terms()) {
        auto *phrase = dynamic_cast<PhraseQueryNode*>(subterm.get());
        QueryTerm& fl_term = (phrase == nullptr) ? *subterm : *phrase->get_terms().front();
        auto& sub_hl = subterm->evaluateHits(sub_hl_store);
        for (auto& h : sub_hl) {
            if constexpr (std::is_same_v<Hit,HitType>) {
                hl.emplace_back(h);
            } else {
                hl.emplace_back(h, extract_field_length(fl_term, h.field_id()));
            }
        }
    }
    std::sort(hl.begin(), hl.end());
    auto last = std::unique(hl.begin(), hl.end(), [](auto& lhs, auto &rhs) noexcept { return lhs.at_same_pos(rhs); });
    hl.erase(last, hl.end());
}

}

EquivQueryNode::EquivQueryNode(std::unique_ptr<QueryNodeResultBase> result_base, uint32_t num_terms)
    : MultiTerm(std::move(result_base), "", num_terms)
{
}

EquivQueryNode::~EquivQueryNode() = default;

bool
EquivQueryNode::evaluate() const
{
    for (auto& subterm : get_terms()) {
        if (subterm->evaluate()) {
            return true;
        }
    }
    return false;
}

const HitList &
EquivQueryNode::evaluateHits(HitList & hl) const
{
    hl.clear();
    merge_hits_from_children(hl, *this);
    return hl;
}

void
EquivQueryNode::unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data)
{
    std::vector<HitWithFieldLength> hitList;
    merge_hits_from_children(hitList, *this);

    if (!hitList.empty()) { // only unpack if we have a hit
        uint32_t lastFieldId = -1;
        uint32_t last_field_length = 0;
        TermFieldMatchData *tmd = nullptr;
        uint32_t num_occs = 0;

        // optimize for hitlist giving all hits for a single field in one chunk
        for (auto& hit : hitList) {
            uint32_t fieldId = hit.field_id();
            if (fieldId != lastFieldId) {
                if (tmd != nullptr) {
                    if (tmd->needs_interleaved_features()) {
                        set_interleaved_features(*tmd, last_field_length, num_occs);
                    }
                    // reset to notfound/unknown values
                    tmd = nullptr;
                }
                num_occs = 0;

                // setup for new field that had a hit
                const ITermFieldData *tfd = td.lookupField(fieldId);
                if (tfd != nullptr) {
                    tmd = match_data.resolveTermField(tfd->getHandle());
                    tmd->setFieldId(fieldId);
                    // reset field match data, but only once per docId
                    if (tmd->getDocId() != docid) {
                        tmd->reset(docid);
                    }
                }
                lastFieldId = fieldId;
                last_field_length = hit.get_field_length();
            }
            ++num_occs;
            if (tmd != nullptr) {
                TermFieldMatchDataPosition pos(hit.element_id(), hit.position(),
                                               hit.element_weight(), hit.element_length());
                tmd->appendPosition(pos);
            }
        }
        if (tmd != nullptr) {
            if (tmd->needs_interleaved_features()) {
                set_interleaved_features(*tmd, last_field_length, num_occs);
            }
        }
    }
}

EquivQueryNode*
EquivQueryNode::as_equiv_query_node() noexcept
{
    return this;
}

const EquivQueryNode*
EquivQueryNode::as_equiv_query_node() const noexcept
{
    return this;
}

std::vector<std::unique_ptr<QueryTerm>>
EquivQueryNode::steal_terms()
{
    return std::move(_terms);
}

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "phrase_query_node.h"
#include "hit_iterator_pack.h"
#include <cassert>

namespace search::streaming {

PhraseQueryNode::PhraseQueryNode(std::unique_ptr<QueryNodeResultBase> result_base, const string& index, uint32_t num_terms)
    : MultiTerm(std::move(result_base), index, num_terms),
      _fieldInfo(32)
{
}

PhraseQueryNode::~PhraseQueryNode() = default;

bool
PhraseQueryNode::evaluate() const
{
  HitList hl;
  return ! evaluateHits(hl).empty();
}

void
PhraseQueryNode::getPhrases(QueryNodeRefList & tl)
{
    tl.push_back(this);
}

void
PhraseQueryNode::getPhrases(ConstQueryNodeRefList & tl) const
{
    tl.push_back(this);
}

void
PhraseQueryNode::getLeaves(QueryTermList & tl)
{
    for (const auto& node : get_terms()) {
        node->getLeaves(tl);
    }
}

void
PhraseQueryNode::getLeaves(ConstQueryTermList & tl) const
{
    for (const auto& node : get_terms()) {
        node->getLeaves(tl);
    }
}

size_t
PhraseQueryNode::width() const
{
    return get_terms().size();
}

MultiTerm*
PhraseQueryNode::as_multi_term() noexcept
{
    return nullptr;
}

const HitList &
PhraseQueryNode::evaluateHits(HitList & hl) const
{
    hl.clear();
    _fieldInfo.clear();
    auto& terms = get_terms();
    HitIteratorPack itr_pack(terms);
    if (!itr_pack.all_valid()) {
        return hl;
    }
    auto& last_child = dynamic_cast<const QueryTerm&>(*terms.back());
    while (itr_pack.seek_to_matching_field_element()) {
        uint32_t first_position = itr_pack.front()->position();
        bool retry_element = true;
        while (retry_element) {
            uint32_t position_offset = 0;
            bool match = true;
            for (auto& it : itr_pack) {
                if (!it.seek_in_field_element(first_position + position_offset, itr_pack.get_field_element_ref())) {
                    retry_element = false;
                    match = false;
                    break;
                }
                if (it->position() > first_position + position_offset) {
                    first_position = it->position() - position_offset;
                    match = false;
                    break;
                }
                ++position_offset;
            }
            if (match) {
                auto h = *itr_pack.back();
                hl.push_back(h);
                auto& fi = last_child.getFieldInfo(h.field_id());
                updateFieldInfo(h.field_id(), hl.size() - 1, fi.getFieldLength());
                if (!itr_pack.front().step_in_field_element(itr_pack.get_field_element_ref())) {
                    retry_element = false;
                }
            }
        }
    }
    return hl;
}

void
PhraseQueryNode::updateFieldInfo(size_t fid, size_t offset, size_t fieldLength) const
{
    if (fid >= _fieldInfo.size()) {
        _fieldInfo.resize(fid + 1);
        // only set hit offset and field length the first time
        QueryTerm::FieldInfo & fi = _fieldInfo[fid];
        fi.setHitOffset(offset);
        fi.setFieldLength(fieldLength);
    }
    QueryTerm::FieldInfo & fi = _fieldInfo[fid];
    fi.setHitCount(fi.getHitCount() + 1);
}

void
PhraseQueryNode::unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data)
{
    (void) docid;
    (void) td;
    (void) match_data;
}

}

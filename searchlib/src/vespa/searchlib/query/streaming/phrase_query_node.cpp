// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "phrase_query_node.h"
#include "hit_iterator_pack.h"
#include <cassert>

namespace search::streaming {

bool
PhraseQueryNode::evaluate() const
{
  HitList hl;
  return ! evaluateHits(hl).empty();
}

void PhraseQueryNode::getPhrases(QueryNodeRefList & tl)            { tl.push_back(this); }
void PhraseQueryNode::getPhrases(ConstQueryNodeRefList & tl) const { tl.push_back(this); }

void
PhraseQueryNode::addChild(QueryNode::UP child) {
    assert(dynamic_cast<const QueryTerm *>(child.get()) != nullptr);
    AndQueryNode::addChild(std::move(child));
}

const HitList &
PhraseQueryNode::evaluateHits(HitList & hl) const
{
    hl.clear();
    _fieldInfo.clear();
    HitIteratorPack itr_pack(getChildren());
    if (!itr_pack.all_valid()) {
        return hl;
    }
    auto& last_child = dynamic_cast<const QueryTerm&>(*(*this)[size() - 1]);
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

}

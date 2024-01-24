// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "near_query_node.h"
#include "hit_iterator_pack.h"
#include <vespa/vespalib/objects/visit.hpp>

namespace search::streaming {

template <bool ordered>
bool
NearQueryNode::evaluate_helper() const
{
    HitIteratorPack itr_pack(getChildren());
    if (!itr_pack.all_valid()) {
        return false;
    }
    while (itr_pack.seek_to_matching_field_element()) {
        uint32_t min_position = 0;
        if (itr_pack.front()->position() > min_position + distance()) {
            min_position = itr_pack.front()->position() - distance();
        }
        bool retry_element = true;
        while (retry_element) {
            bool match = true;
            uint32_t min_next_position = min_position;
            for (auto& it : itr_pack) {
                if (!it.seek_in_field_element(min_next_position, itr_pack.get_field_element_ref())) {
                    retry_element = false;
                    match = false;
                    break;
                }
                if (it->position() > min_position + distance()) {
                    min_position = it->position() - distance();
                    match = false;
                    break;
                }
                if constexpr (ordered) {
                    min_next_position = it->position() + 1;
                }
            }
            if (match) {
                return true;
            }
        }
    }
    return false;
}

bool
NearQueryNode::evaluate() const
{
    return evaluate_helper<false>();
}

void
NearQueryNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AndQueryNode::visitMembers(visitor);
    visit(visitor, "distance", static_cast<uint64_t>(_distance));
}

template bool NearQueryNode::evaluate_helper<false>() const;
template bool NearQueryNode::evaluate_helper<true>() const;

}

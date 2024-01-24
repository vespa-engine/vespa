// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onear_query_node.h"
#include "hit_iterator_pack.h"

namespace search::streaming {

bool
ONearQueryNode::evaluate() const
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
                min_next_position = it->position() + 1;
            }
            if (match) {
                return true;
            }
        }
    }
    return false;
}

}

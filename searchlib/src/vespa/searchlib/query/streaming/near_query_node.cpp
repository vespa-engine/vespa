// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "near_query_node.h"
#include "hit_iterator_pack.h"
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/util/priority_queue.h>

using vespalib::PriorityQueue;

namespace search::streaming {

bool
NearQueryNode::evaluate() const
{
    PriorityQueue<HitIterator> queue;
    HitKey max_key(0, 0, 0);
    auto& children = getChildren();
    if (children.empty()) {
        return false; // No terms
    }
    for (auto& child : children) {
        auto& curr = dynamic_cast<const QueryTerm&>(*child);
        auto& hit_list = curr.getHitList();
        if (hit_list.empty()) {
            return false; // Empty term
        }
        if (max_key < hit_list.front().key()) {
            max_key = hit_list.front().key();
        }
        queue.push(HitIterator(hit_list));
    }
    for (;;) {
        auto& front = queue.front();
        auto last_allowed = calc_window_end_pos(*front);
        if (!(last_allowed < max_key)) {
            return true;
        }
        do {
            ++front;
            if (!front.valid()) {
                return false;
            }
            last_allowed = calc_window_end_pos(*front);
        } while (last_allowed < max_key);
        if (max_key < front->key()) {
            max_key = front->key();
        }
        queue.adjust();
    }
}

void
NearQueryNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AndQueryNode::visitMembers(visitor);
    visit(visitor, "distance", static_cast<uint64_t>(_distance));
}

}

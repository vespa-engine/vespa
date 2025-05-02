// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onear_query_node.h"
#include "hit_iterator_pack.h"
#include <span>

namespace search::streaming {

bool
ONearQueryNode::evaluate() const
{
    HitIteratorPack itr_pack(getChildren());
    if (!itr_pack.all_valid()) {
        return false; // No terms, or an empty term found
    }
    std::span<HitIterator> others(itr_pack.begin() + 1, itr_pack.end());
    if (others.empty()) {
        return true; // A single term
    }
    HitKey cur_term_pos(0, 0, 0);
    for (auto& front = itr_pack.front(); front.valid(); ++front) {
        auto last_allowed = calc_window_end_pos(*front);
        if (last_allowed < cur_term_pos) {
            continue;
        }
        auto prev_term_pos = front->key();
        bool match = true;
        for (auto& it : others) {
            while (!(prev_term_pos < it->key())) {
                ++it;
                if (!it.valid()) {
                    return false;
                }
            }
            cur_term_pos = it->key();
            if (last_allowed < cur_term_pos) {
                match = false;
                break;
            }
            prev_term_pos = cur_term_pos;
        }
        if (match) {
            return true;
        }
    }
    return false;
}

}

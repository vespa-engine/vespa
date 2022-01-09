// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "posting_priority_queue.h"

namespace search {

template <class Reader>
void
PostingPriorityQueue<Reader>::adjust()
{
    typedef typename Vector::iterator VIT;
    if (!_vec.front().get()->isValid()) {
        _vec.erase(_vec.begin());   // Iterator no longer valid
        return;
    }
    if (_vec.size() == 1) {       // Only one iterator left
        return;
    }
    // Peform binary search to find first element higher than changed value
    VIT gt = std::upper_bound(_vec.begin() + 1, _vec.end(), _vec.front());
    VIT to = _vec.begin();
    VIT from = to;
    ++from;
    Ref changed = *to;   // Remember changed value
    while (from != gt) { // Shift elements to make space for changed value
        *to = *from;
        ++from;
        ++to;
    }
    *to = changed;   // Save changed value at right location
}

template <class Reader>
void
PostingPriorityQueue<Reader>::setup(uint32_t heap_limit)
{
    _heap_limit = heap_limit;
    for (auto ref : _vec) {
        assert(ref.get()->isValid());
    }
    if (_vec.size() >= heap_limit) {
        std::sort(_vec.begin(), _vec.end());
    }
}

}

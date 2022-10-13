// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_hold_list.h"
#include "lidstatevector.h"
#include <cassert>

namespace proton {

LidHoldList::LidHoldList() = default;
LidHoldList::~LidHoldList() = default;

void
LidHoldList::add(const uint32_t data, generation_t generation) {
    if (!_holdList.empty()) {
        assert(generation >= _holdList.back().second);
    }
    _holdList.push_back(std::make_pair(data, generation));
}

void
LidHoldList::clear() {
    _holdList.clear();
}

void
LidHoldList::reclaim_memory(generation_t oldest_used_gen, LidStateVector &freeLids)
{
    while (!_holdList.empty() && _holdList.front().second < oldest_used_gen) {
        uint32_t lid = _holdList.front().first;
        freeLids.setBit(lid);
        _holdList.pop_front();
    }
}

}

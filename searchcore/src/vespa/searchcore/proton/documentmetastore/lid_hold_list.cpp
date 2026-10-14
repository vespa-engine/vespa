// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_hold_list.h"

#include "lidstatevector.h"

#include <vespa/check_require.h>

using vespalib::Generation;

namespace proton {

LidHoldList::LidHoldList() = default;
LidHoldList::~LidHoldList() = default;

void LidHoldList::add(const uint32_t data, Generation generation) {
    if (!_holdList.empty()) {
        CHECK(generation >= _holdList.back().second);
    }
    _holdList.emplace_back(data, generation);
}

void LidHoldList::clear() {
    _holdList.clear();
}

void LidHoldList::reclaim_memory(Generation oldest_used_gen, LidStateVector& freeLids) {
    while (!_holdList.empty() && _holdList.front().second < oldest_used_gen) {
        uint32_t lid = _holdList.front().first;
        freeLids.setBit(lid);
        _holdList.pop_front();
    }
}

} // namespace proton

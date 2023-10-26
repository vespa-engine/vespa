// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cell_type_space.h"

namespace vespalib::eval::test {

void
CellTypeSpace::step_state() {
    for (size_t idx = _state.size(); idx-- > 0; ) {
        if ((++_state[idx]) < _types.size()) {
            return;
        } else {
            _state[idx] = 0;
        }
    }
    _done = true;
}

bool
CellTypeSpace::should_skip() {
    if (_done) {
        return false;
    }
    bool same = true;
    auto type = _state[0];
    for (auto t: _state) {
        if (t != type) {
            same = false;
        }
    }
    return same ? _drop_same : _drop_different;
}

CellTypeSpace::~CellTypeSpace() = default;

} // namespace

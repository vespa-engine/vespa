// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packed_labels.h"
#include <assert.h>

namespace vespalib::eval {

int32_t
PackedLabels::find_label(vespalib::stringref to_find) const
{
    uint32_t lo = 0;
    uint32_t hi = num_labels();
    while (lo < hi) {
        uint32_t mid = (lo + hi) / 2;
        if (label_value(mid) < to_find) {
            lo = mid + 1;
        } else {
            hi = mid;
        }
    }
    assert(lo == hi);
    if (lo < num_labels() && label_value(lo) == to_find) {
        return lo;
    }
    return -1;
}

vespalib::stringref
PackedLabels::label_value(uint32_t index) const
{
    assert(index < num_labels());

    auto p = get_label_start(index);
    auto sz = get_label_size(index);
    return vespalib::stringref(p, sz);
}

void
PackedLabels::validate_labels(uint32_t num_labels)
{
    assert(num_labels == _offsets.size()-1);
    for (uint32_t i = 0; i < num_labels; ++i) {
        assert(_offsets[i] < _offsets[i+1]);
        uint32_t last_byte_index = _offsets[i+1] - 1;
        assert(_label_store[last_byte_index] == 0);
    }
    assert(_label_store.size() == _offsets[num_labels]);
    for (uint32_t i = 0; i+1 < num_labels; ++i) {
        assert(label_value(i) < label_value(i+1));
    }
}

const char *
PackedLabels::get_label_start(uint32_t index) const {
    uint32_t offset = _offsets[index];
    return &_label_store[offset];
}

uint32_t
PackedLabels::get_label_size(uint32_t index) const {
    return _offsets[index+1] - _offsets[index] - 1;
}

} // namespace

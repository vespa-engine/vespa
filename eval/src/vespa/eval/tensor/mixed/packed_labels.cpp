// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packed_labels.h"
#include <assert.h>

namespace vespalib::eval::packed_mixed_tensor {

int32_t
PackedLabels::find_label(vespalib::stringref to_find) const
{
    uint32_t lo = 0;
    uint32_t hi = num_labels();
    while (lo < hi) {
        uint32_t mid = (lo + hi) / 2;
        if (get_label(mid) < to_find) {
            lo = mid + 1;
        } else {
            hi = mid;
        }
    }
    assert(lo == hi);
    if (lo < num_labels() && get_label(lo) == to_find) {
        return lo;
    }
    return -1;
}

vespalib::stringref
PackedLabels::get_label(uint32_t index) const
{
    assert(index < num_labels());

    uint32_t this_offset = _offsets[index];
    uint32_t next_offset = _offsets[index+1];
    auto p = &_label_store[this_offset];
    size_t sz = next_offset - this_offset - 1;
    return vespalib::stringref(p, sz);
}

MemoryUsage
PackedLabels::estimate_extra_memory_usage() const
{
    MemoryUsage extra_usage;
    size_t offsets_size = _offsets.size() * sizeof(uint32_t);
    size_t labels_size = _label_store.size() * sizeof(char);
    extra_usage.merge(MemoryUsage(offsets_size, offsets_size, 0, 0));
    extra_usage.merge(MemoryUsage(labels_size, labels_size, 0, 0));
    return extra_usage;
}

void
PackedLabels::validate_labels(uint32_t num_labels)
{
    assert(num_labels == _offsets.size()-1);
    for (uint32_t i = 0; i < num_labels; ++i) {
        assert(_offsets[i] < _offsets[i+1]);
        uint32_t last_byte_index = _offsets[i+1] - 1;
        assert(last_byte_index < _label_store.size());
        assert(_label_store[last_byte_index] == 0);
    }
    assert(_label_store.size() == _offsets[num_labels]);
    for (uint32_t i = 0; i+1 < num_labels; ++i) {
        assert(get_label(i) < get_label(i+1));
    }
}

} // namespace

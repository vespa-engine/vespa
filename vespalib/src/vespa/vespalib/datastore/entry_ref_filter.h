// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"
#include <vector>

namespace vespalib::datastore {

/*
 * Class to filter entry refs based on which buffer the entry is referencing.
 *
 * Buffers being allowed have corresponding bit in _filter set.
 */
class EntryRefFilter {
    std::vector<bool> _filter;
    uint32_t          _offset_bits;
    EntryRefFilter(std::vector<bool> filter, uint32_t offset_bits);
public:
    EntryRefFilter(uint32_t num_buffers, uint32_t offset_bits);
    ~EntryRefFilter();
    bool has(EntryRef ref) const {
        uint32_t buffer_id = ref.buffer_id(_offset_bits);
        return _filter[buffer_id];
    }
    void add_buffer(uint32_t buffer_id) { _filter[buffer_id] = true; }
    void add_buffers(const std::vector<uint32_t>& ids) {
        for (auto buffer_id : ids) {
            _filter[buffer_id] = true;
        }
    }
    static EntryRefFilter create_all_filter(uint32_t num_buffers, uint32_t offset_bits);
};

}

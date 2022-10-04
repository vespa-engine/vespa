// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vector>

namespace vespalib::datastore {

class DataStoreBase;
class EntryRefFilter;

/*
 * Class representing the buffers currently being compacted in a data store.
 */
class CompactingBuffers
{
    DataStoreBase&        _store;
    uint32_t              _num_buffers;
    uint32_t              _offset_bits;
    std::vector<uint32_t> _buffer_ids;
public:
    CompactingBuffers(DataStoreBase& store, uint32_t num_buffers, uint32_t offset_bits, std::vector<uint32_t> buffer_ids);
    ~CompactingBuffers();
    DataStoreBase& get_store() const noexcept { return _store; }
    const std::vector<uint32_t>& get_buffer_ids() const noexcept { return _buffer_ids; }
    bool empty() const noexcept { return _buffer_ids.empty(); }
    void finish();
    EntryRefFilter make_entry_ref_filter() const;
};

}

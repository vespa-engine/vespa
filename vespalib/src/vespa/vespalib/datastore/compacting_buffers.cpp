// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compacting_buffers.h"
#include "datastorebase.h"
#include "entry_ref_filter.h"
#include <cassert>

namespace vespalib::datastore {

CompactingBuffers::CompactingBuffers(DataStoreBase& store, uint32_t num_buffers, uint32_t offset_bits, std::vector<uint32_t> buffer_ids)
    : _store(store),
      _num_buffers(num_buffers),
      _offset_bits(offset_bits),
      _buffer_ids(std::move(buffer_ids))
{
}

CompactingBuffers::~CompactingBuffers()
{
    assert(_buffer_ids.empty());
}

void
CompactingBuffers::finish()
{
    _store.finishCompact(_buffer_ids);
    _buffer_ids.clear();
}

EntryRefFilter
CompactingBuffers::make_entry_ref_filter() const
{
    EntryRefFilter filter(_num_buffers, _offset_bits);
    filter.add_buffers(_buffer_ids);
    return filter;
}

}

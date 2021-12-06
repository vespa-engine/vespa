// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "entry_ref_filter.h"

namespace vespalib::datastore {

EntryRefFilter::EntryRefFilter(std::vector<bool> filter, uint32_t offset_bits)
    : _filter(std::move(filter)),
      _offset_bits(offset_bits)
{
}

EntryRefFilter::EntryRefFilter(uint32_t num_buffers, uint32_t offset_bits)
    : _filter(num_buffers),
      _offset_bits(offset_bits)
{
}

EntryRefFilter::~EntryRefFilter() = default;

EntryRefFilter
EntryRefFilter::create_all_filter(uint32_t num_buffers, uint32_t offset_bits)
{
    std::vector<bool> filter(num_buffers, true);
    return EntryRefFilter(std::move(filter), offset_bits);
}

}

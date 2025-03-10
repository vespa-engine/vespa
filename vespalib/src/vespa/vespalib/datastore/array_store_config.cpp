// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_store_config.h"
#include <algorithm>
#include <cassert>

namespace vespalib::datastore {

ArrayStoreConfig::ArrayStoreConfig(uint32_t max_type_id, const AllocSpec &defaultSpec)
    : _allocSpecs(),
      _enable_free_lists(false)
{
    for (uint32_t type_id = 0; type_id < (max_type_id + 1); ++type_id) {
        _allocSpecs.push_back(defaultSpec);
    }
}

ArrayStoreConfig::ArrayStoreConfig(const AllocSpecVector &allocSpecs)
    : _allocSpecs(allocSpecs),
      _enable_free_lists(false)
{
}

const ArrayStoreConfig::AllocSpec &
ArrayStoreConfig::spec_for_type_id(uint32_t type_id) const
{
    assert(type_id < _allocSpecs.size());
    return _allocSpecs[type_id];
}

namespace {

size_t
capToLimits(size_t value, size_t minLimit, size_t maxLimit)
{
    size_t result = std::max(value, minLimit);
    return std::min(result, maxLimit);
}

size_t
alignToSmallPageSize(size_t value, size_t minLimit, size_t smallPageSize)
{
    return ((value - minLimit) / smallPageSize) * smallPageSize + minLimit;
}

size_t
cap_max_entries(size_t max_entries, size_t max_buffer_size, size_t entry_size)
{
    size_t dynamic_max_entries = (max_buffer_size + (entry_size - 1)) / entry_size;
    return std::min(max_entries, dynamic_max_entries);
}

}

ArrayStoreConfig
ArrayStoreConfig::optimizeForHugePage(uint32_t max_type_id,
                                      std::function<size_t(uint32_t)> type_id_to_entry_size,
                                      size_t hugePageSize,
                                      size_t smallPageSize,
                                      size_t maxEntryRefOffset,
                                      size_t max_buffer_size,
                                      size_t min_num_entries_for_new_buffer,
                                      float allocGrowFactor)
{
    AllocSpecVector allocSpecs;
    auto entry_size = type_id_to_entry_size(max_type_id);
    auto capped_max_entries = cap_max_entries(maxEntryRefOffset, max_buffer_size, entry_size);
    auto capped_min_num_entries_for_new_buffer = std::min(min_num_entries_for_new_buffer, capped_max_entries);
    allocSpecs.emplace_back(0, capped_max_entries, capped_min_num_entries_for_new_buffer, allocGrowFactor); // large array spec;
    for (uint32_t type_id = 1; type_id <= max_type_id; ++type_id) {
        entry_size = type_id_to_entry_size(type_id);
        capped_max_entries = cap_max_entries(maxEntryRefOffset, max_buffer_size, entry_size);
        capped_min_num_entries_for_new_buffer = std::min(min_num_entries_for_new_buffer, capped_max_entries);
        size_t num_entries_for_new_buffer = hugePageSize / entry_size;
        num_entries_for_new_buffer = capToLimits(num_entries_for_new_buffer, capped_min_num_entries_for_new_buffer, capped_max_entries);
        num_entries_for_new_buffer = alignToSmallPageSize(num_entries_for_new_buffer, capped_min_num_entries_for_new_buffer, smallPageSize);
        allocSpecs.emplace_back(0, capped_max_entries, num_entries_for_new_buffer, allocGrowFactor);
    }
    return ArrayStoreConfig(allocSpecs);
}

}

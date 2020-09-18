// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packed_mappings.h"
#include <assert.h>

namespace vespalib::eval::packed_mixed_tensor {


int32_t
PackedMappings::subspace_of_address(const Address &address) const
{
    int32_t idx = sortid_of_address(address);
    if (idx < 0) {
        return -1;
    }
    uint32_t internal_idx = idx;
    assert (internal_idx < _num_mappings);
    return subspace_of_sortid(internal_idx);
}

int32_t
PackedMappings::subspace_of_enums(const InternalAddress &address) const
{
    int32_t idx = sortid_of_enums(address);
    if (idx < 0) {
        return -1;
    }
    uint32_t internal_idx = idx;
    assert (internal_idx < _num_mappings);
    return subspace_of_sortid(internal_idx);
}

int32_t
PackedMappings::sortid_of_address(const Address &address) const
{
    if (_num_dims == 0) return 0;
    assert(address.size() == _num_dims);
    std::vector<uint32_t> to_find;
    to_find.reserve(_num_dims);
    for (const auto & label_value : address) {
        int32_t label_idx = _label_store.find_label(label_value);
        if (label_idx < 0) {
            return -1;
        }
        to_find.push_back(label_idx);
    }
    return sortid_of_enums(to_find);
}

int32_t
PackedMappings::sortid_of_enums(const InternalAddress &address) const
{
    if (_num_dims == 0) return 0;
    assert(address.size() == _num_dims);
    const uint32_t * to_find = &address[0];
    uint32_t lo = 0;
    uint32_t hi = _num_mappings;
    while (lo < hi) {
        uint32_t mid = (lo + hi) / 2;
        if (enums_compare(ptr_of_sortid(mid), to_find) < 0) {
            lo = mid + 1;
        } else {
            hi = mid;
        }
    }
    assert(lo == hi);
    if ((lo < _num_mappings) &&
        (enums_compare(ptr_of_sortid(lo), to_find) == 0))
    {
        return lo;
    }
    return -1;
}

std::vector<vespalib::stringref>
PackedMappings::address_of_sortid(uint32_t internal_index) const
{
    std::vector<vespalib::stringref> result;
    fill_by_sortid(internal_index, result);
    return result;
}

std::vector<vespalib::stringref>
PackedMappings::address_of_subspace(uint32_t subspace_index) const
{
    std::vector<vespalib::stringref> result;
    fill_by_subspace(subspace_index, result);
    return result;
}

uint32_t
PackedMappings::fill_by_subspace(uint32_t subspace_index, InternalAddress &address) const
{
    assert(subspace_index < _num_mappings);
    uint32_t internal_index = sortid_of_subspace(subspace_index);
    uint32_t subspace = fill_by_sortid(internal_index, address);
    assert(subspace == subspace_index);
    return internal_index;
}

uint32_t
PackedMappings::fill_by_sortid(uint32_t internal_index, InternalAddress &address) const
{
    assert(internal_index < _num_mappings);
    uint32_t offset = offset_of_mapping_data(internal_index);
    address.resize(_num_dims);
    for (uint32_t i = 0; i < _num_dims; ++i) {
        address[i] = _int_store[offset++];
    }
    return _int_store[offset];
}

/** returns subspace_index */
uint32_t
PackedMappings::fill_by_sortid(uint32_t internal_index, Address &address) const
{
    assert(internal_index < _num_mappings);
    uint32_t offset = offset_of_mapping_data(internal_index);
    address.resize(_num_dims);
    for (uint32_t i = 0; i < _num_dims; ++i) {
        uint32_t label_idx = _int_store[offset++];
        address[i] = _label_store.label_value(label_idx);
    }
    return _int_store[offset];
}

/** returns internal_index */
uint32_t
PackedMappings::fill_by_subspace(uint32_t subspace_index, Address &address) const
{
    assert(subspace_index < _num_mappings);
    uint32_t internal_index = sortid_of_subspace(subspace_index);
    uint32_t subspace = fill_by_sortid(internal_index, address);
    assert(subspace == subspace_index);
    return internal_index;
}

void
PackedMappings::validate() const
{
    assert((_num_mappings * (2 + _num_dims)) == _int_store.size());
    auto iter = _int_store.cbegin();
    for (uint32_t i = 0; i < _num_mappings; ++i) {
        uint32_t internal_index = *iter++;
        assert(internal_index < _num_mappings);
    }
    std::vector<uint32_t> prev;
    std::vector<uint32_t> next;
    for (uint32_t i = 0; i < _num_mappings; ++i) {
        next.clear();
        for (uint32_t j = 0; j < _num_dims; ++j) {
            uint32_t label_index = *iter++;
            next.push_back(label_index);
            assert(label_index < _label_store.num_labels());
        }
        if (_num_dims == 0) {
            assert(next == prev);
            assert(i == 0);
            assert(_num_mappings == 1);
        } else {
            assert(prev < next);
        }
        std::swap(prev, next);
        uint32_t subspace_index = *iter++;
        assert(subspace_index < _num_mappings);
    }
    assert(iter == _int_store.cend());
}

std::vector<uint32_t>
PackedMappings::enums_of_subspace(uint32_t subspace_index) const
{
    assert(subspace_index < _num_mappings);
    uint32_t internal_index = sortid_of_subspace(subspace_index);
    return enums_of_sortid(internal_index);
}

std::vector<uint32_t>
PackedMappings::enums_of_sortid(uint32_t internal_index) const
{
    std::vector<uint32_t> result;
    result.reserve(_num_dims);
    assert(internal_index < _num_mappings);
    uint32_t offset = offset_of_mapping_data(internal_index);
    for (uint32_t i = 0; i < _num_dims; ++i) {
        result.push_back(_int_store[offset++]);
    }
    return result;
}


} // namespace

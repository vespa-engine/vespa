// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packed_mappings_builder.h"
#include <assert.h>

namespace vespalib::eval {

PackedMappingsBuilder::~PackedMappingsBuilder() = default;

uint32_t
PackedMappingsBuilder::add_mapping_for(SparseAddress address)
{
    assert(address.size() == _num_dims);
    for (auto & label_value : address) {
        // store label string in our own set:
        auto iter = _labels.insert(label_value).first;
        label_value = *iter;
    }

    uint32_t next_index = _mappings.size();
    IndexMap::value_type new_val(address, next_index);
    auto iter = _mappings.insert(new_val).first;
    return iter->second;
}


size_t
PackedMappingsBuilder::extra_memory() const
{
    size_t int_store_cnt = (2 + _num_dims) * _mappings.size();
    size_t int_store_size = int_store_cnt * sizeof(uint32_t);
    size_t label_cnt = _labels.size();
    size_t label_offsets_size = (1 + label_cnt) * sizeof(uint32_t);
    size_t label_bytes = 0;
    for (const auto & label_value : _labels) {
        label_bytes += (label_value.size() + 1);
    }
    size_t extra_size = int_store_size + label_offsets_size + label_bytes;
    return extra_size;
}

PackedMappings
PackedMappingsBuilder::target_memory(char *mem_start, char *mem_end) const
{
    size_t int_store_cnt = (2 + _num_dims) * _mappings.size();
    size_t int_store_size = int_store_cnt * sizeof(uint32_t);
    size_t label_cnt = _labels.size();
    size_t label_offsets_size = (1 + label_cnt) * sizeof(uint32_t);

    size_t label_bytes = 0;
    for (const auto & label_value : _labels) {
        label_bytes += (label_value.size() + 1);
    }

    ssize_t needs_sz = int_store_size + label_offsets_size + label_bytes;
    ssize_t avail_sz = mem_end - mem_start;
    assert(needs_sz <= avail_sz);

    uint32_t * int_store_mem = (uint32_t *) mem_start;
    uint32_t * offsets_mem = (uint32_t *) (mem_start + int_store_size);
    char * labels_mem = mem_start + int_store_size + label_offsets_size;

    ArrayRef<uint32_t> int_store_data(int_store_mem, int_store_cnt);
    ArrayRef<uint32_t> label_offsets(offsets_mem, 1 + label_cnt);
    ArrayRef<char> labels_data(labels_mem, label_bytes);

    size_t byte_idx = 0;
    size_t label_num = 0;
    for (const auto & label_value : _labels) {
        label_offsets[label_num++] = byte_idx;
        size_t len_with_zero = label_value.size() + 1;
        memcpy(&labels_data[byte_idx], label_value.data(), len_with_zero);
        byte_idx += len_with_zero;
    }
    assert(label_num == label_cnt);
    label_offsets[label_num] = byte_idx;

    PackedLabels stored_labels(label_cnt, label_offsets, labels_data);

    size_t mapping_idx = 0;
    size_t int_store_offset = _mappings.size();
    for (const auto & kv : _mappings) {
        const SparseAddress & k = kv.first;
        uint32_t v = kv.second;
        for (const auto & label_value : k) {
            int32_t label_idx = stored_labels.find_label(label_value);
            assert(label_idx >= 0);
            assert(uint32_t(label_idx) < label_num);
            int_store_data[int_store_offset++] = label_idx;
        }
        int_store_data[int_store_offset++] = v;
        int_store_data[v] = mapping_idx++;
    }
    assert(int_store_offset == int_store_cnt);
    assert(mapping_idx == _mappings.size());

    return PackedMappings(_num_dims, _mappings.size(),
                          int_store_data, stored_labels);
}

std::unique_ptr<PackedMappings>
PackedMappingsBuilder::build_mappings() const
{
    size_t meta_size = sizeof(PackedMappings);
    size_t total_size = meta_size + extra_memory();

    char * mem = (char *) operator new(total_size);
    auto meta_data = target_memory(mem + meta_size, mem + total_size);

    PackedMappings * built = new (mem) PackedMappings(meta_data);

    return std::unique_ptr<PackedMappings>(built);
}

} // namespace




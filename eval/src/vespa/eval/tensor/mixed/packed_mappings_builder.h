// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "packed_mappings.h"
#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <memory>
#include <set>
#include <vector>

namespace vespalib::eval::packed_mixed_tensor {

/**
 *  Builder for PackedMappings.
 *  Copies label values in all addresses added
 *  and packs all resulting data into a block of memory
 *  held by the built object, usually part of a larger
 *  aggregating object by using target_memory() method.
 **/
class PackedMappingsBuilder {
public:
    using SparseAddress = std::vector<vespalib::stringref>;

    PackedMappingsBuilder(uint32_t num_mapped_dims)
      : _num_dims(num_mapped_dims),
        _labels(),
        _mappings()
    {}

    ~PackedMappingsBuilder();

    // returns a new index for new addresses
    // may be called multiple times with same address,
    // will then return the same index for that address.
    uint32_t add_mapping_for(SparseAddress address);

    // how much extra memory is needed by target_memory
    // not including sizeof(PackedMappings)
    size_t extra_memory() const;

    // put data that PackedMappings can refer to in the given
    // memory block, and return an object referring to it.
    PackedMappings target_memory(char *mem_start, char *mem_end) const;

    // number of dimensions
    uint32_t num_mapped_dims() const { return _num_dims; }

    // how many unique addresses have been added?
    size_t size() const { return _mappings.size(); }

    // build a self-contained PackedMappings object;
    // used for unit testing.
    std::unique_ptr<PackedMappings> build_mappings() const;

private:
    uint32_t _num_dims;
    std::set<vespalib::string> _labels;
    using IndexMap = std::map<SparseAddress, uint32_t>;
    IndexMap _mappings;
};

} // namespace

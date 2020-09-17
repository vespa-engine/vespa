// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "packed_mappings.h"
#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <memory>
#include <set>
#include <vector>

namespace vespalib::eval {

class PackedMappingsBuilder {
public:
    using SparseAddress = std::vector<vespalib::stringref>;

    PackedMappingsBuilder(uint32_t num_sparse_dims)
      : _num_dims(num_sparse_dims),
        _labels(),
        _mappings()
    {}

    ~PackedMappingsBuilder();

    uint32_t add_mapping_for(SparseAddress address);

    std::unique_ptr<PackedMappings> build_mappings() const;

    size_t extra_memory() const;
    PackedMappings target_memory(char *mem_start, char *mem_end) const;

    uint32_t num_sparse_dims() const { return _num_dims; }
    size_t size() const { return _mappings.size(); }
private:
    uint32_t _num_dims;
    std::set<vespalib::string> _labels;
    using IndexMap = std::map<SparseAddress, uint32_t>;
    IndexMap _mappings;
};

} // namespace

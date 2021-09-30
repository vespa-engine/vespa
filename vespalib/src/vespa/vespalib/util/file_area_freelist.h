// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <limits>
#include <map>
#include <set>

namespace vespalib::alloc {

/*
 * Class that tracks free areas in a file.
 */
class FileAreaFreeList {
    std::map<uint64_t, size_t> _free_areas; // map from offset to size
    std::map<size_t, std::set<uint64_t>> _free_sizes; // map from size to set of offsets
    void remove_from_size_set(uint64_t offset, size_t size);
    std::pair<uint64_t, size_t> prepare_reuse_area(size_t size);
public:
    FileAreaFreeList();
    ~FileAreaFreeList();
    uint64_t alloc(size_t size);
    void free(uint64_t offset, size_t size);
    static constexpr uint64_t bad_offset = std::numeric_limits<uint64_t>::max();
};

}

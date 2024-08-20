// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <atomic>
#include <memory>
#include <string>

namespace vespalib::alloc {

class MemoryAllocator;

/*
 * Class for creating an mmap file allocator on demand.
 */
class MmapFileAllocatorFactory {
    std::string _dir_name;
    std::atomic<uint64_t> _generation;

    MmapFileAllocatorFactory();
    ~MmapFileAllocatorFactory();
    MmapFileAllocatorFactory(const MmapFileAllocatorFactory &) = delete;
    MmapFileAllocatorFactory& operator=(const MmapFileAllocatorFactory &) = delete;
public:
    void setup(const std::string &dir_name);
    std::unique_ptr<MemoryAllocator> make_memory_allocator(const std::string& name);

    static MmapFileAllocatorFactory& instance();
};

}

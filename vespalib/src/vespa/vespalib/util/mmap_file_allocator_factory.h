// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <atomic>

namespace vespalib::alloc {

class MemoryAllocator;

/*
 * Class for creating an mmap file allocator on demand.
 */
class MmapFileAllocatorFactory {
    vespalib::string _dir_name;
    std::atomic<uint64_t> _generation;

    MmapFileAllocatorFactory();
    ~MmapFileAllocatorFactory();
    MmapFileAllocatorFactory(const MmapFileAllocatorFactory &) = delete;
    MmapFileAllocatorFactory& operator=(const MmapFileAllocatorFactory &) = delete;
public:
    void setup(const vespalib::string &dir_name);
    std::unique_ptr<MemoryAllocator> make_memory_allocator(const vespalib::string& name);
    
    static MmapFileAllocatorFactory& instance();
};

}

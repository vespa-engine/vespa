// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>

namespace vespalib::alloc {

class MemoryAllocator;

/*
 * Class for creating an mmap file allocator on demand.
 */
class MmapFileAllocatorFactory {
    std::string           _dir_name;
    std::atomic<uint64_t> _generation;
    std::atomic<size_t>   _max_readahead_size;

    MmapFileAllocatorFactory();
    ~MmapFileAllocatorFactory();
    MmapFileAllocatorFactory(const MmapFileAllocatorFactory&) = delete;
    MmapFileAllocatorFactory& operator=(const MmapFileAllocatorFactory&) = delete;

public:
    void setup(const std::string& dir_name);
    std::unique_ptr<MemoryAllocator> make_memory_allocator(const std::string& name);
    // The longest memory range the kernel will read ahead for a single MADV_WILLNEED range on memory
    // from the allocators made here (cf. WillNeedAdvisor::max_range_size_for_path()).
    size_t max_readahead_size() const noexcept { return _max_readahead_size.load(std::memory_order_relaxed); }

    static MmapFileAllocatorFactory& instance();
};

} // namespace vespalib::alloc

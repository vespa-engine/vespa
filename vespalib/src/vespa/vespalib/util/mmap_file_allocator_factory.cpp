// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mmap_file_allocator_factory.h"

#include "mmap_file_allocator.h"
#include "will_need_advisor.h"

#include <vespa/vespalib/stllike/asciistream.h>

#include <filesystem>

namespace vespalib::alloc {

MmapFileAllocatorFactory::MmapFileAllocatorFactory()
    : _dir_name(), _generation(0), _max_readahead_size(WillNeedAdvisor::default_max_range_size) {
}

MmapFileAllocatorFactory::~MmapFileAllocatorFactory() = default;

void MmapFileAllocatorFactory::setup(const std::string& dir_name) {
    _dir_name = dir_name;
    _generation = 0;
    size_t max_readahead_size = WillNeedAdvisor::default_max_range_size;
    if (!_dir_name.empty()) {
        std::filesystem::remove_all(std::filesystem::path(_dir_name));
        max_readahead_size = WillNeedAdvisor::max_range_size_for_path(_dir_name);
    }
    _max_readahead_size.store(max_readahead_size, std::memory_order_relaxed);
}

std::unique_ptr<MemoryAllocator> MmapFileAllocatorFactory::make_memory_allocator(const std::string& name) {
    if (_dir_name.empty()) {
        return {};
    }
    vespalib::asciistream os;
    os << _dir_name << "/" << _generation.fetch_add(1) << "." << name;
    return std::make_unique<MmapFileAllocator>(os.str());
};

MmapFileAllocatorFactory& MmapFileAllocatorFactory::instance() {
    static MmapFileAllocatorFactory instance;
    return instance;
}

} // namespace vespalib::alloc

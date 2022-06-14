// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mmap_file_allocator_factory.h"
#include "mmap_file_allocator.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <filesystem>

namespace vespalib::alloc {

MmapFileAllocatorFactory::MmapFileAllocatorFactory()
    : _dir_name(),
      _generation(0)
{
}

MmapFileAllocatorFactory::~MmapFileAllocatorFactory()
{
}

void
MmapFileAllocatorFactory::setup(const vespalib::string& dir_name)
{
    _dir_name = dir_name;
    _generation = 0;
    if (!_dir_name.empty()) {
        std::filesystem::remove_all(std::filesystem::path(_dir_name));
    }
}

std::unique_ptr<MemoryAllocator>
MmapFileAllocatorFactory::make_memory_allocator(const vespalib::string& name)
{
    if (_dir_name.empty()) {
        return {};
    }
    vespalib::asciistream os;
    os << _dir_name << "/" << _generation.fetch_add(1) << "." << name;
    return std::make_unique<MmapFileAllocator>(os.str());
};

MmapFileAllocatorFactory&
MmapFileAllocatorFactory::instance()
{
    static MmapFileAllocatorFactory instance;
    return instance;
}

}

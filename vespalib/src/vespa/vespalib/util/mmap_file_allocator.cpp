// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mmap_file_allocator.h"
#include "round_up_to_page_size.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <fcntl.h>
#include <sys/mman.h>
#include <cassert>
#include <filesystem>

namespace vespalib::alloc {

MmapFileAllocator::MmapFileAllocator(const vespalib::string& dir_name)
    : _dir_name(dir_name),
      _file(_dir_name + "/swapfile"),
      _end_offset(0),
      _allocations(),
      _freelist()
{
    std::filesystem::create_directories(std::filesystem::path(_dir_name));
    _file.open(O_RDWR | O_CREAT | O_TRUNC, false);
}

MmapFileAllocator::~MmapFileAllocator()
{
    assert(_allocations.empty());
    _file.close();
    _file.unlink();
    std::filesystem::remove_all(std::filesystem::path(_dir_name));
}

uint64_t
MmapFileAllocator::alloc_area(size_t sz) const
{
    uint64_t offset = _freelist.alloc(sz);
    if (offset != FileAreaFreeList::bad_offset) {
        return offset;
    }
    offset = _end_offset;
    _end_offset += sz;
    _file.resize(_end_offset);
    return offset;
}

MmapFileAllocator::PtrAndSize
MmapFileAllocator::alloc(size_t sz) const
{
    if (sz == 0) {
        return PtrAndSize(nullptr, 0); // empty allocation
    }
    sz = round_up_to_page_size(sz);
    uint64_t offset = alloc_area(sz);
    void *buf = mmap(nullptr, sz,
                     PROT_READ | PROT_WRITE,
                     MAP_SHARED,
                     _file.getFileDescriptor(),
                     offset);
    assert(buf != MAP_FAILED);
    assert(buf != nullptr);
    // Register allocation
    auto ins_res = _allocations.insert(std::make_pair(buf, SizeAndOffset(sz, offset)));
    assert(ins_res.second);
    int retval = madvise(buf, sz, MADV_RANDOM);
    assert(retval == 0);
    return PtrAndSize(buf, sz);
}


void
MmapFileAllocator::free(PtrAndSize alloc) const
{
    if (alloc.second == 0) {
        assert(alloc.first == nullptr);
        return; // empty allocation
    }
    assert(alloc.first != nullptr);
    // Check that matching allocation is registered
    auto itr = _allocations.find(alloc.first);
    assert(itr != _allocations.end());
    assert(itr->first == alloc.first);
    assert(itr->second.size == alloc.second);
    auto offset = itr->second.offset;
    _allocations.erase(itr);
    int retval = madvise(alloc.first, alloc.second, MADV_DONTNEED);
    assert(retval == 0);
    retval = munmap(alloc.first, alloc.second);
    assert(retval == 0);
    _freelist.free(offset, alloc.second);
}

size_t
MmapFileAllocator::resize_inplace(PtrAndSize, size_t) const
{
    return 0;
}

}

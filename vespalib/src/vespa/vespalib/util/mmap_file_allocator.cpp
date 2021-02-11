// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mmap_file_allocator.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <fcntl.h>
#include <sys/mman.h>
#include <cassert>

namespace vespalib::alloc {

namespace {

const size_t mmap_alignment = getpagesize();

size_t round_to_mmap_alignment(size_t size) {
    return ((size + (mmap_alignment - 1)) & ~(mmap_alignment - 1));
}

}

MmapFileAllocator::MmapFileAllocator(const vespalib::string& dir_name)
    : _dir_name(dir_name),
      _file(_dir_name + "/swapfile"),
      _end_offset(0)
{
    mkdir(_dir_name, true);
    _file.open(O_RDWR | O_CREAT | O_TRUNC, false);
}

MmapFileAllocator::~MmapFileAllocator()
{
    assert(_allocations.empty());
    _file.close();
    _file.unlink();
    rmdir(_dir_name, true);
}

MmapFileAllocator::PtrAndSize
MmapFileAllocator::alloc(size_t sz) const
{
    if (sz == 0) {
        return PtrAndSize(nullptr, 0); // empty allocation
    }
    uint64_t offset = _end_offset;
    sz = round_to_mmap_alignment(sz);
    _end_offset += sz;
    _file.resize(_end_offset);
    void *buf = mmap(nullptr, sz,
                     PROT_READ | PROT_WRITE,
                     MAP_SHARED,
                     _file.getFileDescriptor(),
                     offset);
    assert(buf != MAP_FAILED);
    assert(buf != nullptr);
    // Register allocation
    auto ins_res = _allocations.insert(std::make_pair(buf, sz));
    assert(ins_res.second);
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
    assert(itr->second == alloc.second);
    _allocations.erase(itr);
    int retval = madvise(alloc.first, alloc.second, MADV_DONTNEED);
    assert(retval == 0);
    retval = munmap(alloc.first, alloc.second);
    assert(retval == 0);
}

size_t
MmapFileAllocator::resize_inplace(PtrAndSize, size_t) const
{
    return 0;
}

}

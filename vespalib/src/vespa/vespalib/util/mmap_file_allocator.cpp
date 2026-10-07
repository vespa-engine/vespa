// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mmap_file_allocator.h"

#include "exceptions.h"
#include "round_up_to_page_size.h"
#include "stringfmt.h"

#include <vespa/check_require.h>

#include <vespa/vespalib/stllike/hash_map.hpp>

#include <fcntl.h>
#include <sys/mman.h>

#include <filesystem>

using vespalib::make_string_short::fmt;
namespace fs = std::filesystem;

namespace vespalib::alloc {

MmapFileAllocator::MmapFileAllocator(std::string dir_name)
    : MmapFileAllocator(std::move(dir_name), default_small_limit, default_premmap_size) {
}

MmapFileAllocator::MmapFileAllocator(std::string dir_name, uint32_t small_limit, uint32_t premmap_size)
    : _dir_name(std::move(dir_name)),
      _small_limit(small_limit),
      _premmap_size(premmap_size),
      _file(_dir_name + "/swapfile"),
      _end_offset(0),
      _allocations(),
      _freelist(),
      _small_allocations(),
      _small_freelist(),
      _premmapped_areas() {
    fs::create_directories(fs::path(_dir_name));
    _file.open(O_RDWR | O_CREAT | O_TRUNC, false);
}

MmapFileAllocator::~MmapFileAllocator() {
    CHECK(_small_allocations.empty());
    CHECK(_allocations.size() == _premmapped_areas.size());
    for (auto& area : _premmapped_areas) {
        auto offset = area.first;
        auto ptr = area.second;
        auto itr = _allocations.find(ptr);
        CHECK(itr != _allocations.end());
        CHECK(itr->first == ptr);
        CHECK(itr->second.offset == offset);
        auto size = itr->second.size;
        _small_freelist.remove_premmapped_area(offset, size);
        free_large({ptr, size});
    }
    _premmapped_areas.clear();
    CHECK(_allocations.empty());
    _file.close();
    _file.unlink();
    fs::remove_all(fs::path(_dir_name));
}

uint64_t MmapFileAllocator::alloc_area(size_t sz) const {
    uint64_t offset = _freelist.alloc(sz);
    if (offset != FileAreaFreeList::bad_offset) {
        return offset;
    }
    offset = _end_offset.load(std::memory_order_relaxed);
    _end_offset.store(offset + sz, std::memory_order_relaxed);
    _file.resize(offset + sz);
    return offset;
}

PtrAndSize MmapFileAllocator::alloc(size_t sz) const {
    if (sz == 0) {
        return {}; // empty allocation
    }
    static constexpr size_t alignment = 128;
    sz = (sz + alignment - 1) & -alignment; // round sz to a multiple of alignment
    if (sz >= _small_limit) {
        return alloc_large(sz);
    } else {
        return alloc_small(sz);
    }
}

PtrAndSize MmapFileAllocator::alloc_large(size_t sz) const {
    sz = round_up_to_page_size(sz);
    uint64_t offset = alloc_area(sz);
    void*    buf = mmap(nullptr, sz, PROT_READ | PROT_WRITE, MAP_SHARED, _file.getFileDescriptor(), offset);
    if (buf == MAP_FAILED) {
        throw IoException(fmt("Failed mmap(nullptr, %zu, PROT_READ | PROT_WRITE, MAP_SHARED, %s(fd=%d), %" PRIu64
                              "). Reason given by OS = '%s'",
                              sz, _file.getFilename().c_str(), _file.getFileDescriptor(), offset,
                              getLastErrorString().c_str()),
                          IoException::getErrorType(errno), VESPA_STRLOC);
    }
    CHECK(buf != nullptr);
    // Register allocation
    auto ins_res = _allocations.insert(std::make_pair(buf, SizeAndOffset(sz, offset)));
    CHECK(ins_res.second);
    int retval = madvise(buf, sz, MADV_RANDOM);
    CHECK(retval == 0);
#ifdef __linux__
    retval = madvise(buf, sz, MADV_DONTDUMP);
    CHECK(retval == 0);
#endif
    return {buf, sz};
}

void* MmapFileAllocator::map_premapped_offset_to_ptr(uint64_t offset, size_t size) const {
    auto itr = _premmapped_areas.lower_bound(offset);
    if (itr == _premmapped_areas.end() || itr->first > offset) {
        CHECK(itr != _premmapped_areas.begin());
        --itr;
    }
    auto aitr = _allocations.find(itr->second);
    CHECK(aitr != _allocations.end());
    CHECK(aitr->first == itr->second);
    CHECK(offset >= aitr->second.offset);
    CHECK(offset + size <= aitr->second.offset + aitr->second.size);
    return static_cast<char*>(itr->second) + (offset - aitr->second.offset);
}

PtrAndSize MmapFileAllocator::alloc_small(size_t sz) const {
    uint64_t offset = _small_freelist.alloc(sz);
    if (offset == FileAreaFreeList::bad_offset) {
        auto new_premmap = alloc_large(_premmap_size);
        CHECK(new_premmap.size() >= _premmap_size);
        auto itr = _allocations.find(new_premmap.get());
        CHECK(itr != _allocations.end());
        CHECK(itr->first == new_premmap.get());
        _small_freelist.add_premmapped_area(itr->second.offset, itr->second.size);
        auto ins_res = _premmapped_areas.emplace(itr->second.offset, new_premmap.get());
        CHECK(ins_res.second);
        offset = _small_freelist.alloc(sz);
        CHECK(offset != FileAreaFreeList::bad_offset);
    }
    auto ptr = map_premapped_offset_to_ptr(offset, sz);
    // Register allocation
    auto ins_res = _small_allocations.insert(std::make_pair(ptr, SizeAndOffset(sz, offset)));
    CHECK(ins_res.second);
    return {ptr, sz};
}

void MmapFileAllocator::free(PtrAndSize alloc) const noexcept {
    if (alloc.size() == 0) {
        CHECK(alloc.get() == nullptr);
        return; // empty allocation
    }
    CHECK(alloc.get() != nullptr);
    if (alloc.size() >= _small_limit) {
        free_large(alloc);
    } else {
        free_small(alloc);
    }
}

uint64_t MmapFileAllocator::remove_allocation(PtrAndSize alloc, Allocations& allocations) const noexcept {
    // Check that matching allocation is registered
    auto itr = allocations.find(alloc.get());
    CHECK(itr != allocations.end());
    CHECK(itr->first == alloc.get());
    CHECK(itr->second.size == alloc.size());
    auto offset = itr->second.offset;
    allocations.erase(itr);
    return offset;
}

void MmapFileAllocator::free_large(PtrAndSize alloc) const noexcept {
    auto offset = remove_allocation(alloc, _allocations);
    int  retval = madvise(alloc.get(), alloc.size(), MADV_DONTNEED);
    CHECK(retval == 0);
    retval = munmap(alloc.get(), alloc.size());
    CHECK(retval == 0);
    _freelist.free(offset, alloc.size());
}

void MmapFileAllocator::free_small(PtrAndSize alloc) const noexcept {
    auto offset = remove_allocation(alloc, _small_allocations);
    _small_freelist.free(offset, alloc.size());
}

size_t MmapFileAllocator::resize_inplace(PtrAndSize, size_t) const {
    return 0;
}

uint64_t MmapFileAllocator::get_size_on_disk() const noexcept {
    constexpr uint32_t directory_placeholder_size = 4_Ki;
    return directory_placeholder_size + _end_offset.load(std::memory_order_relaxed);
}

} // namespace vespalib::alloc

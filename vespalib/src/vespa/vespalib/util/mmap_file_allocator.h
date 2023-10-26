// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory_allocator.h"
#include "file_area_freelist.h"
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/size_literals.h>
#include <map>

namespace vespalib::alloc {

/*
 * Class handling memory allocations backed by one or more files.
 * Not reentrant or thread safe. Should not be destructed before all allocations
 * have been freed.
 *
 * Memory allocations smaller than _small_limit use portions of
 * premmapped areas to reduce the total number of memory mappings.
 */
class MmapFileAllocator : public MemoryAllocator {
    struct SizeAndOffset {
        size_t   size;
        uint64_t offset;
        SizeAndOffset()
            : SizeAndOffset(0u, 0u)
        {
        }
        SizeAndOffset(size_t size_in, uint64_t offset_in)
            : size(size_in),
              offset(offset_in)
        {
        }
    };
    using Allocations = hash_map<void *, SizeAndOffset>;
    const vespalib::string _dir_name;
    const uint32_t   _small_limit;
    const uint32_t   _premmap_size;
    mutable File     _file;
    mutable uint64_t _end_offset;
    mutable Allocations _allocations;
    mutable FileAreaFreeList _freelist;
    mutable Allocations _small_allocations;
    mutable FileAreaFreeList _small_freelist;
    mutable std::map<uint64_t, void*> _premmapped_areas;
    uint64_t alloc_area(size_t sz) const;
    PtrAndSize alloc_large(size_t size) const;
    PtrAndSize alloc_small(size_t size) const;
    void free_large(PtrAndSize alloc) const noexcept;
    void free_small(PtrAndSize alloc) const noexcept;
    void* map_premapped_offset_to_ptr(uint64_t offset, size_t size) const;
    uint64_t remove_allocation(PtrAndSize alloc, Allocations& allocations) const noexcept;
public:
    static constexpr uint32_t default_small_limit =  128_Ki;
    static constexpr uint32_t default_premmap_size = 1_Mi;
    MmapFileAllocator(const vespalib::string& dir_name);
    MmapFileAllocator(const vespalib::string& dir_name, uint32_t small_limit, uint32_t premmap_size);
    ~MmapFileAllocator();
    PtrAndSize alloc(size_t sz) const override;
    void free(PtrAndSize alloc) const noexcept override;
    size_t resize_inplace(PtrAndSize, size_t) const override;

    // For unit test
    size_t get_end_offset() const noexcept { return _end_offset; }
};

}

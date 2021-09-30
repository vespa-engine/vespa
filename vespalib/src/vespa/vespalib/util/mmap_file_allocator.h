// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory_allocator.h"
#include "file_area_freelist.h"
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/string.h>
#include <map>

namespace vespalib::alloc {

/*
 * Class handling memory allocations backed by one or more files.
 * Not reentant. Should not be destructed before all allocations
 * have been freed.
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
    vespalib::string _dir_name;
    mutable File _file;
    mutable uint64_t _end_offset;
    mutable hash_map<void *, SizeAndOffset> _allocations;
    mutable FileAreaFreeList _freelist;
    uint64_t alloc_area(size_t sz) const;
public:
    MmapFileAllocator(const vespalib::string& dir_name);
    ~MmapFileAllocator();
    PtrAndSize alloc(size_t sz) const override;
    void free(PtrAndSize alloc) const override;
    size_t resize_inplace(PtrAndSize, size_t) const override;
    
    // For unit test
    size_t get_end_offset() const noexcept { return _end_offset; }
};

}

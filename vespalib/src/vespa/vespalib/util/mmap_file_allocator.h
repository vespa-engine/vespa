// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "alloc.h"
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/string.h>
#include <map>

namespace vespalib::alloc {

/*
 * Class handling memory allocations backed by one or more files.
 * Not reentant. Should not be destructed before all allocations
 * have been freed.
 */
class MmapFileAllocator : public MemoryAllocator {
    vespalib::string _dir_name;
    mutable File _file;
    mutable uint64_t _end_offset;
    mutable std::map<void *, size_t> _allocations;
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

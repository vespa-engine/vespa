// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/memory_allocator.h>
#include <iosfwd>

namespace vespalib::alloc::test {

/*
 * Instrumented memory allocator proxy which observes usage.
 */
class MemoryAllocatorObserver : public MemoryAllocator {
public:
    struct Stats {
        size_t alloc_cnt;
        size_t free_cnt;
        Stats()
            : Stats(0, 0)
        {
        }
        Stats(size_t alloc_cnt_in, size_t free_cnt_in)
            : alloc_cnt(alloc_cnt_in),
              free_cnt(free_cnt_in)
        {
        }
        bool operator==(const Stats &rhs) const {
            return ((alloc_cnt == rhs.alloc_cnt) &&
                    (free_cnt == rhs.free_cnt));
        }
    };

private:
    Stats &_stats;
    const alloc::MemoryAllocator* _backing_allocator;
public:
    MemoryAllocatorObserver(Stats &stats);
    ~MemoryAllocatorObserver() override;
    PtrAndSize alloc(size_t sz) const override;
    void free(PtrAndSize alloc) const override;
    size_t resize_inplace(PtrAndSize current, size_t newSize) const override;
};

std::ostream& operator<<(std::ostream &os, const MemoryAllocatorObserver::Stats &stats);

}

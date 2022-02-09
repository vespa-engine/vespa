// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <thread>

namespace vespalib {

/**
 * Provides a hint to malloc implementation that all allocations in the scope of this guard
 * will use mmap directly for allocation larger than the givem limit.
 * The effect is implementation dependent. vespamalloc applies this only for the calling thread.
 **/
class MallocMmapGuard
{
public:
    MallocMmapGuard(size_t mmapLimit);
    MallocMmapGuard(const MallocMmapGuard &) = delete;
    MallocMmapGuard & operator=(const MallocMmapGuard &) = delete;
    MallocMmapGuard(MallocMmapGuard &&) = delete;
    MallocMmapGuard & operator=(MallocMmapGuard &&) = delete;
    ~MallocMmapGuard();
private:
    std::thread::id _threadId;
};

} // namespace vespalib


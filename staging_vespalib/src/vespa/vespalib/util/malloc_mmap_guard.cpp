// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "malloc_mmap_guard.h"
#include <vespa/vespalib/util/size_literals.h>
#ifdef __linux__
#include <malloc.h>
#endif
#include <limits>
#include <cassert>

namespace vespalib {

MallocMmapGuard::MallocMmapGuard(size_t mmapLimit) :
    _threadId(std::this_thread::get_id())
{
#ifdef __linux__
    int limit = mmapLimit <= std::numeric_limits<int>::max() ? mmapLimit : std::numeric_limits<int>::max();
    mallopt(M_MMAP_THRESHOLD, limit);
#else
    (void) mmapLimit;
#endif
}

MallocMmapGuard::~MallocMmapGuard()
{
    assert(_threadId == std::this_thread::get_id());
#ifdef __linux__
    mallopt(M_MMAP_THRESHOLD, 1_Gi);
#endif
}

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespamalloc/malloc/mallocd.h>
#include <vespamalloc/malloc/memblockboundscheck_dst.h>

namespace vespamalloc {

typedef ThreadListT<MemBlockBoundsCheck, Stat> ThreadList;
typedef MemoryWatcher<MemBlockBoundsCheck, ThreadList> Allocator;

static char _Gmem[sizeof(Allocator)];
static Allocator *_GmemP = nullptr;

template <size_t MaxSizeClassMultiAllocC, size_t StackTraceLen>
void MemBlockBoundsCheckBaseT<MaxSizeClassMultiAllocC, StackTraceLen>::dumpInfo(size_t level)
{
    fprintf(_logFile, "mallocdst dumping at level %ld\n", level);
    _GmemP->info(_logFile, level);
}

}


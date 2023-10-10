// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespamalloc/malloc/mallocd.h>
#include <vespamalloc/malloc/memblockboundscheck_d.h>

namespace vespamalloc {

typedef ThreadListT<MemBlockBoundsCheck, Stat> ThreadList;
typedef MemoryWatcher<MemBlockBoundsCheck, ThreadList> Allocator;

static char _Gmem[sizeof(Allocator)];
static Allocator *_GmemP = nullptr;

static Allocator * createAllocator()
{
    if (_GmemP == nullptr) {
        _GmemP = new (_Gmem) Allocator(-1, 0x7fffffffffffffffl);
    }
    return _GmemP;
}

template <size_t MaxSizeClassMultiAllocC, size_t StackTraceLen>
void MemBlockBoundsCheckBaseT<MaxSizeClassMultiAllocC, StackTraceLen>::
dumpInfo(size_t level)
{
    _GmemP->info(_logFile, level);
}

template void MemBlockBoundsCheckBaseT<20, 0>::dumpInfo(size_t);

}

extern "C" {

int is_vespamallocd() __attribute__((visibility ("default")));
int is_vespamallocd() { return 1; }

}

#include <vespamalloc/malloc/overload.h>

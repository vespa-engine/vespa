// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespamalloc/malloc/malloc.h>
#include <vespamalloc/malloc/memorywatcher.h>
#include <vespamalloc/malloc/memblock.h>
#include <vespamalloc/malloc/stat.h>
#include <vespamalloc/malloc/threadpool.hpp>

namespace vespamalloc {

typedef ThreadListT<MemBlock, NoStat> ThreadList;
typedef MemoryWatcher<MemBlock, ThreadList> Allocator;

static char _Gmem[sizeof(Allocator)];
static Allocator *_GmemP = nullptr;

static Allocator * createAllocator()
{
    if (_GmemP == nullptr) {
        _GmemP = (Allocator *)1;
        _GmemP = new (_Gmem) Allocator(-1, 0x7fffffffffffffffl);
    }
    return _GmemP;
}

template <>
void MemBlock::
dumpInfo(size_t level)
{
    _GmemP->info(_G_logFile, level);
}

}

extern "C" {

int is_vespamalloc() __attribute__((visibility ("default")));
int is_vespamalloc() { return 1; }

// Exported symbol used by state explorer to detect vespamalloc presence and dump its info.
void vespamalloc_dump_info(FILE* out_file) __attribute__((visibility("default")));
void vespamalloc_dump_info(FILE* out_file) {
    constexpr int log_level = 2;
    vespamalloc::_GmemP->info(out_file, log_level);
}

}

#include <vespamalloc/malloc/overload.h>

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespamalloc/malloc/mallocdst.h>

namespace vespamalloc {

static Allocator * createAllocator()
{
    if (_GmemP == NULL) {
        _GmemP = new (_Gmem) Allocator(1, 0x7fffffffffffffffl);
    }
    return _GmemP;
}

template void MemBlockBoundsCheckBaseT<20, 16>::dumpInfo(size_t);

}

extern "C" {

int is_vespamallocdst16_nl() __attribute__((visibility ("default")));
int is_vespamallocdst16_nl() { return 1; }

}

#include <vespamalloc/malloc/overload.h>

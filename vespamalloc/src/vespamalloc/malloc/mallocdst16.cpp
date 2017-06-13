// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespamalloc/malloc/mallocdst.h>

namespace vespamalloc {

static Allocator * createAllocator()
{
    if (_GmemP == NULL) {
        _GmemP = new (_Gmem) Allocator(1, 0x200000);
    }
    return _GmemP;
}

class DumpAtEnd
{
public:
    ~DumpAtEnd();
};

DumpAtEnd::~DumpAtEnd()
{
    fprintf(stderr, "mallocdst dumping at end\n");
    _GmemP->info(stderr, 2);
}

static DumpAtEnd _Gdumper;

template void MemBlockBoundsCheckBaseT<20, 16>::dumpInfo(size_t);

}

#include <vespamalloc/malloc/overload.h>

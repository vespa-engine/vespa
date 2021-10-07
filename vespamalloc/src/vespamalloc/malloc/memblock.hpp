// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespamalloc/malloc/memblock.h>

namespace vespamalloc {

template <size_t MinSizeClassC, size_t MaxSizeClassMultiAllocC>
void
MemBlockT<MinSizeClassC, MaxSizeClassMultiAllocC>::logBigBlock(size_t exact, size_t adjusted, size_t gross) const
{
    size_t sz(exact);
    if (std::max(std::max(sz, adjusted), gross) > _bigBlockLimit) {
        Stack st[32];
        size_t count = Stack::fillStack(st, NELEMS(st));
        fprintf(_logFile, "validating %p(%ld, %ld, %ld)",
                ptr(), sz, adjusted, gross);
        st[3].info(_logFile);
        fprintf(_logFile, "\n");
        for(size_t i=1; (i < count) && (i < NELEMS(st)); i++) {
            const Stack & s = st[i];
            if (s.valid()) {
                s.info(_logFile);
                fprintf(_logFile, " from ");
            }
        }
        fprintf(_logFile, "\n");
    }
}

template <size_t MinSizeClassC, size_t MaxSizeClassMultiAllocC>
void
MemBlockT<MinSizeClassC, MaxSizeClassMultiAllocC>::bigBlockLimit(size_t lim)
{
    _bigBlockLimit = lim;
}

template <size_t MinSizeClassC, size_t MaxSizeClassMultiAllocC>
FILE * MemBlockT<MinSizeClassC, MaxSizeClassMultiAllocC>::_logFile = stderr;
template <size_t MinSizeClassC, size_t MaxSizeClassMultiAllocC>
size_t MemBlockT<MinSizeClassC, MaxSizeClassMultiAllocC>::_bigBlockLimit = 0x80000000;

}


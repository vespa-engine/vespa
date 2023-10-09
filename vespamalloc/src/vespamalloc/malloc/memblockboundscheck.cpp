// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#ifndef _VESPAMALLOC_MALLOC_MEMBLOCKBOUNDSCHECK_HPP_
#define _VESPAMALLOC_MALLOC_MEMBLOCKBOUNDSCHECK_HPP_

#include <vespamalloc/malloc/memblockboundscheck.h>

namespace vespamalloc {

FILE *  MemBlockBoundsCheckBaseTBase::_logFile = stderr;
size_t  MemBlockBoundsCheckBaseTBase::_bigBlockLimit = 0x80000000;
uint8_t MemBlockBoundsCheckBaseTBase::_fillValue = MemBlockBoundsCheckBaseTBase::NO_FILL;

void MemBlockBoundsCheckBaseTBase::verifyFill() const
{
    const uint8_t *c(static_cast<const uint8_t *>(ptr())), *e(c+size());
    for(;(c < e) && (*c == _fillValue); c++) { }
    if (c != e) {
        fprintf(_logFile, "Incorrect fillvalue (%2x) instead of (%2x) at position %ld(%p) of %ld(%p - %p)\n",
                *c, _fillValue, c - static_cast<const uint8_t *>(ptr()), c, size(), ptr(), e);
        abort();
    }
}

void MemBlockBoundsCheckBaseTBase::logBigBlock(size_t exact, size_t adjusted, size_t gross) const
{
    size_t sz(exact);
    if (sz > _bigBlockLimit) {
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


} // namespace vespamalloc

#endif

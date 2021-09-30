// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespamalloc/malloc/memblockboundscheck.h>

namespace vespamalloc {

template <size_t MaxSizeClassMultiAllocC, size_t StackTraceLen>
void MemBlockBoundsCheckBaseT<MaxSizeClassMultiAllocC, StackTraceLen>::info(FILE * os, unsigned level) const
{
    if (validCommon()) {
        if (level & 0x02) {
            fprintf(os, "{ %8p(%ld, %u) ", ptr(), size(), threadId());
            const Stack * cStack = callStack();
            for (int i=0; i<int(StackTraceLen);i++) {
                if (cStack[i].valid()) {
                    cStack[i].info(os);
                    fprintf(os, " ");
                }
            }
            fprintf(os, " }");
        }
        if (level & 0x01) {
            fprintf(os, " %8p(%ld, %u)", ptr(), size(), threadId());
        }
        if (level == 0) {
            fprintf(os, " %8p(%ld)", ptr(), size());
        }
    }
}

} // namespace vespamalloc


// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "sort.h"

namespace search {

bool radix_prepare(unsigned int n, unsigned int last[257], unsigned int ptr[256], unsigned int cnt[256])
{
    // Accumulate cnt positions
    bool sorted = (cnt[0]==n);
    ptr[0] = 0;
    for(unsigned int i(1); i<256; i++) {
        ptr[i] = ptr[i-1] + cnt[i-1];
        sorted |= (cnt[i]==n);
    }
    memcpy(last, ptr, 256*sizeof(unsigned int));
    last[256] = last[255] + cnt[255];
    return sorted;
}

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "sort.h"

namespace search {

bool
radix_prepare(size_t n, size_t last[257], size_t ptr[256], size_t cnt[256])
{
    // Accumulate cnt positions
    bool sorted = (cnt[0]==n);
    ptr[0] = 0;
    for(unsigned int i(1); i<256; i++) {
        ptr[i] = ptr[i-1] + cnt[i-1];
        sorted |= (cnt[i]==n);
    }
    memcpy(last, ptr, 256*sizeof(last[0]));
    last[256] = last[255] + cnt[255];
    return sorted;
}

}

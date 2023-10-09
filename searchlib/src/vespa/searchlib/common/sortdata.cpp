// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sortdata.h"
#include <cassert>
#include <cstring>

namespace search {
namespace common {

uint32_t
SortData::GetSize(uint32_t hitcnt, const uint32_t *sortIndex)
{
    if (hitcnt == 0)
        return 0;
    return ((hitcnt + 1) * sizeof(uint32_t)
            + (sortIndex[hitcnt] - sortIndex[0]));
}


bool
SortData::Equals(uint32_t hitcnt,
                 const uint32_t *sortIndex_1, const char *sortData_1,
                 const uint32_t *sortIndex_2, const char *sortData_2)
{
    if (hitcnt == 0)
        return true;
    uint32_t diff = sortIndex_2[0] - sortIndex_1[0];
    for (uint32_t i = 1; i <= hitcnt; i++) {
        if (diff != (sortIndex_2[i] - sortIndex_1[i]))
            return false;
    }
    assert((sortIndex_1[hitcnt] - sortIndex_1[0]) ==
                 (sortIndex_2[hitcnt] - sortIndex_2[0]));
    return (memcmp(sortData_1 + sortIndex_1[0],
                   sortData_2 + sortIndex_2[0],
                   sortIndex_1[hitcnt] - sortIndex_1[0]) == 0);
}


void
SortData::Copy(uint32_t hitcnt,
               uint32_t *sortIndex_dst, char *sortData_dst,
               const uint32_t *sortIndex_src, const char *sortData_src)
{
    if (hitcnt == 0)
        return;
    uint32_t diff = sortIndex_dst[0] - sortIndex_src[0];
    for (uint32_t i = 1; i <= hitcnt; i++) {
        sortIndex_dst[i] = sortIndex_src[i] + diff;
    }
    assert((sortIndex_dst[hitcnt] - sortIndex_dst[0]) ==
                 (sortIndex_src[hitcnt] - sortIndex_src[0]));
    memcpy(sortData_dst + sortIndex_dst[0],
           sortData_src + sortIndex_src[0],
           sortIndex_dst[hitcnt] - sortIndex_dst[0]);
}

bool
SortDataIterator::Before(SortDataIterator *other, bool beforeOnMatch)
{
    uint32_t tlen = GetLen();
    uint32_t olen = other->GetLen();
    uint32_t mlen = (tlen <= olen) ? tlen : olen;

    if (mlen == 0)
        return (tlen != 0 || beforeOnMatch);

    int res = memcmp(GetBuf(), other->GetBuf(), mlen);

    if (res != 0)
        return (res < 0);
    return (tlen < olen || (tlen == olen && beforeOnMatch));
}

}
}

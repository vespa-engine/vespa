// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_params.h"
#include <algorithm>

namespace proton::matching {

namespace {

uint32_t computeArraySize(uint32_t hitsPlussOffset, uint32_t heapSize, uint32_t arraySize)
{
    return std::max(hitsPlussOffset, std::max(heapSize, arraySize));
}

}

MatchParams::MatchParams(uint32_t          numDocs_in,
                         uint32_t          heapSize_in,
                         uint32_t          arraySize_in,
                         search::feature_t rankDropLimit_in,
                         uint32_t          offset_in,
                         uint32_t          hits_in,
                         bool              hasFinalRank,
                         bool              needRanking)
    : numDocs(numDocs_in),
      heapSize((hasFinalRank && needRanking) ? heapSize_in : 0),
      arraySize((needRanking && ((heapSize_in + arraySize_in) > 0))
                ? computeArraySize(hits_in + offset_in, heapSize, arraySize_in)
                : 0),
      offset(offset_in),
      hits(hits_in),
      rankDropLimit(rankDropLimit_in)
{ }

}

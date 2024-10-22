// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "postinglistcounts.h"

namespace search::index {

/**
 * The result after performing a disk dictionary lookup.
 **/
class DictionaryLookupResult {
public:
    uint64_t wordNum;
    index::PostingListCounts counts;
    uint64_t bitOffset;

    DictionaryLookupResult() noexcept;
    ~DictionaryLookupResult();

    bool valid() const noexcept { return counts._numDocs > 0; }

    void swap(DictionaryLookupResult &rhs) noexcept {
        std::swap(wordNum, rhs.wordNum);
        counts.swap(rhs.counts);
        std::swap(bitOffset, rhs.bitOffset);
    }
};

void swap(DictionaryLookupResult& a, DictionaryLookupResult& b) noexcept;

}

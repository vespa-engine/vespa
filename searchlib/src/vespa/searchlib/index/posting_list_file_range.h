// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace search::index {

/*
 * Range of a posting list file used for posting list. Might include padding
 * at start and end due to file format. Offsets are in bytes.
 */
struct PostingListFileRange {
    uint64_t start_offset;
    uint64_t end_offset;

    PostingListFileRange(uint64_t start_offset_in, uint64_t end_offset_in)
        : start_offset(start_offset_in),
          end_offset(end_offset_in)
    {
    }
    uint64_t size() const noexcept { return end_offset - start_offset; }
};

}

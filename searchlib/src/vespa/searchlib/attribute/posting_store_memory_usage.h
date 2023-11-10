// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/memoryusage.h>

namespace search::attribute {

/**
 * Memory usage for a posting store with details for each type of posting list.
 */
struct PostingStoreMemoryUsage {
    vespalib::MemoryUsage btrees;
    vespalib::MemoryUsage short_arrays;
    vespalib::MemoryUsage bitvectors;
    vespalib::MemoryUsage total;
    PostingStoreMemoryUsage(vespalib::MemoryUsage btrees_in,
                            vespalib::MemoryUsage short_arrays_in,
                            vespalib::MemoryUsage bitvectors_in)
        : btrees(btrees_in),
          short_arrays(short_arrays_in),
          bitvectors(bitvectors_in),
          total()
    {
        total.merge(btrees);
        total.merge(short_arrays);
        total.merge(bitvectors);
    }

};

}


// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::fef {

/*
 * Struct containing the raw data used to calculate significance.
 */
struct DocumentFrequency {
    uint64_t frequency; // number of documents containing the word
    uint64_t count;     // total number of documents

    DocumentFrequency(uint64_t document_frequency_in, uint64_t document_count_in)
            : frequency(document_frequency_in),
              count(document_count_in)
    {
    }
    bool operator==(const DocumentFrequency& rhs) const noexcept {
        return frequency == rhs.frequency && count == rhs.count;
    }
};

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::features::util {

/*
 * Struct containing the raw data used to calculate significance.
 */
struct DocumentFrequency {
    uint64_t document_frequency; // number of documents containing the word
    uint64_t document_count;     // total number of documents

    DocumentFrequency(uint64_t document_frequency_in, uint64_t document_count_in)
            : document_frequency(document_frequency_in),
              document_count(document_count_in)
    {
    }
    bool operator==(const DocumentFrequency& rhs) const noexcept {
        return document_frequency == rhs.document_frequency && document_count == rhs.document_count;
    }
};

}

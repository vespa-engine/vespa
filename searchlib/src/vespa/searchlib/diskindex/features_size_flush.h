// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <string>

namespace search::index { class PostingListParams; }

namespace search::diskindex {

/*
 * A marker value for number of documents indicating changed file format to handle flushing due to feature size.
 * When this value is read, the real number of documents follows, but further decoding is slightly adjusted.
 *
 * A posting list on disk starts with the number of documents in the posting list chunk.
 * When encountering the marker, the "has more"-bit is always read, and variant with skip info is always selected.
 *
 * A posting list counts entry in the dictionary starts with the number of documents for the word.
 * When encountering this marker, the number of chunks for the counts entry is always read.
 */
constexpr uint32_t features_size_flush_marker = 0xfffffff0;

// Limits posting list chunks to 1 document each, for corner case testing.
constexpr bool force_features_size_flush_always = false;

void setup_default_features_size_flush(index::PostingListParams& params);

namespace tags {

extern std::string FEATURES_SIZE_FLUSH_BITS;

}

}

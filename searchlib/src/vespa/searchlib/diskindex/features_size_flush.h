// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <string>

namespace search::index { class PostingListParams; }

namespace search::diskindex {

/*
 * A posting list on disk normally starts with the number of documents in the posting list chunk.
 * When this value is read, the real number of documents in the posting list chunk follows, and file format
 * is slightly adjusted (has more bit is always read, and variant with skip info is always selected).
 */
constexpr uint32_t features_size_flush_marker = 0xfffffff0;

// Limits posting list chunks to 1 document each, for corner case testing.
constexpr bool force_features_size_flush_always = false;

void setup_default_features_size_flush(index::PostingListParams& params);

namespace tags {

extern std::string FEATURES_SIZE_FLUSH_BITS;

}

}

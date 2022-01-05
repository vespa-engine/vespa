// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket.h"
#include <memory>

namespace storage::spi { class DocEntry; }

namespace storage::spi::test {

// Helper functions used by unit tests

Bucket makeSpiBucket(document::BucketId bucketId);
std::unique_ptr<DocEntry> cloneDocEntry(const DocEntry & entry);
bool equal(const DocEntry & a, const DocEntry & b);

}

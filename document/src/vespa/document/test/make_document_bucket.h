// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucket.h>

namespace document::test {

// Helper function used by unit tests

Bucket makeDocumentBucket(BucketId bucketId);

}

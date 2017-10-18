// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucket.h>

namespace storage::test {

// Helper function used by unit tests

document::Bucket makeDocumentBucket(document::BucketId bucketId);

}

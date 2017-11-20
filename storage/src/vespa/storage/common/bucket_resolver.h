// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucket.h>

namespace document { class DocumentId; }

namespace storage {

/**
 * Interface for resolving which bucket a given a document id belongs to.
 */
struct BucketResolver {
    virtual ~BucketResolver() {}
    virtual document::Bucket bucketFromId(const document::DocumentId &documentId) const = 0;
};

}

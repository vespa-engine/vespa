// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucket.h>
#include <vespa/vespalib/stllike/string.h>

namespace document { class DocumentId; }

namespace storage {

/**
 * Interface for resolving which bucket a given a document id belongs to.
 */
struct BucketResolver {
    virtual ~BucketResolver() = default;
    virtual document::Bucket bucketFromId(const document::DocumentId &documentId) const = 0;
    virtual document::BucketSpace bucketSpaceFromName(const vespalib::string &bucketSpace) const = 0;
    virtual vespalib::string nameFromBucketSpace(const document::BucketSpace &bucketSpace) const = 0;
};

}

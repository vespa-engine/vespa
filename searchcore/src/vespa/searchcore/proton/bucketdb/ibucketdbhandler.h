// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/searchlib/common/serialnum.h>

namespace proton { struct IDocumentMetaStore; }

namespace proton::bucketdb {

/**
 * The IBucketDBHandler class handles operations on a bucket db.
 */
class IBucketDBHandler
{
public:
    using BucketId = document::BucketId;

    IBucketDBHandler() { }

    virtual ~IBucketDBHandler() { }

    virtual void handleSplit(search::SerialNum serialNum, const BucketId &source,
                             const BucketId &target1, const BucketId &target2) = 0;

    virtual void handleJoin(search::SerialNum serialNum, const BucketId &source1,
                            const BucketId &source2, const BucketId &target) = 0;

    virtual void handleCreateBucket(const BucketId &bucketId) = 0;
    virtual void handleDeleteBucket(const BucketId &bucketId) = 0;
};

}

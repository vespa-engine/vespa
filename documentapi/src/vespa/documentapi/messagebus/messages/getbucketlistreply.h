// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentreply.h"
#include <vespa/document/bucket/bucket.h>

namespace documentapi {

class GetBucketListReply : public DocumentReply {
public:
    class BucketInfo {
    public:
        document::BucketId _bucket;
        string        _bucketInformation;

        BucketInfo();
        BucketInfo(const document::BucketId &bucketId,
                   const string &bucketInformation);
        bool operator==(const BucketInfo &rhs) const;
    };

private:
    document::BucketSpace   _bucketSpace;
    std::vector<BucketInfo> _buckets;

public:
    /**
     * Constructs a new reply with no content.
     */
    GetBucketListReply(document::BucketSpace bucketSpace);

    /**
     * Returns the bucket state contained in this.
     *
     * @return The state object.
     */
    std::vector<BucketInfo> &getBuckets() { return _buckets; }

    /**
     * Returns a const reference to the bucket state contained in this.
     *
     * @return The state object.
     */
    const std::vector<BucketInfo> &getBuckets() const { return _buckets; }

    string toString() const override { return "getbucketlistreply"; }
};

std::ostream & operator<<(std::ostream &out, const GetBucketListReply::BucketInfo &info);

} // documentapi


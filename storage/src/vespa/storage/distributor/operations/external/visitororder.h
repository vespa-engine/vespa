// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <climits>
#include <vespa/document/bucket/bucketid.h>

namespace storage::distributor {

struct VisitorOrder {

    VisitorOrder() { }

    bool operator()(const document::BucketId& a, const document::BucketId& b) {
        if (a == document::BucketId(INT_MAX) ||
            b == document::BucketId(0, 0)) {
            return false; // All before max, non before null
        }
        if (a == document::BucketId(0, 0) ||
            b == document::BucketId(INT_MAX)) {
            return true; // All after null, non after max
        }
        return (a.toKey() < b.toKey()); // Reversed bucket id order
    }
};

}



// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketreply.h"
#include "bucketcommand.h"
#include <ostream>

using document::Bucket;
using document::BucketId;

namespace storage::api {

void
BucketReply::remapBucketId(const BucketId& bucket) {
    if (_originalBucket.getRawId() == 0) {
        _originalBucket = _bucket.getBucketId();
    }
    Bucket newBucket(_bucket.getBucketSpace(), bucket);
    _bucket = newBucket;
}

void
BucketReply::print(std::ostream& out, bool verbose,
                   const std::string& indent) const
{
    out << "BucketReply(" << _bucket.getBucketId();
    if (hasBeenRemapped()) {
        out << " <- " << _originalBucket;
    }
    out << ")";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

}

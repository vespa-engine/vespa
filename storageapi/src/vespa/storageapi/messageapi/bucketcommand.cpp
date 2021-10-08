// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketcommand.h"
#include <ostream>

using document::Bucket;
using document::BucketId;
using document::BucketSpace;

namespace storage {
namespace api {

BucketCommand::BucketCommand(const MessageType& type, const Bucket &bucket)
    : StorageCommand(type),
      _bucket(bucket),
      _originalBucket()
{
}

void
BucketCommand::remapBucketId(const BucketId& bucket)
{
    if (_originalBucket.getRawId() == 0) {
        _originalBucket = _bucket.getBucketId();
    }
    Bucket newBucket(_bucket.getBucketSpace(), bucket);
    _bucket = newBucket;
}

void
BucketCommand::print(std::ostream& out,
                     bool verbose, const std::string& indent) const
{
    out << "BucketCommand(" << _bucket.getBucketId();
    if (hasBeenRemapped()) {
        out << " <- " << _originalBucket;
    }
    out << ")";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

} // api
} // storage

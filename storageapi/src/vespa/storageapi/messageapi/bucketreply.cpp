// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketreply.h"
#include "bucketcommand.h"
#include <ostream>

namespace storage {
namespace api {

BucketReply::BucketReply(const BucketCommand& cmd,
                         const ReturnCode& code)
    : StorageReply(cmd, code),
      _bucket(cmd.getBucketId()),
      _originalBucket(cmd.getOriginalBucketId())
{
}

void
BucketReply::print(std::ostream& out, bool verbose,
                   const std::string& indent) const
{
    out << "BucketReply(" << _bucket;
    if (hasBeenRemapped()) {
        out << " <- " << _originalBucket;
    }
    out << ")";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

} // api
} // storage

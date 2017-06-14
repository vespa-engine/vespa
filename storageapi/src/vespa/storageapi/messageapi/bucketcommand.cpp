// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketcommand.h"
#include <ostream>

namespace storage {
namespace api {

void
BucketCommand::print(std::ostream& out,
                     bool verbose, const std::string& indent) const
{
    out << "BucketCommand(" << _bucket;
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

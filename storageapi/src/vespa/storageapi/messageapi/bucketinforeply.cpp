// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketinforeply.h"
#include <ostream>

namespace storage::api {

void
BucketInfoReply::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    out << "BucketInfoReply(" << _result << ")";
    if (verbose) {
        out << " : ";
        BucketReply::print(out, verbose, indent);
    }
}

}

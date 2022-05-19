// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketinfocommand.h"
#include <ostream>

namespace storage {
namespace api {

void
BucketInfoCommand::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    out << "BucketInfoCommand()";
    if (verbose) {
        out << " : ";
        BucketCommand::print(out, verbose, indent);
    }
}

} // api
} // storage

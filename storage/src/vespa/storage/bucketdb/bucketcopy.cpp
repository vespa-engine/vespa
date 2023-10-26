// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucketcopy.h"
#include <sstream>

namespace storage {

void
BucketCopy::print(std::ostream& out, bool /*verbose*/, const std::string&) const
{
    out << "node("
        << "idx=" << _node
        << ",crc=" << std::hex << "0x" << getChecksum() << std::dec
        << ",docs=" << getDocumentCount() << "/" << getMetaCount()
        << ",bytes=" << getTotalDocumentSize() << "/" << getUsedFileSize()
        << ",trusted=" << (trusted() ? "true" : "false")
        << ",active=" << (active() ? "true" : "false")
        << ",ready=" << (ready() ? "true" : "false")
        << ")";
}

std::string
BucketCopy::toString() const {
    std::ostringstream ost;
    print(ost, true, "");
    return ost.str();
}

}

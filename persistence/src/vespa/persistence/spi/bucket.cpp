// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/persistence/spi/bucket.h>
#include <sstream>
#include <iomanip>

namespace storage {
namespace spi {

std::string Bucket::toString() const {
    std::ostringstream ost;
    print(ost);
    return ost.str();
}

void
Bucket::print(std::ostream& out) const
{
    out << "Bucket(0x"
        << std::hex << std::setw(sizeof(document::BucketId::Type) * 2)
        << std::setfill('0') << _bucket.getId()
        << std::dec
        << ", partition " << _partition
        << ")";
}

} // spi
} // storage

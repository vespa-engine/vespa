// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketspace.h"
#include <iomanip>

namespace document {

vespalib::string BucketSpace::toString() const
{
    vespalib::asciistream os;
    os << *this;
    return os.str();
}

void BucketSpace::print(std::ostream& os) const
{
    os << toString();
}

vespalib::asciistream& operator<<(vespalib::asciistream& os, const BucketSpace& id)
{
    vespalib::asciistream::StateSaver stateSaver(os);
    return os << "BucketSpace(0x"
              << vespalib::hex << vespalib::setw(sizeof(BucketSpace::Type)*2) << vespalib::setfill('0')
              << id.getId()
              << ")";
}

std::ostream& operator<<(std::ostream& os, const BucketSpace& id)
{
    return os << id.toString();
}

}
// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket.h"
#include <iomanip>

namespace document {

vespalib::string Bucket::toString() const
{
    vespalib::asciistream os;
    os << *this;
    return os.str();
}

void Bucket::print(std::ostream& os) const
{
    os << toString();
}

vespalib::asciistream& operator<<(vespalib::asciistream& os, const Bucket& id)
{
    return os << "Bucket(" << id.getBucketSpace() << ", " << id.getBucketId() << ")";
}

std::ostream& operator<<(std::ostream& os, const Bucket& id)
{
    return os << id.toString();
}

}

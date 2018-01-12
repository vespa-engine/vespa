// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace document {

Bucket::Bucket() noexcept
    : _bucketSpace(BucketSpace::invalid()),
      _bucketId()
{
}

vespalib::string Bucket::toString() const
{
    vespalib::asciistream os;
    os << *this;
    return os.str();
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

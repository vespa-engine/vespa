// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket.h"
#include <ostream>
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage::spi {

vespalib::string
Bucket::toString() const {
    vespalib::asciistream ost;
    ost << *this;
    return ost.str();
}

vespalib::asciistream&
operator<<(vespalib::asciistream& os, const Bucket& bucket)
{
    return os << "Bucket(0x"
              << vespalib::hex << vespalib::setw(sizeof(document::BucketId::Type)*2) << vespalib::setfill('0')
              << bucket.getBucketId().getId()
              << vespalib::dec
              << ")";
}

std::ostream&
operator<<(std::ostream& os, const Bucket& bucket) {
    return os << bucket.toString();
}

}

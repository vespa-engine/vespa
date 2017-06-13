// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket.h"
#include <ostream>
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {
namespace spi {

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
              << ", partition " << bucket.getPartition()
              << ")";
}

std::ostream&
operator<<(std::ostream& os, const Bucket& bucket) {
    return os << bucket.toString();
}

} // spi
} // storage

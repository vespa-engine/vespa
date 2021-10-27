// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/persistence/spi/bucketinfo.h>

namespace proton::bucketdb {

/**
 * Interface for aggregating bucket checksums.
 **/
class ChecksumAggregator {
public:
    enum class ChecksumType {LEGACY, XXHASH64};
    using GlobalId = document::GlobalId;
    using Timestamp = storage::spi::Timestamp;
    using BucketChecksum = storage::spi::BucketChecksum;
};

}

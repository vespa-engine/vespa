// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "checksumaggregators.h"

namespace proton::bucketdb {

std::unique_ptr<ChecksumAggregator>
ChecksumAggregator::create(ChecksumType type, BucketChecksum seed) {
    switch (type) {
    case ChecksumType::LEGACY:
        return std::make_unique<LegacyChecksumAggregator>(seed);
    case ChecksumType::XXHASH64:
        return std::make_unique<XXH64ChecksumAggregator>(seed);
    }
    return std::unique_ptr<ChecksumAggregator>();
}

}

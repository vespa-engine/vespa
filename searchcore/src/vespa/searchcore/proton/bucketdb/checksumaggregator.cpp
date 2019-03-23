#include "checksumaggregators.h"

namespace proton::bucketdb {

std::unique_ptr<ChecksumAggregator>
ChecksumAggregator::create(ChecksumType type, BucketChecksum seed) {
    switch (type) {
    case ChecksumType::LEGACY:
        return std::make_unique<LegacyChecksumAggregator>(seed);
    case ChecksumType::XXHASH64:
        return std::make_unique<XXHChecksumAggregator>(seed);
    }
    return std::unique_ptr<ChecksumAggregator>();
}

}

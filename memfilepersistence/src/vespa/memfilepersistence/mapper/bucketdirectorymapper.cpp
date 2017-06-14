// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketdirectorymapper.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/random.h>

namespace storage {
namespace memfile {

BucketDirectoryMapper::BucketDirectoryMapper(uint32_t dirLevels,
                                             uint32_t dirSpread)
    : _dirLevels(dirLevels),
      _dirSpread(dirSpread)
{
}

std::vector<uint32_t>
BucketDirectoryMapper::getPath(const document::BucketId& bucket)
{
    document::BucketId::Type seed = bucket.getId();
    seed = seed ^ (seed >> 32);
    vespalib::RandomGen randomizer(static_cast<uint32_t>(seed) ^ 0xba5eba11);
    std::vector<uint32_t> position(_dirLevels);
    for (uint32_t i=0; i<_dirLevels; ++i) {
        position[i] = randomizer.nextUint32() % _dirSpread;
    }
    return position;
}

} // memfile
} // storage

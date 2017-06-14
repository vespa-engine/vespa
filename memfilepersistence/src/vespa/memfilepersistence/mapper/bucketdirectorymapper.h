// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::BucketDirectoryMapper
 * \ingroup memfile
 *
 * \brief Maps buckets to directories on disk.
 *
 * To avoid having too many files in one directory, we want to map buckets to
 * different directories. As these are all in the same partition anyways, we
 * don't really need the distribution to be different based on node indexes or
 * disk indexes.
 *
 * This class hides a simple function for distributing buckets between
 * directories.
 */

#pragma once

#include <vector>
#include <cstdint>

namespace document {
    class BucketId;
}

namespace storage {
namespace memfile {

class BucketDirectoryMapper {
    uint32_t _dirLevels;
    uint32_t _dirSpread;

public:
    BucketDirectoryMapper(uint32_t dirLevels, uint32_t dirSpread);

    std::vector<uint32_t> getPath(const document::BucketId&);
};

}
}

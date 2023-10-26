// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_info.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <stdexcept>

namespace search {

using vespalib::make_string;

LidInfo::LidInfo(uint32_t fileId, uint32_t chunkId, uint32_t sz)
{
    if (fileId >= getFileIdLimit()) {
        throw std::runtime_error(
                make_string("LidInfo(fileId=%u, chunkId=%u, size=%u) has invalid fileId larger than %d",
                            fileId, chunkId, sz, getFileIdLimit() - 1));
    }
    if (chunkId >= getChunkIdLimit()) {
        throw std::runtime_error(
                make_string("LidInfo(fileId=%u, chunkId=%u, size=%u) has invalid chunkId larger than %d",
                            fileId, chunkId, sz, getChunkIdLimit() - 1));
    }
    if (sz >= getSizeLimit()) {
        throw std::runtime_error(
                make_string("LidInfo(fileId=%u, chunkId=%u, size=%u) has too large size larger than %u",
                            fileId, chunkId, sz, getSizeLimit() - 1));
    }
    _value.v.fileId = fileId;
    _value.v.chunkId = chunkId;
    _value.v.size = computeAlignedSize(sz);
}

}

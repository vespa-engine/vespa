// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "chunkformats.h"

#include <vespa/vespalib/util/crc.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <xxhash.h>

using vespalib::MemoryDataStore;

namespace search {

using vespalib::make_string;

ChunkFormatV1::ChunkFormatV1(vespalib::nbostream& is, uint32_t expectedCrc,
                             vespalib::MemoryDataStore* memory_data_store)
    : ChunkFormat() {
    verifyCrc(is, expectedCrc);
    deserializeBody(is, memory_data_store);
}

ChunkFormatV1::ChunkFormatV1(size_t maxSize) : ChunkFormat(maxSize) {
}

uint32_t ChunkFormatV1::computeCrc(const void* buf, size_t sz) const {
    return vespalib::crc_32_type::crc(buf, sz);
}

ChunkFormatV2::ChunkFormatV2(vespalib::nbostream& is, uint32_t expectedCrc,
                             vespalib::MemoryDataStore* memory_data_store)
    : ChunkFormat() {
    verifyCrc(is, expectedCrc);
    verifyMagic(is);
    deserializeBody(is, memory_data_store);
}

ChunkFormatV2::ChunkFormatV2(size_t maxSize) : ChunkFormat(maxSize) {
}

uint32_t ChunkFormatV2::computeCrc(const void* buf, size_t sz) const {
    return XXH32(buf, sz, 0);
}

void ChunkFormatV2::verifyMagic(vespalib::nbostream& is) const {
    uint32_t magic;
    is >> magic;
    if (magic != MAGIC) {
        throw ChunkException(make_string("Unknown magic %0x, expected %0x", magic, MAGIC), VESPA_STRLOC);
    }
}

} // namespace search

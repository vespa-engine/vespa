// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "chunks.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/crc.h>
#include <vespa/vespalib/xxhash/xxhash.h>
#include <cassert>

using std::runtime_error;
using std::make_unique;
using vespalib::make_string;
using vespalib::nbostream_longlivedbuf;
using vespalib::compression::CompressionConfig;

namespace search::transactionlog {

Encoding::Encoding(Crc crc, Compression compression)
    : _raw(crc | (compression >> 2))
{
    assert(crc <= Crc::xxh64);
    assert(compression <= Compression::lz4);
}

IChunk::~IChunk() = default;

void
IChunk::add(const Packet::Entry & entry) {
    _entries.emplace_back(entry);
}

SerialNumRange
IChunk::range() const {
    return _entries.empty()
           ? SerialNumRange()
           : SerialNumRange(_entries.front().serial(), _entries.back().serial());
}

void
IChunk::deserializeEntries(nbostream & is) {
    while (is.good() && !is.empty()) {
        Packet::Entry e;
        e.deserialize(is);
        add(e);
    }
}

void
IChunk::serializeEntries(nbostream & os) const {
    for (const auto & e : _entries) {
        e.serialize(os);
    }
}

Encoding
IChunk::encode(nbostream & os) const {
    return onEncode(os);
}

void
IChunk::decode(nbostream & is) {
    onDecode(is);
}

IChunk::UP
IChunk::create(uint8_t chunkType) {
    return create(Encoding(chunkType));
}
IChunk::UP
IChunk::create(Encoding encoding) {
    switch (encoding.getCrc()) {
        case Encoding::Crc::xxh64:
            switch (encoding.getCompression()) {
                case Encoding::Compression::none:
                    return make_unique<XXH64None>();
                case Encoding::Compression::lz4:
                    return make_unique<XXH64Compressed>(CompressionConfig::LZ4);
                case Encoding::Compression::zstd:
                    return make_unique<XXH64Compressed>(CompressionConfig::ZSTD);
                default:
                    return make_unique<XXH64Compressed>(CompressionConfig::LZ4);
            }
        case Encoding::Crc::ccitt_crc32:
            switch (encoding.getCompression()) {
                case Encoding::Compression::none:
                    return make_unique<CCITTCRC32None>();
                default:
                    throw runtime_error(make_string("Unhandled compression type '%d' for ccitt_crc32 compression",
                                                    encoding.getCompression()));
            }
        default:
            throw runtime_error(make_string("Unhandled crc type '%d'", encoding.getCrc()));
    }
}

int32_t Encoding::calcCrc(Crc version, const void * buf, size_t sz)
{
    if (version == xxh64) {
        return static_cast<int32_t>(XXH64(buf, sz, 0ll));
    } else if (version == ccitt_crc32) {
        vespalib::crc_32_type calculator;
        calculator.process_bytes(buf, sz);
        return calculator.checksum();
    } else {
        abort();
    }
}

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "chunks.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/data/databuffer.h>

using std::runtime_error;
using std::make_unique;
using vespalib::make_string;
using vespalib::compression::compress;
using vespalib::compression::decompress;
using vespalib::compression::CompressionConfig;
using vespalib::DataBuffer;
using vespalib::ConstBufferRef;
using vespalib::nbostream;

namespace search::transactionlog {

namespace {
void verifyCrc(nbostream & is, Encoding::Crc crcType) {
    if (is.size() < sizeof(int32_t) * 2) {
        throw runtime_error(make_string("Not even room for the crc and length. Only %zu bytes left", is.size()));
    }
    size_t start = is.rp();
    is.adjustReadPos(is.size() - sizeof(int32_t));
    int32_t crc(0);
    is >> crc;
    is.rp(start);
    int32_t crcVerify = Encoding::calcCrc(crcType, is.c_str(), is.size() - sizeof(crc));
    if (crc != crcVerify) {
        throw runtime_error(make_string("Got bad crc : crcVerify = %d, expected %d",
                                        static_cast<int>(crcVerify), static_cast<int>(crc)));
    }
    is.rp(start);
}

Encoding::Compression
toCompression(CompressionConfig::Type type) {
    switch (type) {
        case CompressionConfig::ZSTD:
            return Encoding::Compression::zstd;
        case CompressionConfig::LZ4:
            return Encoding::Compression::lz4;
        case CompressionConfig::NONE:
            return Encoding::Compression::none;
        default:
            abort();
    }
}

}

Encoding
CCITTCRC32None::onEncode(nbostream &os) const {
    size_t start = os.wp();
    assert(getEntries().size() == 1);
    serializeEntries(os);
    os << int32_t(Encoding::calcCrc(Encoding::Crc::ccitt_crc32, os.c_str()+start, os.size() - start));
    return Encoding(Encoding::Crc::ccitt_crc32, Encoding::Compression::none);
}

void CCITTCRC32None::onDecode(nbostream &is) {
    verifyCrc(is, Encoding::Crc::ccitt_crc32);
    nbostream data(is.peek(), is.size() - sizeof(int32_t));
    deserializeEntries(data);
    is.adjustReadPos(is.size());
}

Encoding
XXH64None::onEncode(nbostream &os) const {
    size_t start = os.wp();
    assert(getEntries().size() == 1);
    serializeEntries(os);
    os << int32_t(Encoding::calcCrc(Encoding::Crc::xxh64, os.c_str()+start, os.size() - start));
    return Encoding(Encoding::Crc::xxh64, Encoding::Compression::none);
}

void XXH64None::onDecode(nbostream &is) {
    verifyCrc(is, Encoding::Crc::xxh64);
    nbostream data(is.peek(), is.size() - sizeof(int32_t));
    deserializeEntries(data);
    is.adjustReadPos(is.size());
}

void
XXH64Compressed::decompress(nbostream & is) {
    uint32_t uncompressedLen;
    is >> uncompressedLen;
    vespalib::DataBuffer uncompressed;
    ConstBufferRef compressed(is.peek(), is.size()-sizeof(uint32_t)*2);
    ::decompress(_type, uncompressedLen, compressed, uncompressed, false);
    nbostream data(uncompressed.getData(), uncompressed.getDataLen());
    deserializeEntries(data);
    is.adjustReadPos(is.size());
}

XXH64Compressed::XXH64Compressed(CompressionConfig::Type type, uint8_t level)
    : _type(type),
      _level(level)
{ }

Encoding
XXH64Compressed::compress(nbostream & os, Encoding::Crc crc) const {
    nbostream org;
    serializeEntries(org);
    DataBuffer compressed;
    CompressionConfig cfg(_type, _level, 80);
    ConstBufferRef uncompressed(org.c_str(), org.size());
    Encoding::Compression actual = toCompression(::compress(cfg, uncompressed, compressed, false));
    size_t start = os.wp();
    os.write(compressed.getData(), compressed.getDataLen());
    os << int32_t(Encoding::calcCrc(crc, os.c_str()+start, os.size() - start));
    return Encoding(Encoding::Crc::xxh64, actual);
}

Encoding
XXH64Compressed::onEncode(IChunk::nbostream &os) const {
    return compress(os, Encoding::Crc::xxh64);
}

void
XXH64Compressed::onDecode(IChunk::nbostream &is) {
    verifyCrc(is, Encoding::Crc::xxh64);
    decompress(is);
}

}

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

}

void CCITTCRC32None::onEncode(nbostream &os) {
    (void) os;
}

void CCITTCRC32None::onDecode(nbostream &is) {
    verifyCrc(is, Encoding::Crc::ccitt_crc32);
    nbostream data(is.peek(), is.size() - sizeof(int32_t));
    add(data);
    is.adjustReadPos(is.size());
}

void XXH64None::onEncode(nbostream &os) {
    (void) os;
}

void XXH64None::onDecode(nbostream &is) {
    verifyCrc(is, Encoding::Crc::xxh64);
    nbostream data(is.peek(), is.size() - sizeof(int32_t));
    add(data);
    is.adjustReadPos(is.size());
}

void
XXH64Compressed::decompress(nbostream & is, vespalib::compression::CompressionConfig::Type type) {
    uint32_t uncompressedLen;
    is >> uncompressedLen;
    vespalib::DataBuffer uncompressed;
    ConstBufferRef compressed(is.peek(), is.size()-sizeof(uint32_t)*2);
    ::decompress(type, uncompressedLen, compressed, uncompressed, false);
    nbostream data(uncompressed.getData(), uncompressed.getDataLen());
    add(data);
    is.adjustReadPos(is.size());
}

void XXH64LZ4::onEncode(IChunk::nbostream &os) {
    (void) os;

}

void XXH64LZ4::onDecode(IChunk::nbostream &is) {
    verifyCrc(is, Encoding::Crc::xxh64);
    decompress(is, CompressionConfig::LZ4);
}

void XXH64ZSTD::onEncode(IChunk::nbostream &os) {
    (void) os;

}

void XXH64ZSTD::onDecode(IChunk::nbostream &is) {
    verifyCrc(is, Encoding::Crc::xxh64);
    decompress(is, CompressionConfig::ZSTD);
}

}

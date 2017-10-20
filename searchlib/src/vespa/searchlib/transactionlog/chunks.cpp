// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "chunks.h"
#include <vespa/vespalib/util/stringfmt.h>

using std::runtime_error;
using std::make_unique;
using vespalib::make_string;

namespace search::transactionlog {

void CCITTCRC32None::onEncode(nbostream &os) {
    (void) os;
}

void CCITTCRC32None::onDecode(nbostream &is) {
    if (is.size() < sizeof(int32_t)) {
        throw runtime_error(make_string("Not even room for the crc. Only %zu bytes left", is.size()));
    }
    size_t start = is.rp();
    is.adjustReadPos(is.size() - sizeof(int32_t));
    int32_t crc(0);
    is >> crc;
    is.rp(start);
    int32_t crcVerify = Encoding::calcCrc(Encoding::Crc::ccitt_crc32, is.c_str(), is.size() - sizeof(crc));
    if (crc != crcVerify) {
        throw runtime_error(make_string("Got bad crc : crcVerify = %d, expected %d",
                                        static_cast<int>(crcVerify), static_cast<int>(crc)));
    }
    is.rp(start);
    while (is.good() && (is.size() > sizeof(int32_t))) {
        Packet::Entry e;
        e.deserialize(is);
        add(e);
    }
}

void XXH64None::onEncode(nbostream &os) {
    (void) os;
}

void XXH64None::onDecode(nbostream &is) {
    if (is.size() < sizeof(int32_t)) {
        throw runtime_error(make_string("Not even room for the crc. Only %zu bytes left", is.size()));
    }
    size_t start = is.rp();
    is.adjustReadPos(is.size() - sizeof(int32_t));
    int32_t crc(0);
    is >> crc;
    is.rp(start);
    int32_t crcVerify = Encoding::calcCrc(Encoding::Crc::xxh64, is.c_str(), is.size() - sizeof(crc));
    if (crc != crcVerify) {
        throw runtime_error(make_string("Got bad crc : crcVerify = %d, expected %d",
                                        static_cast<int>(crcVerify), static_cast<int>(crc)));
    }
    is.rp(start);
    while (is.good() && (is.size() > sizeof(int32_t))) {
        Packet::Entry e;
        e.deserialize(is);
        add(e);
    }
}

void XXH64LZ4::onEncode(IChunk::nbostream &os) {
    (void) os;

}

void XXH64LZ4::onDecode(IChunk::nbostream &is) {
    (void) is;
}

}

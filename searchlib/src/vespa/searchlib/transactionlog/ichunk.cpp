// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "chunks.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/crc.h>
#include <vespa/vespalib/util/exceptions.h>
#include <xxhash.h>
#include <cassert>
#include <ostream>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.transactionlog.ichunk");

using std::make_unique;
using vespalib::make_string_short::fmt;
using vespalib::nbostream_longlivedbuf;
using vespalib::compression::CompressionConfig;
using vespalib::IllegalArgumentException;

namespace search::transactionlog {

Encoding::Encoding(Crc crc, Compression compression)
    : _raw(crc | (compression << 4u))
{
    assert(crc <= Crc::xxh64);
    assert(compression <= Compression::zstd);
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
    return create(Encoding(chunkType), 9);
}
IChunk::UP
IChunk::create(Encoding encoding, uint8_t compressionLevel) {
    switch (encoding.getCrc()) {
        case Encoding::Crc::xxh64:
            switch (encoding.getCompression()) {
                case Encoding::Compression::none:
                    return make_unique<XXH64NoneChunk>();
                case Encoding::Compression::none_multi:
                    return make_unique<XXH64CompressedChunk>(CompressionConfig::NONE_MULTI, compressionLevel);
                case Encoding::Compression::lz4:
                    return make_unique<XXH64CompressedChunk>(CompressionConfig::LZ4, compressionLevel);
                case Encoding::Compression::zstd:
                    return make_unique<XXH64CompressedChunk>(CompressionConfig::ZSTD, compressionLevel);
                default:
                    throw IllegalArgumentException(fmt("Unhandled compression type '%d' for xxh64, compression=",
                                                       encoding.getCompression()));
            }
        case Encoding::Crc::ccitt_crc32:
            switch (encoding.getCompression()) {
                case Encoding::Compression::none:
                    return make_unique<CCITTCRC32NoneChunk>();
                default:
                    throw IllegalArgumentException(fmt("Unhandled compression type '%d' for ccitt_crc32, compression=",
                                                       encoding.getCompression()));
            }
        default:
            throw IllegalArgumentException(fmt("Unhandled crc type '%d'", encoding.getCrc()));
    }
}

int32_t
Encoding::calcCrc(Crc version, const void * buf, size_t sz)
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

std::ostream &
operator << (std::ostream & os, Encoding e) {
    return os << "crc=" << e.getCrc() << " compression=" << e.getCompression();
}

void
encode(vespalib::nbostream & os, const IChunk & chunk, Encoding encoding) {
    size_t begin = os.wp();
    os << encoding.getRaw();  // Placeholder for encoding
    os << uint32_t(0);         // Placeholder for size
    Encoding realEncoding = chunk.encode(os);
    size_t end = os.wp();
    os.wp(0);
    os << realEncoding.getRaw();  //Patching real encoding
    os << uint32_t(end - (begin + sizeof(uint32_t) + sizeof(uint8_t))); // Patching actual size.
    os.wp(end);
    SerialNumRange range = chunk.range();
    LOG(spam, "Encoded chunk with %zu entries and %zu bytes, range[%" PRIu64 ", %" PRIu64 "] encoding(wanted=%x, real=%x)",
        chunk.getEntries().size(), os.size(), range.from(), range.to(), encoding.getRaw(), realEncoding.getRaw());
}

SerializedChunk::SerializedChunk(std::unique_ptr<CommitChunk> commitChunk, Encoding encoding, uint8_t compressionLevel)
   : _commitChunk(std::move(commitChunk)),
     _os(),
     _range(_commitChunk->getPacket().range()),
     _numEntries(_commitChunk->getPacket().size())
{
    Packet packet = _commitChunk->stealPacket();
    nbostream_longlivedbuf h(packet.getHandle().data(), packet.getHandle().size());

    IChunk::UP chunk = IChunk::create(encoding, compressionLevel);
    SerialNum prev = 0;
    while (h.size() > 0) {
        //LOG(spam,
        //"Pos(%d) Len(%d), Lim(%d), Remaining(%d)",
        //h.getPos(), h.getLength(), h.getLimit(), h.getRemaining());
        Packet::Entry entry;
        entry.deserialize(h);
        assert (prev < entry.serial());
        chunk->add(entry);
        prev = entry.serial();
    }
    assert(! chunk->getEntries().empty());
    encode(_os, *chunk, encoding);
}

SerializedChunk::~SerializedChunk() = default;

vespalib::ConstBufferRef SerializedChunk::getData() const {
    return vespalib::ConstBufferRef(_os.data(), _os.size());
}
}

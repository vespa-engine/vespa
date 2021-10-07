// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "common.h"

namespace search::transactionlog {

class Encoding {
public:
    enum Crc {
        nocrc = 0,
        ccitt_crc32 = 1,
        xxh64 = 2
    };
    enum Compression {
        none = 0,
        none_multi = 1,
        lz4 = 2,
        zstd = 3
    };
    explicit Encoding(uint8_t raw) : _raw(raw) { }
    Encoding(Crc crc, Compression compression);
    Crc getCrc() const { return Crc(_raw & 0xf); }
    Compression getCompression() const { return Compression((_raw >> 4) & 0xf); }
    static int32_t calcCrc(Crc version, const void * buf, size_t sz);
    uint8_t getRaw() const { return _raw; }
    bool operator == (Encoding rhs) const { return _raw == rhs._raw; }
private:
    uint8_t _raw;
};

std::ostream & operator << (std::ostream & os, Encoding e);

/**
 * Interface for different chunk formats.
 * Format specifies both crc type, and compression type.
 */
class IChunk {
public:
    using UP = std::unique_ptr<IChunk>;
    using Entries = std::vector<Packet::Entry>;
    using nbostream = vespalib::nbostream;
    using ConstBufferRef = vespalib::ConstBufferRef;
    virtual ~IChunk();
    const Entries & getEntries() const { return _entries; }
    void add(const Packet::Entry & entry);
    Encoding encode(nbostream & os) const;
    void decode(nbostream & buf);
    static UP create(uint8_t chunkType);
    static UP create(Encoding chunkType, uint8_t compressionLevel);
    SerialNumRange range() const;
protected:
    virtual Encoding onEncode(nbostream & os) const = 0;
    virtual void onDecode(nbostream & is) = 0;
    void deserializeEntries(nbostream & is);
    void serializeEntries(nbostream & os) const;
private:
    Entries _entries;
};

}

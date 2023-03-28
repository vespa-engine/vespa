// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "chunkformat.h"

namespace search {

class ChunkFormatV1 : public ChunkFormat
{
public:
    enum {VERSION=0};
    ChunkFormatV1(vespalib::nbostream & is, uint32_t expectedCrc);
    ChunkFormatV1(size_t maxSize);
private:
    bool includeSerializedSize() const override { return false; }
    uint8_t getVersion() const override { return VERSION; }
    size_t getHeaderSize() const override { return 0; }
    uint32_t computeCrc(const void * buf, size_t sz) const override;
    void writeHeader(vespalib::DataBuffer & buf) const override {
        (void) buf;
    }
};

class ChunkFormatV2 : public ChunkFormat
{
public:
    enum {VERSION=1, MAGIC=0x5ba32de7};
    ChunkFormatV2(vespalib::nbostream & is, uint32_t expectedCrc);
    ChunkFormatV2(size_t maxSize);
private:
    bool includeSerializedSize() const override { return true; }
    size_t getHeaderSize() const override {
        // MAGIC
        return 4;
    }
    uint8_t getVersion() const override { return VERSION; }
    uint32_t computeCrc(const void * buf, size_t sz) const override;
    void writeHeader(vespalib::DataBuffer & buf) const override {
        buf.writeInt32(MAGIC);
    }
    void verifyMagic(vespalib::nbostream & is) const;
};

} // namespace search


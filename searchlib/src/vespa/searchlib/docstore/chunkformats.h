// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/docstore/chunkformat.h>

namespace search {

class ChunkFormatV1 : public ChunkFormat
{
public:
    enum {VERSION=0};
    ChunkFormatV1(vespalib::nbostream & is);
    ChunkFormatV1(vespalib::nbostream & is, uint32_t expectedCrc);
    ChunkFormatV1(size_t maxSize);
private:
    virtual bool includeSerializedSize() const { return false; }
    virtual uint8_t getVersion() const { return VERSION; }
    virtual size_t getHeaderSize() const { return 0; }
    virtual uint32_t computeCrc(const void * buf, size_t sz) const;
    virtual void writeHeader(vespalib::DataBuffer & buf) const {
        (void) buf;
    }
};

class ChunkFormatV2 : public ChunkFormat
{
public:
    enum {VERSION=1, MAGIC=0x5ba32de7};
    ChunkFormatV2(vespalib::nbostream & is);
    ChunkFormatV2(vespalib::nbostream & is, uint32_t expectedCrc);
    ChunkFormatV2(size_t maxSize);
private:
    virtual bool includeSerializedSize() const { return true; }
    virtual size_t getHeaderSize() const {
        // MAGIC
        return 4;
    }
    virtual uint8_t getVersion() const { return VERSION; }
    virtual uint32_t computeCrc(const void * buf, size_t sz) const;
    virtual void writeHeader(vespalib::DataBuffer & buf) const {
        buf.writeInt32(MAGIC);
    }
    void verifyMagic(vespalib::nbostream & is) const;
};

} // namespace search


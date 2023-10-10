// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ichunk.h"
#include <vespa/vespalib/util/compressionconfig.h>

namespace search::transactionlog {

/// Current default chunk serialisation format
class XXH64NoneChunk : public IChunk {
protected:
    Encoding onEncode(nbostream &os) const override;
    void onDecode(nbostream &is) override;
public:
};

/// TODO Legacy chunk serialisation format to be removed soon.
class CCITTCRC32NoneChunk : public IChunk {
protected:
    Encoding onEncode(nbostream &os) const override;
    void onDecode(nbostream &is) override;
public:
};

/// Future default chunk serialisation format
class XXH64CompressedChunk : public IChunk {
public:
    using CompressionConfig = vespalib::compression::CompressionConfig;
    XXH64CompressedChunk(CompressionConfig::Type, uint8_t level);
    ~XXH64CompressedChunk() override;
protected:
    void decompress(nbostream & os, uint32_t uncompressedLen);
    Encoding compress(nbostream & os, Encoding::Crc crc) const;
    Encoding onEncode(nbostream &os) const override;
    void onDecode(nbostream &is) override;
private:
    CompressionConfig::Type _type;
    uint8_t                 _level;
    vespalib::alloc::Alloc  _backing;
};

}

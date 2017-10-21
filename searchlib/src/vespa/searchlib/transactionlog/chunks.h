// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ichunk.h"
#include <vespa/vespalib/util/compressionconfig.h>

namespace search::transactionlog {

class XXH64None : public IChunk {
protected:
    void onEncode(nbostream &os) const override;
    void onDecode(nbostream &is) override;
public:
};

class CCITTCRC32None : public IChunk {
protected:
    void onEncode(nbostream &os) const override;
    void onDecode(nbostream &is) override;
public:
};

class XXH64Compressed : public IChunk {
public:
    using CompressionConfig = vespalib::compression::CompressionConfig;
    XXH64Compressed(CompressionConfig::Type);
    void setLevel(uint8_t level) { _level = level; }
protected:
    void decompress(nbostream & os);
    void compress(nbostream & os, Encoding::Crc crc) const;
    void onEncode(nbostream &os) const override;
    void onDecode(nbostream &is) override;
private:
    CompressionConfig::Type _type;
    uint8_t                 _level;
};

}

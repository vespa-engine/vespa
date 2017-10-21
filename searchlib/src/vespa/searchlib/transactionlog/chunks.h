// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/compressionconfig.h>
#include "ichunk.h"

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
    void setLevel(uint8_t level) { _compressionLevel = level; }
protected:
    void decompress(nbostream & os, vespalib::compression::CompressionConfig::Type type);
private:
    uint8_t _compressionLevel;
};
class XXH64LZ4 : public XXH64Compressed {
protected:
    void onEncode(nbostream &os) const override;
    void onDecode(nbostream &is) override;
};

class XXH64ZSTD : public XXH64Compressed {
protected:
    void onEncode(nbostream &os) const override;
    void onDecode(nbostream &is) override;
};

}

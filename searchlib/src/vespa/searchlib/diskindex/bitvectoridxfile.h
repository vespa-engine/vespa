// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/vespalib/stllike/string.h>
#include "bitvectorkeyscope.h"

class Fast_BufferedFile;

namespace search::common { class FileHeaderContext; }

namespace search::diskindex {

class BitVectorIdxFileWrite
{
private:
    std::unique_ptr<Fast_BufferedFile> _idxFile;

public:

protected:
    uint32_t _numKeys;      // Number of bitvectors and keys
    uint32_t _docIdLimit;   // Limit for document ids (docId < docIdLimit)
    uint32_t _idxHeaderLen;
    BitVectorKeyScope _scope;

    uint64_t idxSize() const;
    void syncCommon();

public:
    BitVectorIdxFileWrite(const BitVectorIdxFileWrite &) = delete;
    BitVectorIdxFileWrite(const BitVectorIdxFileWrite &&) = delete;
    BitVectorIdxFileWrite& operator=(const BitVectorIdxFileWrite &) = delete;
    BitVectorIdxFileWrite& operator=(const BitVectorIdxFileWrite &&) = delete;
    BitVectorIdxFileWrite(BitVectorKeyScope scope);

    virtual ~BitVectorIdxFileWrite();

    virtual void open(const vespalib::string &name, uint32_t docIdLimit,
                      const TuneFileSeqWrite &tuneFileWrite,
                      const common::FileHeaderContext &fileHeaderContext);



    void addWordSingle(uint64_t wordNum, uint32_t numDocs);
    virtual void flush();
    virtual void sync();
    virtual void close();

    static uint32_t getBitVectorLimit(uint32_t docIdLimit) {
        // Must match FastS_BinSizeParams::CalcMaxBinSize()
        uint32_t ret = (docIdLimit + 63) / 64;
        if (ret < 16) {
            ret = 16;
        }
        if (ret > docIdLimit) {
            ret = docIdLimit;
        }
        return ret;
    }

    void makeIdxHeader(const common::FileHeaderContext &fileHeaderContext);
    void updateIdxHeader(uint64_t fileBitSize);
};

}

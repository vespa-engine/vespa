// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS
#pragma once

#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/vespalib/stllike/string.h>
#include "bitvectorkeyscope.h"

namespace vespalib
{

class nbostream;

}

namespace search
{


namespace common
{

class FileHeaderContext;

}


namespace diskindex
{

class BitVectorIdxFileWrite
{
private:
    BitVectorIdxFileWrite(const BitVectorIdxFileWrite &) = delete;
    BitVectorIdxFileWrite(const BitVectorIdxFileWrite &&) = delete;
    BitVectorIdxFileWrite& operator=(const BitVectorIdxFileWrite &) = delete;
    BitVectorIdxFileWrite& operator=(const BitVectorIdxFileWrite &&) = delete;

    Fast_BufferedFile *_idxFile;

public:

protected:
    uint32_t _numKeys;		// Number of bitvectors and keys
    uint32_t _docIdLimit;	// Limit for document ids (docId < docIdLimit)
    uint32_t _idxHeaderLen;
    BitVectorKeyScope _scope;

    uint64_t idxSize() const;
    void checkPointWriteCommon(vespalib::nbostream &out);
    void syncCommon();

public:
    BitVectorIdxFileWrite(BitVectorKeyScope scope);

    ~BitVectorIdxFileWrite();

    /**
     * Checkpoint write.  Used at semi-regular intervals during indexing
     * to allow for continued indexing after an interrupt.  Implies
     * flush from memory to disk, and possibly also sync to permanent
     * storage media.
     */
    void
    checkPointWrite(vespalib::nbostream &out);

    /**
     * Checkpoint read.  Used when resuming indexing after an interrupt.
     */
    void
    checkPointRead(vespalib::nbostream &in);

    void
    open(const vespalib::string &name, uint32_t docIdLimit,
         const TuneFileSeqWrite &tuneFileWrite,
         const search::common::FileHeaderContext &fileHeaderContext);



    void
    addWordSingle(uint64_t wordNum, uint32_t numDocs);

    void
    flush();

    void
    sync();

    void
    close();

    static uint32_t
    getBitVectorLimit(uint32_t docIdLimit)
    {
        // Must match FastS_BinSizeParams::CalcMaxBinSize()
        uint32_t ret = (docIdLimit + 63) / 64;
        if (ret < 16)
            ret = 16;
        if (ret > docIdLimit)
            ret = docIdLimit;
        return ret;
    }

    void
    makeIdxHeader(const search::common::FileHeaderContext &fileHeaderContext);

    void
    updateIdxHeader(uint64_t fileBitSize);
};


} // namespace diskindex

} // namespace search


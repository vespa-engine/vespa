// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS
#pragma once

#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/vespalib/stllike/string.h>
#include "bitvectoridxfile.h"

namespace search
{


namespace diskindex
{


class BitVectorFileWrite : public BitVectorIdxFileWrite
{
private:
    BitVectorFileWrite(const BitVectorFileWrite &) = delete;
    BitVectorFileWrite(const BitVectorFileWrite &&) = delete;
    BitVectorFileWrite& operator=(const BitVectorFileWrite &) = delete;
    BitVectorFileWrite& operator=(const BitVectorFileWrite &&) = delete;

    using Parent = BitVectorIdxFileWrite;

    Fast_BufferedFile *_datFile;
public:

private:
    uint32_t _datHeaderLen;

public:
    BitVectorFileWrite(BitVectorKeyScope scope);

    ~BitVectorFileWrite();

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
         const common::FileHeaderContext &fileHeaderContext);


    void
    addWordSingle(uint64_t wordNum, const BitVector &bitVector);

    void
    flush();

    void
    sync();

    void
    close();

    void
    makeDatHeader(const common::FileHeaderContext &fileHeaderContext);

    void
    updateDatHeader(uint64_t fileBitSize);
};


/*
 * Buffer document ids for a candidate bitvector.
 */
class BitVectorCandidate
{
private:
    std::vector<uint32_t> _array;
    uint64_t _numDocs;
    uint32_t _bitVectorLimit;
    BitVector::UP _bv;

public:
    BitVectorCandidate(uint32_t docIdLimit, uint32_t bitVectorLimit)
        : _array(),
          _numDocs(0u),
          _bitVectorLimit(bitVectorLimit),
          _bv(BitVector::create(docIdLimit))
    {
        _array.reserve(_bitVectorLimit);
    }


    BitVectorCandidate(uint32_t docIdLimit)
        : _array(),
          _numDocs(0u),
          _bitVectorLimit(BitVectorFileWrite::getBitVectorLimit(docIdLimit)),
          _bv(BitVector::create(docIdLimit))
    {
        _array.reserve(_bitVectorLimit);
    }

    ~BitVectorCandidate();

    void
    clear()
    {
        if (__builtin_expect(_numDocs > _bitVectorLimit, false)) {
            _bv->clear();
        }
        _numDocs = 0;
        _array.clear();
    }

    void
    flush(BitVector &obv)
    {
        if (__builtin_expect(_numDocs > _bitVectorLimit, false)) {
            obv.orWith(*_bv);
        } else {
            for (uint32_t i : _array) {
                obv.setBit(i);
            }
        }
        clear();
    }

    void
    add(uint32_t docId)
    {
        if (_numDocs < _bitVectorLimit) {
            _array.push_back(docId);
        } else {
            if (__builtin_expect(_numDocs == _bitVectorLimit, false)) {
                for (uint32_t i : _array) {
                    _bv->setBit(i);
                }
                _array.clear();
            }
            _bv->setBit(docId);
        }
        ++_numDocs;
    }

    /*
     * Get number of documents buffered.  This might include duplicates.
     */
    uint64_t
    getNumDocs() const
    {
        return _numDocs;
    }

    bool
    empty() const
    {
        return _numDocs == 0;
    }

    /*
     * Return true if array limit has been exceeded and bitvector has been
     * populated.
     */
    bool
    getCrossedBitVectorLimit() const
    {
        return _numDocs > _bitVectorLimit;
    }

    BitVector &
    getBitVector()
    {
        return *_bv;
    }

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
};


} // namespace diskindex

} // namespace search


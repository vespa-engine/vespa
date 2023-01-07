// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvectoridxfile.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <vector>

class Fast_BufferedFile;

namespace search::diskindex {

class BitVectorFileWrite : public BitVectorIdxFileWrite
{
private:
    using Parent = BitVectorIdxFileWrite;
    std::unique_ptr<Fast_BufferedFile> _datFile;
    uint32_t                           _datHeaderLen;

public:
    BitVectorFileWrite(const BitVectorFileWrite &) = delete;
    BitVectorFileWrite(const BitVectorFileWrite &&) = delete;
    BitVectorFileWrite& operator=(const BitVectorFileWrite &) = delete;
    BitVectorFileWrite& operator=(const BitVectorFileWrite &&) = delete;
    BitVectorFileWrite(BitVectorKeyScope scope);
    ~BitVectorFileWrite() override;

    void open(const vespalib::string &name, uint32_t docIdLimit,
            const TuneFileSeqWrite &tuneFileWrite,
            const common::FileHeaderContext &fileHeaderContext) override;


    void addWordSingle(uint64_t wordNum, const BitVector &bitVector);
    void flush() override;
    void sync() override;
    void close() override;
    void makeDatHeader(const common::FileHeaderContext &fileHeaderContext);
    void updateDatHeader(uint64_t fileBitSize);
};


/*
 * Buffer document ids for a candidate bitvector.
 */
class BitVectorCandidate
{
private:
    std::vector<uint32_t, vespalib::allocator_large<uint32_t>> _array;
    BitVector::UP  _bv;
    uint64_t       _numDocs;
    const uint32_t _bitVectorLimit;


public:
    BitVectorCandidate(uint32_t docIdLimit, uint32_t bitVectorLimit)
        : _array(),
          _bv(BitVector::create(docIdLimit)),
          _numDocs(0u),
          _bitVectorLimit(bitVectorLimit)
    {
        _array.reserve(_bitVectorLimit);
    }


    BitVectorCandidate(uint32_t docIdLimit)
        : BitVectorCandidate(docIdLimit, BitVectorFileWrite::getBitVectorLimit(docIdLimit))
    { }

    ~BitVectorCandidate();

    void clear() {
        if (__builtin_expect(_numDocs > _bitVectorLimit, false)) {
            _bv->clear();
        }
        _numDocs = 0;
        _array.clear();
    }

    void flush(BitVector &obv) {
        if (__builtin_expect(_numDocs > _bitVectorLimit, false)) {
            obv.orWith(*_bv);
        } else {
            for (uint32_t i : _array) {
                obv.setBit(i);
            }
        }
        clear();
    }

    void add(uint32_t docId) {
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
    uint64_t getNumDocs() const { return _numDocs; }

    bool empty() const { return _numDocs == 0; }

    /*
     * Return true if array limit has been exceeded and bitvector has been
     * populated.
     */
    bool getCrossedBitVectorLimit() const {
        return _numDocs > _bitVectorLimit;
    }

    BitVector &getBitVector() { return *_bv; }
};

}

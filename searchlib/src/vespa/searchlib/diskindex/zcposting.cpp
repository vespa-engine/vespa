// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zcposting.h"
#include <vespa/searchlib/index/postinglistcounts.h>
#include <vespa/searchlib/index/postinglistcountfile.h>
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/vespalib/data/fileheader.h>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.zcposting");

namespace {

vespalib::string myId5("Zc.5");
vespalib::string myId4("Zc.4");
vespalib::string emptyId;

}

namespace search::diskindex {

using index::PostingListCountFileSeqRead;
using index::PostingListCountFileSeqWrite;
using common::FileHeaderContext;
using bitcompression::FeatureDecodeContextBE;
using bitcompression::FeatureEncodeContextBE;
using vespalib::getLastErrorString;


Zc4PostingSeqRead::
Zc4PostingSeqRead(PostingListCountFileSeqRead *countFile)
    : PostingListFileSeqRead(),
      _decodeContext(),
      _docIdK(0),
      _prevDocId(0),
      _numDocs(0),
      _readContext(sizeof(uint64_t)),
      _file(),
      _hasMore(false),
      _dynamicK(false),
      _lastDocId(0),
      _minChunkDocs(1 << 30),
      _minSkipDocs(64),
      _docIdLimit(10000000),
      _zcDocIds(),
      _l1Skip(),
      _l2Skip(),
      _l3Skip(),
      _l4Skip(),
      _numWords(0),
      _fileBitSize(0),
      _chunkNo(0),
      _l1SkipDocId(0),
      _l1SkipDocIdPos(0),
      _l1SkipFeaturesPos(0),
      _l2SkipDocId(0),
      _l2SkipDocIdPos(0),
      _l2SkipL1SkipPos(0),
      _l2SkipFeaturesPos(0),
      _l3SkipDocId(0),
      _l3SkipDocIdPos(0),
      _l3SkipL1SkipPos(0),
      _l3SkipL2SkipPos(0),
      _l3SkipFeaturesPos(0),
      _l4SkipDocId(0),
      _l4SkipDocIdPos(0),
      _l4SkipL1SkipPos(0),
      _l4SkipL2SkipPos(0),
      _l4SkipL3SkipPos(0),
      _l4SkipFeaturesPos(0),
      _featuresSize(0),
      _countFile(countFile),
      _headerBitLen(0),
      _rangeEndOffset(0),
      _readAheadEndOffset(0),
      _wordStart(0),
      _residue(0)
{
    if (_countFile != NULL) {
        PostingListParams params;
        _countFile->getParams(params);
        params.get("docIdLimit", _docIdLimit);
        params.get("minChunkDocs", _minChunkDocs);
    }
}


Zc4PostingSeqRead::~Zc4PostingSeqRead()
{
}


void
Zc4PostingSeqRead::
readCommonWordDocIdAndFeatures(DocIdAndFeatures &features)
{
    if (_zcDocIds._valI >= _zcDocIds._valE && _hasMore)
        readWordStart();    // Read start of next chunk
    // Split docid & features.
    assert(_zcDocIds._valI < _zcDocIds._valE);
    uint32_t docIdPos = _zcDocIds.pos();
    uint32_t docId = _prevDocId + 1 + _zcDocIds.decode();
    features._docId = docId;
    _prevDocId = docId;
    assert(docId <= _lastDocId);
    if (docId > _l1SkipDocId) {
        _l1SkipDocIdPos += _l1Skip.decode() + 1;
        assert(docIdPos == _l1SkipDocIdPos);
        _l1SkipFeaturesPos += _l1Skip.decode() + 1;
        uint64_t featuresPos = _decodeContext->getReadOffset();
        assert(featuresPos == _l1SkipFeaturesPos);
        (void) featuresPos;
        if (docId > _l2SkipDocId) {
            _l2SkipDocIdPos += _l2Skip.decode() + 1;
            assert(docIdPos == _l2SkipDocIdPos);
            _l2SkipFeaturesPos += _l2Skip.decode() + 1;
            assert(featuresPos == _l2SkipFeaturesPos);
            _l2SkipL1SkipPos += _l2Skip.decode() + 1;
            assert(_l1Skip.pos() == _l2SkipL1SkipPos);
            if (docId > _l3SkipDocId) {
                _l3SkipDocIdPos += _l3Skip.decode() + 1;
                assert(docIdPos == _l3SkipDocIdPos);
                _l3SkipFeaturesPos += _l3Skip.decode() + 1;
                assert(featuresPos == _l3SkipFeaturesPos);
                _l3SkipL1SkipPos += _l3Skip.decode() + 1;
                assert(_l1Skip.pos() == _l3SkipL1SkipPos);
                _l3SkipL2SkipPos += _l3Skip.decode() + 1;
                assert(_l2Skip.pos() == _l3SkipL2SkipPos);
                if (docId > _l4SkipDocId) {
                    _l4SkipDocIdPos += _l4Skip.decode() + 1;
                    assert(docIdPos == _l4SkipDocIdPos);
                    (void) docIdPos;
                    _l4SkipFeaturesPos += _l4Skip.decode() + 1;
                    assert(featuresPos == _l4SkipFeaturesPos);
                    _l4SkipL1SkipPos += _l4Skip.decode() + 1;
                    assert(_l1Skip.pos() == _l4SkipL1SkipPos);
                    _l4SkipL2SkipPos += _l4Skip.decode() + 1;
                    assert(_l2Skip.pos() == _l4SkipL2SkipPos);
                    _l4SkipL3SkipPos += _l4Skip.decode() + 1;
                    assert(_l3Skip.pos() == _l4SkipL3SkipPos);
                    _l4SkipDocId += _l4Skip.decode() + 1;
                    assert(_l4SkipDocId <= _lastDocId);
                    assert(_l4SkipDocId >= docId);
                }
                _l3SkipDocId += _l3Skip.decode() + 1;
                assert(_l3SkipDocId <= _lastDocId);
                assert(_l3SkipDocId <= _l4SkipDocId);
                assert(_l3SkipDocId >= docId);
            }
            _l2SkipDocId += _l2Skip.decode() + 1;
            assert(_l2SkipDocId <= _lastDocId);
            assert(_l2SkipDocId <= _l4SkipDocId);
            assert(_l2SkipDocId <= _l3SkipDocId);
            assert(_l2SkipDocId >= docId);
        }
        _l1SkipDocId += _l1Skip.decode() + 1;
        assert(_l1SkipDocId <= _lastDocId);
        assert(_l1SkipDocId <= _l4SkipDocId);
        assert(_l1SkipDocId <= _l3SkipDocId);
        assert(_l1SkipDocId <= _l2SkipDocId);
        assert(_l1SkipDocId >= docId);
    }
    if (docId < _lastDocId) {
        // Assert more space available when not yet at last docid
        assert(_zcDocIds._valI < _zcDocIds._valE);
    } else {
        // Assert that space has been used when at last docid
        assert(_zcDocIds._valI == _zcDocIds._valE);
        // Assert that we've read to end of skip info
        assert(_l1SkipDocId == _lastDocId);
        assert(_l2SkipDocId == _lastDocId);
        assert(_l3SkipDocId == _lastDocId);
        assert(_l4SkipDocId == _lastDocId);
        if (!_hasMore) {
            _chunkNo = 0;
        }
    }
    _decodeContext->readFeatures(features);
    --_residue;
}


void
Zc4PostingSeqRead::
readDocIdAndFeatures(DocIdAndFeatures &features)
{
    if (_residue == 0 && !_hasMore) {
        if (_rangeEndOffset != 0) {
            DecodeContext &d = *_decodeContext;
            uint64_t curOffset = d.getReadOffset();
            assert(curOffset <= _rangeEndOffset);
            if (curOffset < _rangeEndOffset)
                readWordStart();
        }
        if (_residue == 0) {
            // Don't read past end of posting list.
            features.clear(static_cast<uint32_t>(-1));
            return;
        }
    }
    if (_lastDocId > 0)
        return readCommonWordDocIdAndFeatures(features);
    // Interleaves docid & features
    typedef FeatureEncodeContextBE EC;
    DecodeContext &d = *_decodeContext;
    uint32_t length;
    uint64_t val64;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);

    UC64BE_DECODEEXPGOLOMB_SMALL_NS(o,
                                    K_VALUE_ZCPOSTING_DELTA_DOCID,
                                    EC);
    uint32_t docId = _prevDocId + 1 + val64;
    features._docId = docId;
    _prevDocId = docId;
    UC64_DECODECONTEXT_STORE(o, d._);
    if (__builtin_expect(oCompr >= d._valE, false)) {
        _readContext.readComprBuffer();
    }
    _decodeContext->readFeatures(features);
    --_residue;
}


void
Zc4PostingSeqRead::readWordStartWithSkip()
{
    typedef FeatureEncodeContextBE EC;
    DecodeContext &d = *_decodeContext;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);
    uint32_t length;
    uint64_t val64;
    const uint64_t *valE = d._valE;

    if (_hasMore)
        ++_chunkNo;
    else
        _chunkNo = 0;
    assert(_numDocs >= _minSkipDocs || _hasMore);
    bool hasMore = false;
    if (__builtin_expect(_numDocs >= _minChunkDocs, false)) {
        hasMore = static_cast<int64_t>(oVal) < 0;
        oVal <<= 1;
        length = 1;
        UC64BE_READBITS_NS(o, EC);
    }
    if (_dynamicK)
        _docIdK = EC::calcDocIdK((_hasMore || hasMore) ? 1 : _numDocs,
                                 _docIdLimit);
    if (_hasMore || hasMore) {
        if (_rangeEndOffset == 0) {
            assert(hasMore == (_chunkNo + 1 < _counts._segments.size()));
            assert(_numDocs == _counts._segments[_chunkNo]._numDocs);
        }
        if (hasMore) {
            assert(_numDocs >= _minSkipDocs);
            assert(_numDocs >= _minChunkDocs);
        }
    } else {
        assert(_numDocs >= _minSkipDocs);
        if (_rangeEndOffset == 0) {
            assert(_numDocs == _counts._numDocs);
        }
    }
    if (__builtin_expect(oCompr >= valE, false)) {
        UC64_DECODECONTEXT_STORE(o, d._);
        _readContext.readComprBuffer();
        valE = d._valE;
        UC64_DECODECONTEXT_LOAD(o, d._);
    }
    UC64BE_DECODEEXPGOLOMB_NS(o,
                              K_VALUE_ZCPOSTING_DOCIDSSIZE,
                              EC);
    uint32_t docIdsSize = val64 + 1;
    UC64BE_DECODEEXPGOLOMB_NS(o,
                              K_VALUE_ZCPOSTING_L1SKIPSIZE,
                              EC);
    uint32_t l1SkipSize = val64;
    if (__builtin_expect(oCompr >= valE, false)) {
        UC64_DECODECONTEXT_STORE(o, d._);
        _readContext.readComprBuffer();
        valE = d._valE;
        UC64_DECODECONTEXT_LOAD(o, d._);
    }
    uint32_t l2SkipSize = 0;
    if (l1SkipSize != 0) {
        UC64BE_DECODEEXPGOLOMB_NS(o,
                                  K_VALUE_ZCPOSTING_L2SKIPSIZE,
                                  EC);
        l2SkipSize = val64;
    }
    uint32_t l3SkipSize = 0;
    if (l2SkipSize != 0) {
        UC64BE_DECODEEXPGOLOMB_NS(o,
                                  K_VALUE_ZCPOSTING_L3SKIPSIZE,
                                  EC);
        l3SkipSize = val64;
    }
    if (__builtin_expect(oCompr >= valE, false)) {
        UC64_DECODECONTEXT_STORE(o, d._);
        _readContext.readComprBuffer();
        valE = d._valE;
        UC64_DECODECONTEXT_LOAD(o, d._);
    }
    uint32_t l4SkipSize = 0;
    if (l3SkipSize != 0) {
        UC64BE_DECODEEXPGOLOMB_NS(o,
                                  K_VALUE_ZCPOSTING_L4SKIPSIZE,
                                  EC);
        l4SkipSize = val64;
    }
    UC64BE_DECODEEXPGOLOMB_NS(o,
                              K_VALUE_ZCPOSTING_FEATURESSIZE,
                              EC);
    _featuresSize = val64;
    if (__builtin_expect(oCompr >= valE, false)) {
        UC64_DECODECONTEXT_STORE(o, d._);
        _readContext.readComprBuffer();
        valE = d._valE;
        UC64_DECODECONTEXT_LOAD(o, d._);
    }
    if (_dynamicK) {
        UC64BE_DECODEEXPGOLOMB_NS(o,
                                  _docIdK,
                                  EC);
    } else {
        UC64BE_DECODEEXPGOLOMB_NS(o,
                                  K_VALUE_ZCPOSTING_LASTDOCID,
                                  EC);
    }
    _lastDocId = _docIdLimit - 1 - val64;
    if (_hasMore || hasMore) {
        if (_rangeEndOffset == 0) {
            assert(_lastDocId == _counts._segments[_chunkNo]._lastDoc);
        }
    }

    if (__builtin_expect(oCompr >= valE, false)) {
        UC64_DECODECONTEXT_STORE(o, d._);
        _readContext.readComprBuffer();
        valE = d._valE;
        UC64_DECODECONTEXT_LOAD(o, d._);
    }
    uint64_t bytePad = oPreRead & 7;
    if (bytePad > 0) {
        length = bytePad;
        oVal <<= length;
        UC64BE_READBITS_NS(o, EC);
    }
    UC64_DECODECONTEXT_STORE(o, d._);
    if (__builtin_expect(oCompr >= valE, false)) {
        _readContext.readComprBuffer();
    }
    _zcDocIds.clearReserve(docIdsSize);
    _l1Skip.clearReserve(l1SkipSize);
    _l2Skip.clearReserve(l2SkipSize);
    _l3Skip.clearReserve(l3SkipSize);
    _l4Skip.clearReserve(l4SkipSize);
    _decodeContext->readBytes(_zcDocIds._valI, docIdsSize);
    _zcDocIds._valE = _zcDocIds._valI + docIdsSize;
    if (l1SkipSize > 0)
        _decodeContext->readBytes(_l1Skip._valI, l1SkipSize);
    _l1Skip._valE = _l1Skip._valI + l1SkipSize;
    if (l2SkipSize > 0)
        _decodeContext->readBytes(_l2Skip._valI, l2SkipSize);
    _l2Skip._valE = _l2Skip._valI + l2SkipSize;
    if (l3SkipSize > 0)
        _decodeContext->readBytes(_l3Skip._valI, l3SkipSize);
    _l3Skip._valE = _l3Skip._valI + l3SkipSize;
    if (l4SkipSize > 0)
        _decodeContext->readBytes(_l4Skip._valI, l4SkipSize);
    _l4Skip._valE = _l4Skip._valI + l4SkipSize;

    if (l1SkipSize > 0)
        _l1SkipDocId = _l1Skip.decode() + 1 + _prevDocId;
    else
        _l1SkipDocId = _lastDocId;
    if (l2SkipSize > 0)
        _l2SkipDocId = _l2Skip.decode() + 1 + _prevDocId;
    else
        _l2SkipDocId = _lastDocId;
    if (l3SkipSize > 0)
        _l3SkipDocId = _l3Skip.decode() + 1 + _prevDocId;
    else
        _l3SkipDocId = _lastDocId;
    if (l4SkipSize > 0)
        _l4SkipDocId = _l4Skip.decode() + 1 + _prevDocId;
    else
        _l4SkipDocId = _lastDocId;
    _l1SkipDocIdPos = 0;
    _l1SkipFeaturesPos = _decodeContext->getReadOffset();
    _l2SkipDocIdPos = 0;
    _l2SkipL1SkipPos = 0;
    _l2SkipFeaturesPos = _decodeContext->getReadOffset();
    _l3SkipDocIdPos = 0;
    _l3SkipL1SkipPos = 0;
    _l3SkipL2SkipPos = 0;
    _l3SkipFeaturesPos = _decodeContext->getReadOffset();
    _l4SkipDocIdPos = 0;
    _l4SkipL1SkipPos = 0;
    _l4SkipL2SkipPos = 0;
    _l4SkipL3SkipPos = 0;
    _l4SkipFeaturesPos = _decodeContext->getReadOffset();
    _hasMore = hasMore;
    // Decode context is now positioned at start of features
}


void
Zc4PostingSeqRead::readWordStart()
{
    typedef FeatureEncodeContextBE EC;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _decodeContext->_);
    uint32_t length;
    uint64_t val64;
    const uint64_t *valE = _decodeContext->_valE;

    UC64BE_DECODEEXPGOLOMB_NS(o,
                              K_VALUE_ZCPOSTING_NUMDOCS,
                              EC);
    UC64_DECODECONTEXT_STORE(o, _decodeContext->_);
    if (oCompr >= valE)
        _readContext.readComprBuffer();
    _numDocs = static_cast<uint32_t>(val64) + 1;
    _residue = _numDocs;
    _prevDocId = _hasMore ? _lastDocId : 0u;
    if (_rangeEndOffset == 0) {
        assert(_numDocs <= _counts._numDocs);
        assert(_numDocs == _counts._numDocs ||
               _numDocs >= _minChunkDocs ||
               _hasMore);
    }

    if (_numDocs >= _minSkipDocs || _hasMore) {
        readWordStartWithSkip();
        // Decode context is not positioned at start of features
    } else {
        if (_dynamicK)
            _docIdK = EC::calcDocIdK(_numDocs, _docIdLimit);
        _lastDocId = 0u;
        // Decode context is not positioned at start of docids & features
    }
}


void
Zc4PostingSeqRead::readCounts(const PostingListCounts &counts)
{
    assert(!_hasMore);  // Previous words must have been read.

    _counts = counts;

    assert((_counts._numDocs == 0) == (_counts._bitLength == 0));
    if (_counts._numDocs > 0) {
        _wordStart = _decodeContext->getReadOffset();
        readWordStart();
    }
}


bool
Zc4PostingSeqRead::open(const vespalib::string &name,
                        const TuneFileSeqRead &tuneFileRead)
{
    if (tuneFileRead.getWantDirectIO())
        _file.EnableDirectIO();
    bool res = _file.OpenReadOnly(name.c_str());
    if (res) {
        _readContext.setFile(&_file);
        _readContext.setFileSize(_file.GetSize());
        DecodeContext &d = *_decodeContext;
        _readContext.allocComprBuf(65536u, 32768u);
        d.emptyBuffer(0);
        _readContext.readComprBuffer();

        readHeader();
        if (d._valI >= d._valE) {
            _readContext.readComprBuffer();
        }
    } else {
        LOG(error, "could not open %s: %s",
            _file.GetFileName(), getLastErrorString().c_str());
    }
    return res;
}


bool
Zc4PostingSeqRead::close()
{
    _readContext.dropComprBuf();
    _file.Close();
    _readContext.setFile(NULL);
    return true;
}


void
Zc4PostingSeqRead::getParams(PostingListParams &params)
{
    if (_countFile != NULL) {
        PostingListParams countParams;
        _countFile->getParams(countParams);
        params = countParams;
        uint32_t countDocIdLimit = 0;
        uint32_t countMinChunkDocs = 0;
        countParams.get("docIdLimit", countDocIdLimit);
        countParams.get("minChunkDocs", countMinChunkDocs);
        assert(_docIdLimit == countDocIdLimit);
        assert(_minChunkDocs == countMinChunkDocs);
    } else {
        params.clear();
        params.set("docIdLimit", _docIdLimit);
        params.set("minChunkDocs", _minChunkDocs);
    }
    params.set("minSkipDocs", _minSkipDocs);
}


void
Zc4PostingSeqRead::getFeatureParams(PostingListParams &params)
{
    _decodeContext->getParams(params);
}


void
Zc4PostingSeqRead::readHeader()
{
    FeatureDecodeContextBE &d = *_decodeContext;
    const vespalib::string &myId = _dynamicK ? myId5 : myId4;

    vespalib::FileHeader header;
    d.readHeader(header, _file.getSize());
    uint32_t headerLen = header.getSize();
    assert(header.hasTag("frozen"));
    assert(header.hasTag("fileBitSize"));
    assert(header.hasTag("format.0"));
    assert(header.hasTag("format.1"));
    assert(!header.hasTag("format.2"));
    assert(header.hasTag("numWords"));
    assert(header.hasTag("minChunkDocs"));
    assert(header.hasTag("docIdLimit"));
    assert(header.hasTag("minSkipDocs"));
    assert(header.hasTag("endian"));
    bool completed = header.getTag("frozen").asInteger() != 0;
    _fileBitSize = header.getTag("fileBitSize").asInteger();
    headerLen += (-headerLen & 7);
    assert(completed);
    (void) completed;
    assert(_fileBitSize >= 8 * headerLen);
    assert(header.getTag("format.0").asString() == myId);
    (void) myId;
    assert(header.getTag("format.1").asString() == d.getIdentifier());
    _numWords = header.getTag("numWords").asInteger();
    _minChunkDocs = header.getTag("minChunkDocs").asInteger();
    _docIdLimit = header.getTag("docIdLimit").asInteger();
    _minSkipDocs = header.getTag("minSkipDocs").asInteger();
    assert(header.getTag("endian").asString() == "big");
    // Read feature decoding specific subheader
    d.readHeader(header, "features.");
    // Align on 64-bit unit
    d.smallAlign(64);
    assert(d.getReadOffset() == headerLen * 8);
    _headerBitLen = d.getReadOffset();
}


const vespalib::string &
Zc4PostingSeqRead::getIdentifier()
{
    return myId4;
}


uint64_t
Zc4PostingSeqRead::getCurrentPostingOffset() const
{
    FeatureDecodeContextBE &d = *_decodeContext;
    return d.getReadOffset() - _headerBitLen;
}


void
Zc4PostingSeqRead::setPostingOffset(uint64_t offset,
                                    uint64_t endOffset,
                                    uint64_t readAheadOffset)
{
    assert(_residue == 0);  // Only to be called between posting lists

    FeatureDecodeContextBE &d = *_decodeContext;

    _rangeEndOffset = endOffset + _headerBitLen;
    _readAheadEndOffset = readAheadOffset +  _headerBitLen;
    _readContext.setStopOffset(_readAheadEndOffset, false);
    uint64_t newOffset = offset + _headerBitLen;
    if (newOffset != d.getReadOffset()) {
        _readContext.setPosition(newOffset);
        assert(newOffset == d.getReadOffset());
        _readContext.readComprBuffer();
    }
}


Zc4PostingSeqWrite::
Zc4PostingSeqWrite(PostingListCountFileSeqWrite *countFile)
    : PostingListFileSeqWrite(),
      _encodeContext(),
      _writeContext(_encodeContext),
      _file(),
      _minChunkDocs(1 << 30),
      _minSkipDocs(64),
      _docIdLimit(10000000),
      _docIds(),
      _encodeFeatures(NULL),
      _featureOffset(0),
      _featureWriteContext(sizeof(uint64_t)),
      _writePos(0),
      _dynamicK(false),
      _zcDocIds(),
      _l1Skip(),
      _l2Skip(),
      _l3Skip(),
      _l4Skip(),
      _numWords(0),
      _fileBitSize(0),
      _countFile(countFile)
{
    _encodeContext.setWriteContext(&_writeContext);

    if (_countFile != NULL) {
        PostingListParams params;
        _countFile->getParams(params);
        params.get("docIdLimit", _docIdLimit);
        params.get("minChunkDocs", _minChunkDocs);
    }
    _featureWriteContext.allocComprBuf(64, 1);
}


Zc4PostingSeqWrite::~Zc4PostingSeqWrite()
{
}


void
Zc4PostingSeqWrite::
writeDocIdAndFeatures(const DocIdAndFeatures &features)
{
    if (__builtin_expect(_docIds.size() >= _minChunkDocs, false))
        flushChunk();
    _encodeFeatures->writeFeatures(features);
    uint64_t writeOffset = _encodeFeatures->getWriteOffset();
    uint64_t featureSize = writeOffset - _featureOffset;
    assert(static_cast<uint32_t>(featureSize) == featureSize);
    _docIds.push_back(std::make_pair(features._docId,
                                     static_cast<uint32_t>(featureSize)));
    _featureOffset = writeOffset;
}


void
Zc4PostingSeqWrite::flushWord()
{
    if (__builtin_expect(_docIds.size() >= _minSkipDocs ||
                         !_counts._segments.empty(), false)) {
        // Use skip information if enough documents of chunking has happened
        flushWordWithSkip(false);
        _numWords++;
    } else if (_docIds.size() > 0) {
        flushWordNoSkip();
        _numWords++;
    }

    EncodeContext &e = _encodeContext;
    uint64_t writePos = e.getWriteOffset();

    _counts._bitLength = writePos - _writePos;
    _writePos = writePos;
}


uint32_t
Zc4PostingSeqWrite::readHeader(const vespalib::string &name)
{
    EncodeContext &f = *_encodeFeatures;

    FeatureDecodeContextBE d;
    ComprFileReadContext drc(d);
    FastOS_File file;
    const vespalib::string &myId = _dynamicK ? myId5 : myId4;

    d.setReadContext(&drc);
    bool res = file.OpenReadOnly(name.c_str());
    if (!res) {
        LOG(error, "Could not open %s for reading file header: %s",
            name.c_str(), getLastErrorString().c_str());
        LOG_ABORT("should not be reached");
    }

    drc.setFile(&file);
    drc.setFileSize(file.GetSize());
    drc.allocComprBuf(512, 32768u);
    d.emptyBuffer(0);
    drc.readComprBuffer();

    vespalib::FileHeader header;
    d.readHeader(header, file.getSize());
    uint32_t headerLen = header.getSize();
    assert(header.hasTag("frozen"));
    assert(header.hasTag("fileBitSize"));
    assert(header.hasTag("format.0"));
    assert(header.hasTag("format.1"));
    assert(!header.hasTag("format.2"));
    assert(header.hasTag("numWords"));
    assert(header.hasTag("minChunkDocs"));
    assert(header.hasTag("docIdLimit"));
    assert(header.hasTag("minSkipDocs"));
    assert(header.hasTag("endian"));
    bool headerCompleted = header.getTag("frozen").asInteger() != 0;
    uint64_t headerFileBitSize = header.getTag("fileBitSize").asInteger();
    headerLen += (-headerLen & 7);
    assert(!headerCompleted || headerFileBitSize >= headerLen * 8);
    (void) headerCompleted;
    (void) headerFileBitSize;
    assert(header.getTag("format.0").asString() == myId);
    (void) myId;
    assert(header.getTag("format.1").asString() == f.getIdentifier());
    _minChunkDocs = header.getTag("minChunkDocs").asInteger();
    _docIdLimit = header.getTag("docIdLimit").asInteger();
    _minSkipDocs = header.getTag("minSkipDocs").asInteger();
    assert(header.getTag("endian").asString() == "big");
    // Read feature decoding specific subheader using helper decode context
    f.readHeader(header, "features.");
    // Align on 64-bit unit
    d.smallAlign(64);
    assert(d.getReadOffset() == headerLen * 8);
    file.Close();
    return headerLen;
}


void
Zc4PostingSeqWrite::makeHeader(const FileHeaderContext &fileHeaderContext)
{
    EncodeContext &f = *_encodeFeatures;
    EncodeContext &e = _encodeContext;
    ComprFileWriteContext &wce = _writeContext;

    const vespalib::string &myId = _dynamicK ? myId5 : myId4;
    vespalib::FileHeader header;

    typedef vespalib::GenericHeader::Tag Tag;
    fileHeaderContext.addTags(header, _file.GetFileName());
    header.putTag(Tag("frozen", 0));
    header.putTag(Tag("fileBitSize", 0));
    header.putTag(Tag("format.0", myId));
    header.putTag(Tag("format.1", f.getIdentifier()));
    header.putTag(Tag("numWords", 0));
    header.putTag(Tag("minChunkDocs", _minChunkDocs));
    header.putTag(Tag("docIdLimit", _docIdLimit));
    header.putTag(Tag("minSkipDocs", _minSkipDocs));
    header.putTag(Tag("endian", "big"));
    header.putTag(Tag("desc", "Posting list file"));

    f.writeHeader(header, "features.");
    e.setupWrite(wce);
    e.writeHeader(header);
    e.smallAlign(64);
    e.flush();
    uint32_t headerLen = header.getSize();
    headerLen += (-headerLen & 7);      // Then to uint64_t
    assert(e.getWriteOffset() == headerLen * 8);
    assert((e.getWriteOffset() & 63) == 0); // Header must be word aligned
}


void
Zc4PostingSeqWrite::updateHeader()
{
    vespalib::FileHeader h;
    FastOS_File f;
    f.OpenReadWrite(_file.GetFileName());
    h.readFile(f);
    FileHeaderContext::setFreezeTime(h);
    typedef vespalib::GenericHeader::Tag Tag;
    h.putTag(Tag("frozen", 1));
    h.putTag(Tag("fileBitSize", _fileBitSize));
    h.putTag(Tag("numWords", _numWords));
    h.rewriteFile(f);
    f.Sync();
    f.Close();
}


bool
Zc4PostingSeqWrite::open(const vespalib::string &name,
                         const TuneFileSeqWrite &tuneFileWrite,
                         const FileHeaderContext &fileHeaderContext)
{
    if (tuneFileWrite.getWantSyncWrites()) {
        _file.EnableSyncWrites();
    }
    if (tuneFileWrite.getWantDirectIO()) {
        _file.EnableDirectIO();
    }
    bool ok = _file.OpenWriteOnly(name.c_str());
    if (!ok) {
        LOG(error, "could not open '%s' for writing: %s",
            _file.GetFileName(), getLastErrorString().c_str());
        // XXX may need to do something more here, I don't know what...
        return false;
    }
    uint64_t fileSize = _file.GetSize();
    uint64_t bufferStartFilePos = _writeContext.getBufferStartFilePos();
    assert(fileSize >= bufferStartFilePos);
    (void) fileSize;
    _file.SetSize(bufferStartFilePos);
    assert(bufferStartFilePos == static_cast<uint64_t>(_file.GetPosition()));
    _writeContext.setFile(&_file);
    search::ComprBuffer &cb = _writeContext;
    EncodeContext &e = _encodeContext;
    _writeContext.allocComprBuf(65536u, 32768u);
    if (bufferStartFilePos == 0) {
        e.setupWrite(cb);
        // Reset accumulated stats
        _fileBitSize = 0;
        _numWords = 0;
        // Start write initial header
        makeHeader(fileHeaderContext);
        _encodeFeatures->setupWrite(_featureWriteContext);
        // end write initial header
        _writePos = e.getWriteOffset();
    } else {
        assert(bufferStartFilePos >= 8u);
        uint32_t headerSize = readHeader(name); // Read existing header
        assert(bufferStartFilePos >= headerSize);
        (void) headerSize;
        e.afterWrite(_writeContext, 0, bufferStartFilePos);
    }

    // Ensure that some space is initially available in encoding buffers
    _zcDocIds.maybeExpand();
    _l1Skip.maybeExpand();
    _l2Skip.maybeExpand();
    _l3Skip.maybeExpand();
    _l4Skip.maybeExpand();
    return true;    // Assume success
}


bool
Zc4PostingSeqWrite::close()
{
    EncodeContext &e = _encodeContext;

    _fileBitSize = e.getWriteOffset();
    // Write some pad bits to avoid decompression readahead going past
    // memory mapped file during search and into SIGSEGV territory.

    // First pad to 64 bits alignment.
    e.smallAlign(64);
    e.writeComprBufferIfNeeded();

    // Then write 128 more bits.  This allows for 64-bit decoding
    // with a readbits that always leaves a nonzero preRead
    e.padBits(128);
    e.alignDirectIO();
    e.flush();
    e.writeComprBuffer();   // Also flushes slack

    _writeContext.dropComprBuf();
    _file.Sync();
    _file.Close();
    _writeContext.setFile(NULL);
    updateHeader();
    return true;
}



void
Zc4PostingSeqWrite::
setParams(const PostingListParams &params)
{
    if (_countFile != NULL)
        _countFile->setParams(params);
    params.get("docIdLimit", _docIdLimit);
    params.get("minChunkDocs", _minChunkDocs);
    params.get("minSkipDocs", _minSkipDocs);
}


void
Zc4PostingSeqWrite::
getParams(PostingListParams &params)
{
    if (_countFile != NULL) {
        PostingListParams countParams;
        _countFile->getParams(countParams);
        params = countParams;
        uint32_t countDocIdLimit = 0;
        uint32_t countMinChunkDocs = 0;
        countParams.get("docIdLimit", countDocIdLimit);
        countParams.get("minChunkDocs", countMinChunkDocs);
        assert(_docIdLimit == countDocIdLimit);
        assert(_minChunkDocs == countMinChunkDocs);
    } else {
        params.clear();
        params.set("docIdLimit", _docIdLimit);
        params.set("minChunkDocs", _minChunkDocs);
    }
    params.set("minSkipDocs", _minSkipDocs);
}


void
Zc4PostingSeqWrite::
setFeatureParams(const PostingListParams &params)
{
    _encodeFeatures->setParams(params);
}


void
Zc4PostingSeqWrite::
getFeatureParams(PostingListParams &params)
{
    _encodeFeatures->getParams(params);
}


void
Zc4PostingSeqWrite::flushChunk()
{
    /* TODO: Flush chunk and prepare for new (possible short) chunk  */
    flushWordWithSkip(true);
}

#define L1SKIPSTRIDE 16
#define L2SKIPSTRIDE 8
#define L3SKIPSTRIDE 8
#define L4SKIPSTRIDE 8


void
Zc4PostingSeqWrite::calcSkipInfo()
{
    uint32_t lastDocId = 0u;
    uint32_t lastL1SkipDocId = 0u;
    uint32_t lastL1SkipDocIdPos = 0;
    uint32_t lastL1SkipFeaturePos = 0;
    uint32_t lastL2SkipDocId = 0u;
    uint32_t lastL2SkipDocIdPos = 0;
    uint32_t lastL2SkipFeaturePos = 0;
    uint32_t lastL2SkipL1SkipPos = 0;
    uint32_t lastL3SkipDocId = 0u;
    uint32_t lastL3SkipDocIdPos = 0;
    uint32_t lastL3SkipFeaturePos = 0;
    uint32_t lastL3SkipL1SkipPos = 0;
    uint32_t lastL3SkipL2SkipPos = 0;
    uint32_t lastL4SkipDocId = 0u;
    uint32_t lastL4SkipDocIdPos = 0;
    uint32_t lastL4SkipFeaturePos = 0;
    uint32_t lastL4SkipL1SkipPos = 0;
    uint32_t lastL4SkipL2SkipPos = 0;
    uint32_t lastL4SkipL3SkipPos = 0;
    unsigned int l1SkipCnt = 0;
    unsigned int l2SkipCnt = 0;
    unsigned int l3SkipCnt = 0;
    unsigned int l4SkipCnt = 0;
    uint64_t featurePos = 0;

    std::vector<DocIdAndFeatureSize>::const_iterator dit = _docIds.begin();
    std::vector<DocIdAndFeatureSize>::const_iterator dite = _docIds.end();

    if (!_counts._segments.empty()) {
        lastDocId = _counts._segments.back()._lastDoc;
        lastL1SkipDocId = lastDocId;
        lastL2SkipDocId = lastDocId;
        lastL3SkipDocId = lastDocId;
        lastL4SkipDocId = lastDocId;
    }

    for (; dit != dite; ++dit) {
        if (l1SkipCnt >= L1SKIPSTRIDE) {
            // L1 docid delta
            uint32_t docIdDelta = lastDocId - lastL1SkipDocId;
            assert(static_cast<int32_t>(docIdDelta) > 0);
            _l1Skip.encode(docIdDelta - 1);
            lastL1SkipDocId = lastDocId;
            // L1 docid pos
            uint64_t docIdPos = _zcDocIds.size();
            _l1Skip.encode(docIdPos - lastL1SkipDocIdPos - 1);
            lastL1SkipDocIdPos = docIdPos;
            // L1 features pos
            _l1Skip.encode(featurePos - lastL1SkipFeaturePos - 1);
            lastL1SkipFeaturePos = featurePos;
            l1SkipCnt = 0;
            ++l2SkipCnt;
            if (l2SkipCnt >= L2SKIPSTRIDE) {
                // L2 docid delta
                docIdDelta = lastDocId - lastL2SkipDocId;
                assert(static_cast<int32_t>(docIdDelta) > 0);
                _l2Skip.encode(docIdDelta - 1);
                lastL2SkipDocId = lastDocId;
                // L2 docid pos
                docIdPos = _zcDocIds.size();
                _l2Skip.encode(docIdPos - lastL2SkipDocIdPos - 1);
                lastL2SkipDocIdPos = docIdPos;
                // L2 features pos
                _l2Skip.encode(featurePos - lastL2SkipFeaturePos - 1);
                lastL2SkipFeaturePos = featurePos;
                // L2 L1Skip pos
                uint64_t l1SkipPos = _l1Skip.size();
                _l2Skip.encode(l1SkipPos - lastL2SkipL1SkipPos - 1);
                lastL2SkipL1SkipPos = l1SkipPos;
                l2SkipCnt = 0;
                ++l3SkipCnt;
                if (l3SkipCnt >= L3SKIPSTRIDE) {
                    // L3 docid delta
                    docIdDelta = lastDocId - lastL3SkipDocId;
                    assert(static_cast<int32_t>(docIdDelta) > 0);
                    _l3Skip.encode(docIdDelta - 1);
                    lastL3SkipDocId = lastDocId;
                    // L3 docid pos
                    docIdPos = _zcDocIds.size();
                    _l3Skip.encode(docIdPos - lastL3SkipDocIdPos - 1);
                    lastL3SkipDocIdPos = docIdPos;
                    // L3 features pos
                    _l3Skip.encode(featurePos - lastL3SkipFeaturePos - 1);
                    lastL3SkipFeaturePos = featurePos;
                    // L3 L1Skip pos
                    l1SkipPos = _l1Skip.size();
                    _l3Skip.encode(l1SkipPos - lastL3SkipL1SkipPos - 1);
                    lastL3SkipL1SkipPos = l1SkipPos;
                    // L3 L2Skip pos
                    uint64_t l2SkipPos = _l2Skip.size();
                    _l3Skip.encode(l2SkipPos - lastL3SkipL2SkipPos - 1);
                    lastL3SkipL2SkipPos = l2SkipPos;
                    l3SkipCnt = 0;
                    ++l4SkipCnt;
                    if (l4SkipCnt >= L4SKIPSTRIDE) {
                        // L4 docid delta
                        docIdDelta = lastDocId - lastL4SkipDocId;
                        assert(static_cast<int32_t>(docIdDelta) > 0);
                        _l4Skip.encode(docIdDelta - 1);
                        lastL4SkipDocId = lastDocId;
                        // L4 docid pos
                        docIdPos = _zcDocIds.size();
                        _l4Skip.encode(docIdPos - lastL4SkipDocIdPos - 1);
                        lastL4SkipDocIdPos = docIdPos;
                        // L4 features pos
                        _l4Skip.encode(featurePos - lastL4SkipFeaturePos - 1);
                        lastL4SkipFeaturePos = featurePos;
                        // L4 L1Skip pos
                        l1SkipPos = _l1Skip.size();
                        _l4Skip.encode(l1SkipPos - lastL4SkipL1SkipPos - 1);
                        lastL4SkipL1SkipPos = l1SkipPos;
                        // L4 L2Skip pos
                        l2SkipPos = _l2Skip.size();
                        _l4Skip.encode(l2SkipPos - lastL4SkipL2SkipPos - 1);
                        lastL4SkipL2SkipPos = l2SkipPos;
                        // L4 L3Skip pos
                        uint64_t l3SkipPos = _l3Skip.size();
                        _l4Skip.encode(l3SkipPos - lastL4SkipL3SkipPos - 1);
                        lastL4SkipL3SkipPos = l3SkipPos;
                        l4SkipCnt = 0;
                    }
                }
            }
        }
        uint32_t docId = dit->first;
        featurePos += dit->second;
        _zcDocIds.encode(docId - lastDocId - 1);
        lastDocId = docId;
        ++l1SkipCnt;
    }
    // Extra partial entries for skip tables to simplify iterator during search
    if (_l1Skip.size() > 0)
        _l1Skip.encode(lastDocId - lastL1SkipDocId - 1);
    if (_l2Skip.size() > 0)
        _l2Skip.encode(lastDocId - lastL2SkipDocId - 1);
    if (_l3Skip.size() > 0)
        _l3Skip.encode(lastDocId - lastL3SkipDocId - 1);
    if (_l4Skip.size() > 0)
        _l4Skip.encode(lastDocId - lastL4SkipDocId - 1);
}


void
Zc4PostingSeqWrite::flushWordWithSkip(bool hasMore)
{
    assert(_docIds.size() >= _minSkipDocs || !_counts._segments.empty());

    _encodeFeatures->flush();
    EncodeContext &e = _encodeContext;

    uint32_t numDocs = _docIds.size();

    e.encodeExpGolomb(numDocs - 1, K_VALUE_ZCPOSTING_NUMDOCS);
    if (numDocs >= _minChunkDocs)
        e.writeBits((hasMore ? 1 : 0), 1);

    // TODO: Calculate docids size, possible also k parameter  */
    calcSkipInfo();

    uint32_t docIdsSize = _zcDocIds.size();
    uint32_t l1SkipSize = _l1Skip.size();
    uint32_t l2SkipSize = _l2Skip.size();
    uint32_t l3SkipSize = _l3Skip.size();
    uint32_t l4SkipSize = _l4Skip.size();

    e.encodeExpGolomb(docIdsSize - 1, K_VALUE_ZCPOSTING_DOCIDSSIZE);
    e.encodeExpGolomb(l1SkipSize, K_VALUE_ZCPOSTING_L1SKIPSIZE);
    if (l1SkipSize != 0) {
        e.encodeExpGolomb(l2SkipSize, K_VALUE_ZCPOSTING_L2SKIPSIZE);
        if (l2SkipSize != 0) {
            e.encodeExpGolomb(l3SkipSize, K_VALUE_ZCPOSTING_L3SKIPSIZE);
            if (l3SkipSize != 0) {
                e.encodeExpGolomb(l4SkipSize, K_VALUE_ZCPOSTING_L4SKIPSIZE);
            }
        }
    }
    e.encodeExpGolomb(_featureOffset, K_VALUE_ZCPOSTING_FEATURESSIZE);

    // Encode last document id in chunk or word.
    if (_dynamicK) {
        uint32_t docIdK = e.calcDocIdK((_counts._segments.empty() &&
                                        !hasMore) ?
                                       numDocs : 1,
                                       _docIdLimit);
        e.encodeExpGolomb(_docIdLimit - 1 - _docIds.back().first,
                          docIdK);
    } else {
        e.encodeExpGolomb(_docIdLimit - 1 - _docIds.back().first,
                          K_VALUE_ZCPOSTING_LASTDOCID);
    }

    e.smallAlign(8);    // Byte align

    uint8_t *docIds = _zcDocIds._mallocStart;
    e.writeBits(reinterpret_cast<const uint64_t *>(docIds),
                0,
                docIdsSize * 8);
    if (l1SkipSize > 0) {
        uint8_t *l1Skip = _l1Skip._mallocStart;
        e.writeBits(reinterpret_cast<const uint64_t *>(l1Skip),
                    0,
                    l1SkipSize * 8);
    }
    if (l2SkipSize > 0) {
        uint8_t *l2Skip = _l2Skip._mallocStart;
        e.writeBits(reinterpret_cast<const uint64_t *>(l2Skip),
                    0,
                    l2SkipSize * 8);
    }
    if (l3SkipSize > 0) {
        uint8_t *l3Skip = _l3Skip._mallocStart;
        e.writeBits(reinterpret_cast<const uint64_t *>(l3Skip),
                    0,
                    l3SkipSize * 8);
    }
    if (l4SkipSize > 0) {
        uint8_t *l4Skip = _l4Skip._mallocStart;
        e.writeBits(reinterpret_cast<const uint64_t *>(l4Skip),
                    0,
                    l4SkipSize * 8);
    }

    // Write features
    e.writeBits(static_cast<const uint64_t *>(_featureWriteContext._comprBuf),
                0,
                _featureOffset);

    _counts._numDocs += numDocs;
    if (hasMore || !_counts._segments.empty()) {
        uint64_t writePos = e.getWriteOffset();
        PostingListCounts::Segment seg;
        seg._bitLength = writePos - (_writePos + _counts._bitLength);
        seg._numDocs = numDocs;
        seg._lastDoc = _docIds.back().first;
        _counts._segments.push_back(seg);
        _counts._bitLength += seg._bitLength;
    }
    // reset tables in preparation for next word or next chunk
    _zcDocIds.clear();
    _l1Skip.clear();
    _l2Skip.clear();
    _l3Skip.clear();
    _l4Skip.clear();
    resetWord();
}


void
Zc4PostingSeqWrite::flushWordNoSkip()
{
    // Too few document ids for skip info.
    assert(_docIds.size() < _minSkipDocs && _counts._segments.empty());

    _encodeFeatures->flush();
    EncodeContext &e = _encodeContext;
    uint32_t numDocs = _docIds.size();

    e.encodeExpGolomb(numDocs - 1, K_VALUE_ZCPOSTING_NUMDOCS);

    uint32_t baseDocId = 1;
    const uint64_t *features =
        static_cast<const uint64_t *>(_featureWriteContext._comprBuf);
    uint64_t featureOffset = 0;

    std::vector<DocIdAndFeatureSize>::const_iterator dit = _docIds.begin();
    std::vector<DocIdAndFeatureSize>::const_iterator dite = _docIds.end();

    for (; dit != dite; ++dit) {
        uint32_t docId = dit->first;
        uint32_t featureSize = dit->second;
        e.encodeExpGolomb(docId - baseDocId, K_VALUE_ZCPOSTING_DELTA_DOCID);
        baseDocId = docId + 1;
        e.writeBits(features + (featureOffset >> 6),
                    featureOffset & 63,
                    featureSize);
        featureOffset += featureSize;
    }
    _counts._numDocs += numDocs;
    resetWord();
}


void
Zc4PostingSeqWrite::resetWord()
{
    _docIds.clear();
    _encodeFeatures->setupWrite(_featureWriteContext);
    _featureOffset = 0;
}


ZcPostingSeqRead::ZcPostingSeqRead(PostingListCountFileSeqRead *countFile)
    : Zc4PostingSeqRead(countFile)
{
    _dynamicK = true;
}


void
ZcPostingSeqRead::
readDocIdAndFeatures(DocIdAndFeatures &features)
{
    if (_residue == 0 && !_hasMore) {
        if (_rangeEndOffset != 0) {
            DecodeContext &d = *_decodeContext;
            uint64_t curOffset = d.getReadOffset();
            assert(curOffset <= _rangeEndOffset);
            if (curOffset < _rangeEndOffset)
                readWordStart();
        }
        if (_residue == 0) {
            // Don't read past end of posting list.
            features.clear(static_cast<uint32_t>(-1));
            return;
        }
    }
    if (_lastDocId > 0) {
        readCommonWordDocIdAndFeatures(features);
        return;
    }
    // Interleaves docid & features
    typedef FeatureEncodeContextBE EC;
    DecodeContext &d = *_decodeContext;
    uint32_t length;
    uint64_t val64;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);

    UC64BE_DECODEEXPGOLOMB_SMALL_NS(o,
                                    _docIdK,
                                    EC);
    uint32_t docId = _prevDocId + 1 + val64;
    features._docId = docId;
    _prevDocId = docId;
    UC64_DECODECONTEXT_STORE(o, d._);
    if (__builtin_expect(oCompr >= d._valE, false)) {
        _readContext.readComprBuffer();
    }
    _decodeContext->readFeatures(features);
    --_residue;
}


const vespalib::string &
ZcPostingSeqRead::getIdentifier()
{
    return myId5;
}


ZcPostingSeqWrite::ZcPostingSeqWrite(PostingListCountFileSeqWrite *countFile)
    : Zc4PostingSeqWrite(countFile)
{
    _dynamicK = true;
}


void
ZcPostingSeqWrite::flushWordNoSkip()
{
    // Too few document ids for skip info.
    assert(_docIds.size() < _minSkipDocs && _counts._segments.empty());

    _encodeFeatures->flush();
    EncodeContext &e = _encodeContext;
    uint32_t numDocs = _docIds.size();

    e.encodeExpGolomb(numDocs - 1, K_VALUE_ZCPOSTING_NUMDOCS);

    uint32_t docIdK = e.calcDocIdK(numDocs, _docIdLimit);

    uint32_t baseDocId = 1;
    const uint64_t *features =
        static_cast<const uint64_t *>(_featureWriteContext._comprBuf);
    uint64_t featureOffset = 0;

    std::vector<DocIdAndFeatureSize>::const_iterator dit = _docIds.begin();
    std::vector<DocIdAndFeatureSize>::const_iterator dite = _docIds.end();

    for (; dit != dite; ++dit) {
        uint32_t docId = dit->first;
        uint32_t featureSize = dit->second;
        e.encodeExpGolomb(docId - baseDocId, docIdK);
        baseDocId = docId + 1;
        e.writeBits(features + (featureOffset >> 6),
                    featureOffset & 63,
                    featureSize);
        featureOffset += featureSize;
    }
    _counts._numDocs += numDocs;
    resetWord();
}

} // namespace search::diskindex

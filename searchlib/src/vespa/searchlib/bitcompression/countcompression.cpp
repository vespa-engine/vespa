// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "countcompression.h"
#include <vespa/searchlib/index/postinglistcounts.h>
#include <cassert>

namespace search::bitcompression {

#define K_VALUE_COUNTFILE_LASTDOCID 22
#define K_VALUE_COUNTFILE_NUMCHUNKS 1
#define K_VALUE_COUNTFILE_CHUNKNUMDOCS 18
#define K_VALUE_COUNTFILE_SPNUMDOCS 0


void
PostingListCountFileDecodeContext::
readCounts(PostingListCounts &counts)
{
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;
    const uint64_t *valE = _valE;
    uint32_t numDocs;

    counts._segments.clear();
    UC64BE_DECODEEXPGOLOMB_NS(o,
                              K_VALUE_COUNTFILE_SPNUMDOCS,
                              EC);
    numDocs = static_cast<uint32_t>(val64) + 1;
    counts._numDocs = numDocs;
    if (numDocs != 0) {
        uint64_t expVal = numDocs * static_cast<uint64_t>(_avgBitsPerDoc);
        uint32_t kVal = (expVal < 4) ? 1 : EC::asmlog2(expVal);
        UC64BE_DECODEEXPGOLOMB_NS(o,
                                  kVal,
                                  EC);
        counts._bitLength = val64;
    } else
        counts._bitLength = 0;
    if (__builtin_expect(oCompr >= valE, false)) {
        UC64_DECODECONTEXT_STORE(o, _);
        _readContext->readComprBuffer();
        valE = _valE;
        UC64_DECODECONTEXT_LOAD(o, _);
    }
    uint32_t numChunks = 0;
    if (numDocs >= _minChunkDocs) {
        UC64BE_DECODEEXPGOLOMB_NS(o,
                                  K_VALUE_COUNTFILE_NUMCHUNKS,
                                  EC);
        numChunks = static_cast<uint32_t>(val64);
        if (__builtin_expect(oCompr >= valE, false)) {
            UC64_DECODECONTEXT_STORE(o, _);
            _readContext->readComprBuffer();
            valE = _valE;
            UC64_DECODECONTEXT_LOAD(o, _);
        }
    }
    if (numChunks != 0) {
        uint32_t prevLastDoc = 0u;
        counts._segments.reserve(numChunks);
        for (uint32_t chunk = 0; chunk < numChunks; ++chunk) {
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
            PostingListCounts::Segment seg;
            UC64BE_DECODEEXPGOLOMB_NS(o,
                                      K_VALUE_COUNTFILE_CHUNKNUMDOCS,
                                      EC);
            seg._numDocs = static_cast<uint32_t>(val64) + 1;
            UC64BE_DECODEEXPGOLOMB_NS(o,
                                      K_VALUE_COUNTFILE_POSOCCBITS,
                                      EC);
            seg._bitLength = val64;
            UC64BE_DECODEEXPGOLOMB_NS(o,
                                      K_VALUE_COUNTFILE_LASTDOCID,
                                      EC);
            seg._lastDoc =
                static_cast<uint32_t>(val64) + seg._numDocs + prevLastDoc;
            prevLastDoc = seg._lastDoc;
            counts._segments.push_back(seg);
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
    if (__builtin_expect(oCompr >= valE, false)) {
        _readContext->readComprBuffer();
    }
}

void
PostingListCountFileDecodeContext::
copyParams(const PostingListCountFileDecodeContext &rhs)
{
    _avgBitsPerDoc = rhs._avgBitsPerDoc;
    _minChunkDocs = rhs._minChunkDocs;
    _docIdLimit = rhs._docIdLimit;
    _numWordIds = rhs._numWordIds;
}


void
PostingListCountFileEncodeContext::
writeCounts(const PostingListCounts &counts)
{
    assert(counts._segments.empty() ||
           counts._numDocs >= _minChunkDocs);
    uint32_t numDocs = counts._numDocs;
    assert(numDocs > 0);
    encodeExpGolomb(numDocs - 1, K_VALUE_COUNTFILE_SPNUMDOCS);
    if (numDocs == 0) {
        if (__builtin_expect(_valI >= _valE, false)) {
            _writeContext->writeComprBuffer(false);
        }
        return;
    }
    uint64_t encodeVal = counts._bitLength;
    uint64_t expVal = numDocs * static_cast<uint64_t>(_avgBitsPerDoc);
    uint32_t kVal = (expVal < 4) ? 1 : asmlog2(expVal);
    encodeExpGolomb(encodeVal, kVal);
    uint32_t numChunks = counts._segments.size();
    if (numDocs >= _minChunkDocs) {
        encodeExpGolomb(numChunks, K_VALUE_COUNTFILE_NUMCHUNKS);
    }
    if (numChunks != 0) {
        using segit = std::vector<PostingListCounts::Segment>::const_iterator;

        segit ite = counts._segments.end();

        uint32_t prevLastDoc = 0u;
        for (segit it = counts._segments.begin(); it != ite; ++it) {
            if (__builtin_expect(_valI >= _valE, false)) {
                _writeContext->writeComprBuffer(false);
            }
            encodeExpGolomb(it->_numDocs - 1,
                            K_VALUE_COUNTFILE_CHUNKNUMDOCS);
            encodeExpGolomb(it->_bitLength,
                            K_VALUE_COUNTFILE_POSOCCBITS);
            encodeExpGolomb(it->_lastDoc - prevLastDoc - it->_numDocs,
                            K_VALUE_COUNTFILE_LASTDOCID);
            prevLastDoc = it->_lastDoc;
        }
    }
    if (__builtin_expect(_valI >= _valE, false)) {
        _writeContext->writeComprBuffer(false);
    }
}

void
PostingListCountFileEncodeContext::
copyParams(const PostingListCountFileEncodeContext &rhs)
{
    _avgBitsPerDoc = rhs._avgBitsPerDoc;
    _minChunkDocs = rhs._minChunkDocs;
    _docIdLimit = rhs._docIdLimit;
    _numWordIds = rhs._numWordIds;
}

}

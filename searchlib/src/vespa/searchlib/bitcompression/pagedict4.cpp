// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pagedict4.h"
#include "compression.h"
#include "countcompression.h"
#include <vespa/searchlib/index/postinglistcounts.h>
#include <vespa/searchlib/index/dictionaryfile.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".pagedict4");

namespace search {

namespace bitcompression {

namespace {

void
setDecoderPositionHelper(PostingListCountFileDecodeContext &ctx,
                         const void *buffer,
                         uint64_t offset)
{
    const uint64_t *p = static_cast<const uint64_t *>(buffer);
    ctx._valI = p + offset / 64;
    ctx.setupBits(offset & 63);
    ctx.defineReadOffset(offset);
}

void
setDecoderPositionInPage(PostingListCountFileDecodeContext &ctx,
                         const void *buffer,
                         uint64_t offset)
{
    ctx.afterRead(buffer,
                  PageDict4PageParams::getPageBitSize() / 64,
                  PageDict4PageParams::getPageBitSize() / 8,
                  false);
    setDecoderPositionHelper(ctx, buffer, offset);
}

void
setDecoderPosition(PostingListCountFileDecodeContext &ctx,
                   const ComprBuffer &cb,
                   uint64_t offset)
{
    ctx.afterRead(cb._comprBuf,
                  cb._comprBufSize,
                  cb._comprBufSize * sizeof(uint64_t),
                  false);
    setDecoderPositionHelper(ctx, cb._comprBuf, offset);
}


}


uint32_t
PageDict4PageParams::getFileHeaderPad(uint32_t offset)
{
    uint32_t pad = (- offset & getPageBitSize());
    return pad > getMaxFileHeaderPad() ? 0u : pad;
}


std::ostream &
operator<<(std::ostream &stream, const index::PostingListCounts &counts)
{
    stream << "(d=" << counts._numDocs << ",b=" << counts._bitLength << ")";
    return stream;
}

typedef index::PostingListCounts Counts;
typedef PageDict4StartOffset StartOffset;

#define K_VALUE_COUNTFILE_L1_FILEOFFSET 7
#define K_VALUE_COUNTFILE_L2_FILEOFFSET 11
#define K_VALUE_COUNTFILE_L3_FILEOFFSET 13
#define K_VALUE_COUNTFILE_L4_FILEOFFSET 15
#define K_VALUE_COUNTFILE_L5_FILEOFFSET 17
#define K_VALUE_COUNTFILE_L6_FILEOFFSET 19

#define K_VALUE_COUNTFILE_L1_WORDOFFSET 7
#define K_VALUE_COUNTFILE_L2_WORDOFFSET 10
#define K_VALUE_COUNTFILE_L4_WORDOFFSET 7
#define K_VALUE_COUNTFILE_L5_WORDOFFSET 10

#define K_VALUE_COUNTFILE_L1_COUNTOFFSET 8
#define K_VALUE_COUNTFILE_L2_COUNTOFFSET 11
#define K_VALUE_COUNTFILE_L2_L1OFFSET 8

#define K_VALUE_COUNTFILE_L4_L3OFFSET 8
#define K_VALUE_COUNTFILE_L5_L3OFFSET 11
#define K_VALUE_COUNTFILE_L5_L4OFFSET 8

#define K_VALUE_COUNTFILE_L6_PAGENUM 7

#define K_VALUE_COUNTFILE_L3_WORDNUM 7
#define K_VALUE_COUNTFILE_L4_WORDNUM 11
#define K_VALUE_COUNTFILE_L5_WORDNUM 14
#define K_VALUE_COUNTFILE_L6_WORDNUM 17

#define K_VALUE_COUNTFILE_L1_ACCNUMDOCS 4
#define K_VALUE_COUNTFILE_L2_ACCNUMDOCS 8
#define K_VALUE_COUNTFILE_L3_ACCNUMDOCS 10
#define K_VALUE_COUNTFILE_L4_ACCNUMDOCS 12
#define K_VALUE_COUNTFILE_L5_ACCNUMDOCS 14
#define K_VALUE_COUNTFILE_L6_ACCNUMDOCS 16

static uint32_t
getLCP(const vespalib::stringref &word,
       const vespalib::stringref &prevWord)
{
    size_t len1 = word.size();
    size_t len2 = prevWord.size();

    size_t res = 0;
    while (res < len1 &&
           res < len2 &&
           res < 254u &&
           word[res] == prevWord[res])
        ++res;
    return res;
}


static void
addLCPWord(const vespalib::stringref &word, size_t lcp, std::vector<char> &v)
{
    v.push_back(lcp);
    size_t pos = lcp;
    size_t len = word.size();
    while (pos < len) {
        v.push_back(word[pos]);
        ++pos;
    }
    v.push_back(0);
}


static void
writeStartOffset(PostingListCountFileEncodeContext &e,
                 const StartOffset &startOffset,
                 const StartOffset &prevStartOffset,
                 uint32_t fileOffsetK,
                 uint32_t accNumDocsK)
{
    e.encodeExpGolomb(startOffset._fileOffset -
                      prevStartOffset._fileOffset,
                      fileOffsetK);
    e.encodeExpGolomb(startOffset._accNumDocs -
                      prevStartOffset._accNumDocs,
                      accNumDocsK);
    e.writeComprBufferIfNeeded();
}


static void
readStartOffset(PostingListCountFileDecodeContext &d,
                StartOffset &startOffset,
                uint32_t fileOffsetK,
                uint32_t accNumDocsK)
{
    typedef PostingListCountFileEncodeContext EC;

    UC64_DECODECONTEXT(o);
    uint32_t length;
    uint64_t val64;
    const bool bigEndian = true;
    UC64_DECODECONTEXT_LOAD(o, d._);
    UC64_DECODEEXPGOLOMB_NS(o,
                            fileOffsetK,
                            EC);
    startOffset._fileOffset += val64;
    UC64_DECODEEXPGOLOMB_NS(o,
                            accNumDocsK,
                            EC);
    startOffset._accNumDocs += val64;
    UC64_DECODECONTEXT_STORE(o, d._);
    d.readComprBufferIfNeeded();
}


PageDict4SSWriter::PageDict4SSWriter(SSEC &sse)
    : _eL6(sse),
      _l6Word(),
      _l6StartOffset(),
      _l6PageNum(0u),
      _l6SparsePageNum(0u),
      _l6WordNum(1u)
{
}

PageDict4SSWriter::~PageDict4SSWriter()
{
}

void
PageDict4SSWriter::addL6Skip(const vespalib::stringref &word,
                             const StartOffset &startOffset,
                             uint64_t wordNum,
                             uint64_t pageNum,
                             uint32_t sparsePageNum)
{
#if 0
    LOG(info,
        "addL6SKip, \"%s\" -> wordnum %d, page (%d,%d) startOffset %" PRId64
        ", SS bitOffset %" PRIu64,
        word.c_str(),
        (int) wordNum,
        (int) pageNum,
        (int) sparsePageNum,
        startOffset.empty() ?
        static_cast<int64_t>(0) :
        startOffset[0]._fileOffset,
        _eL6.getWriteOffset());
#endif
    _eL6.writeBits(0, 1);   // Selector bit
    writeStartOffset(_eL6,
                     startOffset,
                     _l6StartOffset,
                     K_VALUE_COUNTFILE_L6_FILEOFFSET,
                     K_VALUE_COUNTFILE_L6_ACCNUMDOCS);
    _eL6.encodeExpGolomb(wordNum - _l6WordNum,
                         K_VALUE_COUNTFILE_L6_WORDNUM);
    _eL6.writeComprBufferIfNeeded();
    size_t lcp = getLCP(word, _l6Word);
    vespalib::stringref wordSuffix = word.substr(lcp);
    _eL6.smallAlign(8);
#if 0
    LOG(info,
        "lcp=%d, at offset %" PRIu64 ,
        (int) lcp,
        _eL6.getWriteOffset());
#endif
    _eL6.writeBits(lcp, 8);
    _eL6.writeComprBufferIfNeeded();
    _eL6.writeString(wordSuffix);
    assert(pageNum >= _l6PageNum);
    _eL6.encodeExpGolomb(pageNum - _l6PageNum,
                         K_VALUE_COUNTFILE_L6_PAGENUM);
    _eL6.writeComprBufferIfNeeded();
    assert(_l6PageNum < pageNum);
    assert(_l6SparsePageNum + 1 == sparsePageNum);
    _l6SparsePageNum = sparsePageNum;
    _l6PageNum = pageNum;
    _l6StartOffset = startOffset;
    _l6Word = word;
    _l6WordNum = wordNum;
#if 0
    LOG(info, "after .. SS bit Offset %" PRId64,
        _eL6.getWriteOffset());
#endif
}


void
PageDict4SSWriter::
addOverflowCounts(const vespalib::stringref &word,
                  const Counts &counts,
                  const StartOffset &startOffset,
                  uint64_t wordNum)
{
#if 0
    std::ostringstream txtCounts;
    std::ostringstream txtStartOffset;
    std::ostringstream txtL6StartOffset;
    txtCounts << counts;
    txtStartOffset << startOffset;
    txtL6StartOffset << _l6StartOffset;
    LOG(info,
        "addL6Overflow, \"%s\" wordNum %d, counts %s fileoffset %s l6startOffset %s",
        word.c_str(),
        (int) wordNum,
        txtCounts.str().c_str(),
        txtStartOffset.str().c_str(),
        txtL6StartOffset.str().c_str());
#endif
    _eL6.writeBits(1, 1);   // Selector bit
    writeStartOffset(_eL6,
                     startOffset,
                     _l6StartOffset,
                     K_VALUE_COUNTFILE_L6_FILEOFFSET,
                     K_VALUE_COUNTFILE_L6_ACCNUMDOCS);
    _eL6.encodeExpGolomb(wordNum - _l6WordNum,
                         K_VALUE_COUNTFILE_L6_WORDNUM);
    _eL6.writeComprBufferIfNeeded();
    _eL6.smallAlign(8);
    size_t lcp = getLCP(word, _l6Word);
    vespalib::stringref wordSuffix = word.substr(lcp);
    _eL6.writeBits(lcp, 8);
    _eL6.writeComprBufferIfNeeded();
    _eL6.writeString(wordSuffix);
    _eL6.writeCounts(counts);
    _l6StartOffset = startOffset;
    _l6StartOffset.adjust(counts);
    _l6Word = word;
    _l6WordNum = wordNum;
}


void
PageDict4SSWriter::flush()
{
}


PageDict4SPWriter::PageDict4SPWriter(SSWriter &ssWriter,
                                     EC &spe)
    : _eL3(),
      _wcL3(_eL3),
      _eL4(),
      _wcL4(_eL4),
      _eL5(),
      _wcL5(_eL5),
      _l3Word(),
      _l4Word(),
      _l5Word(),
      _l6Word(),
      _l3WordOffset(0u),
      _l4WordOffset(0u),
      _l5WordOffset(0u),
      _l3StartOffset(),
      _l4StartOffset(),
      _l5StartOffset(),
      _l6StartOffset(),
      _l3WordNum(1u),
      _l4WordNum(1u),
      _l5WordNum(1u),
      _l6WordNum(1u),
      _curL3OffsetL4(0u),
      _curL3OffsetL5(0u),
      _curL4OffsetL5(0u),
      _headerSize(getPageHeaderBitSize()),
      _l3Entries(0u),
      _l4StrideCheck(0u),
      _l5StrideCheck(0u),
      _l3Size(0u),
      _l4Size(0u),
      _l5Size(0u),
      _prevL3Size(0u),
      _prevL4Size(0u),
      _prevL5Size(0u),
      _prevWordsSize(0u),
      _sparsePageNum(0u),
      _l3PageNum(0u),
      _ssWriter(ssWriter),
      _spe(spe)
{
}


void
PageDict4SPWriter::setup()
{
    _eL3.copyParams(_spe);
    _eL4.copyParams(_spe);
    _eL5.copyParams(_spe);
    _l6Word.clear();
    _wcL3.allocComprBuf(getPageByteSize() * 2, getPageByteSize() * 2);
    _wcL4.allocComprBuf(getPageByteSize() * 2, getPageByteSize() * 2);
    _wcL5.allocComprBuf(getPageByteSize() * 2, getPageByteSize() * 2);
    _eL3.setWriteContext(&_wcL3);
    _eL4.setWriteContext(&_wcL4);
    _eL5.setWriteContext(&_wcL5);
    _l3Word = _l6Word;
    _l4Word = _l6Word;
    _l5Word = _l6Word;
    _l3WordOffset = 0u;
    _l4WordOffset = 0u;
    _l5WordOffset = 0u;
    _l3StartOffset = _l6StartOffset;
    // Handle extra padding after file header
    _spe.padBits(getFileHeaderPad(_spe.getWriteOffset()));
    resetPage();
    _headerSize += _spe.getWriteOffset() & (getPageBitSize() - 1);
}


PageDict4SPWriter::~PageDict4SPWriter()
{
}


void
PageDict4SPWriter::flushPage()
{
    assert(_l3Entries > 0);
    assert(_l3Size > 0);
    assert(_headerSize >= getPageHeaderBitSize());
    uint32_t wordsSize = _prevWordsSize;
    assert(_prevL3Size + _prevL4Size + _prevL5Size + _headerSize +
           wordsSize * 8 <= getPageBitSize());
    assert(_prevL5Size < (1u << 15));
    assert(_prevL4Size < (1u << 15));
    assert(_prevL3Size < (1u << 15));
    assert(_l3Entries < (1u << 15));
    assert(wordsSize < (1u << 12));
    assert(wordsSize <= _words.size());

    uint32_t l4Residue = getL4Entries(_l3Entries);
    uint32_t l5Residue = getL5Entries(l4Residue);

    assert((l4Residue == 0) == (_prevL4Size == 0));
    assert((l5Residue == 0) == (_prevL5Size == 0));
    (void) l5Residue;

    EC &e = _spe;
    e.writeBits(_prevL5Size, 15);
    e.writeBits(_prevL4Size, 15);
    e.writeBits(_l3Entries, 15);
    e.writeBits(wordsSize, 12);
    e.writeComprBufferIfNeeded();
    if (_prevL5Size > 0) {
        _eL5.flush();
        const uint64_t *l5Buf = static_cast<const uint64_t *>(_wcL5._comprBuf);
        e.writeBits(l5Buf, 0, _prevL5Size);
    }
    if (_prevL4Size > 0) {
        _eL4.flush();
        const uint64_t *l4Buf = static_cast<const uint64_t *>(_wcL4._comprBuf);
        e.writeBits(l4Buf, 0, _prevL4Size);
    }
    _eL3.flush();
    const uint64_t *l3Buf = static_cast<const uint64_t *>(_wcL3._comprBuf);
    e.writeBits(l3Buf, 0, _prevL3Size);
    uint32_t padding = getPageBitSize() - _headerSize - _prevL5Size - _prevL4Size -
                       _prevL3Size - wordsSize * 8;
    e.padBits(padding);
    if (wordsSize > 0) {
        // Pad with 7 NUL bytes to silence testing tools.
        _words.reserve(_words.size() + 7);
        memset(&*_words.end(), '\0', 7);
        const char *wordsBufX = static_cast<const char *>(&_words[0]);
        size_t wordsBufXOff = reinterpret_cast<unsigned long>(wordsBufX) & 7;
        const uint64_t *wordsBuf = reinterpret_cast<const uint64_t *>
                                   (wordsBufX - wordsBufXOff);
        e.writeBits(wordsBuf, 8 * wordsBufXOff, wordsSize * 8);
    }
    assert((e.getWriteOffset() & (getPageBitSize() - 1)) == 0);
    _l6Word = _l3Word;
    _l6StartOffset = _l3StartOffset;
    _l6WordNum = _l3WordNum;
    ++_sparsePageNum;
}


void
PageDict4SPWriter::flush()
{
    if (!empty()) {
        flushPage();
        _ssWriter.addL6Skip(_l6Word,
                            _l6StartOffset,
                            _l6WordNum,
                            _l3PageNum, getSparsePageNum());
    }
    _ssWriter.flush();
}


void
PageDict4SPWriter::resetPage()
{
    _eL3.setupWrite(_wcL3);
    _eL4.setupWrite(_wcL4);
    _eL5.setupWrite(_wcL5);
    assert(_eL3.getWriteOffset() == 0);
    assert(_eL4.getWriteOffset() == 0);
    assert(_eL5.getWriteOffset() == 0);
    _l3Word = _l6Word;
    _l4Word = _l6Word;
    _l5Word = _l6Word;
    _l3WordOffset = 0u;
    _l4WordOffset = 0u;
    _l5WordOffset = 0u;
    _l3StartOffset = _l6StartOffset;
    _l4StartOffset = _l6StartOffset;
    _l5StartOffset = _l6StartOffset;
    _l3WordNum = _l6WordNum;
    _l4WordNum = _l6WordNum;
    _l5WordNum = _l6WordNum;
    _curL3OffsetL4 = 0u;
    _curL3OffsetL5 = 0u;
    _curL4OffsetL5 = 0u;
    _l3Entries = 0u;
    _l4StrideCheck = 0u;
    _l5StrideCheck = 0u;
    _l3Size = 0u;
    _l4Size = 0u;
    _l5Size = 0u;
    _prevL3Size = 0u;
    _prevL4Size = 0u;
    _prevL5Size = 0u;
    _prevWordsSize = 0u;
    _words.clear();
    _headerSize = getPageHeaderBitSize();
}


void
PageDict4SPWriter::addL3Skip(const vespalib::stringref &word,
                             const StartOffset &startOffset,
                             uint64_t wordNum,
                             uint64_t pageNum)
{
#if 0
    LOG(info,
        "addL3Skip(\"%s\"), wordNum=%d pageNum=%d",
        word.c_str(), (int) wordNum, (int) pageNum);
#endif
    assert(_l3WordOffset == _words.size());
    /*
     * Update notion of previous size, converting tentative writes to
     * full writes.  This is used when flushing page, since last entry
     * on each page (possibly overflowing page) is elided, in practice
     * promoted to an L6 entry at SS level.
     */
    _prevL3Size = _l3Size;
    _prevL4Size = _l4Size;
    _prevL5Size = _l5Size;
    _prevWordsSize = _l3WordOffset;

    /*
     * Tentative write of counts, word and skip info.  Converted to full
     * write when new entry is tentatively added to same page.
     */
    writeStartOffset(_eL3,
                     startOffset,
                     _l3StartOffset,
                     K_VALUE_COUNTFILE_L3_FILEOFFSET,
                     K_VALUE_COUNTFILE_L3_ACCNUMDOCS);
#if 0
    LOG(info,
        "Adding l3 delta %d", (int) (wordNum - _l3WordNum));
#endif
    _eL3.encodeExpGolomb(wordNum - _l3WordNum,
                         K_VALUE_COUNTFILE_L3_WORDNUM);
    _eL3.writeComprBufferIfNeeded();
    _l3Size = static_cast<uint32_t>(_eL3.getWriteOffset());
    size_t lcp = getLCP(word, _l3Word);
    _l3Word = word;
    _l3StartOffset = startOffset;
    _l3WordNum = wordNum;
    ++_l3Entries;
    ++_l4StrideCheck;
    if (_l4StrideCheck >= getL4SkipStride())
        addL4Skip(lcp);
    addLCPWord(word, lcp, _words);
    _l3WordOffset = _words.size();
    _l3PageNum = pageNum;
    if (_l3Size + _l4Size + _l5Size + _headerSize + 8 * _l3WordOffset >
        getPageBitSize()) {
        // Cannot convert tentative writes to full writes due to overflow.
        // Flush existing full writes.
        flushPage();

        // Promote elided L3 entry to L6 entry
        _l6Word = word;
        _l6StartOffset = startOffset;
        _l6WordNum = wordNum;

        _ssWriter.addL6Skip(_l6Word,
                            _l6StartOffset,
                            _l6WordNum,
                            _l3PageNum, getSparsePageNum());
        resetPage();
    }
}


void
PageDict4SPWriter::addL4Skip(size_t &lcp)
{
#if 0
    LOG(info,
        "addL4Skip(\"%s\")",
        _l3Word.c_str());
#endif
    size_t tlcp = getLCP(_l3Word, _l4Word);
    assert(tlcp <= lcp);
    if (tlcp < lcp)
        lcp = tlcp;
    _l4StrideCheck = 0u;
    _eL4.encodeExpGolomb(_l3WordOffset - _l4WordOffset,
                         K_VALUE_COUNTFILE_L4_WORDOFFSET);
    _eL4.writeComprBufferIfNeeded();
    writeStartOffset(_eL4,
                     _l3StartOffset,
                     _l4StartOffset,
                     K_VALUE_COUNTFILE_L4_FILEOFFSET,
                     K_VALUE_COUNTFILE_L4_ACCNUMDOCS);
    _eL4.encodeExpGolomb(_l3WordNum - _l4WordNum,
                         K_VALUE_COUNTFILE_L4_WORDNUM);
    _eL4.writeComprBufferIfNeeded();
    _eL4.encodeExpGolomb(_l3Size - _curL3OffsetL4,
                         K_VALUE_COUNTFILE_L4_L3OFFSET);
    _eL4.writeComprBufferIfNeeded();
    _l4StartOffset = _l3StartOffset;
    _l4WordNum = _l3WordNum;
    _curL3OffsetL4 = _l3Size;
    _l4Size = _eL4.getWriteOffset();
    _l4Word = _l3Word;
    ++_l5StrideCheck;
    if (_l5StrideCheck >= getL5SkipStride()) {
        addL5Skip(lcp);
        _l5StrideCheck = 0;
    }
    _l4WordOffset = _l3WordOffset + 2 + _l3Word.size() - lcp;
}


void
PageDict4SPWriter::addL5Skip(size_t &lcp)
{
#if 0
    LOG(info,
        "addL5Skip(\"%s\")",
        _l3Word.c_str());
#endif
    size_t tlcp = getLCP(_l3Word, _l5Word);
    assert(tlcp <= lcp);
    if (tlcp < lcp)
        lcp = tlcp;
    _eL5.encodeExpGolomb(_l3WordOffset - _l5WordOffset,
                         K_VALUE_COUNTFILE_L5_WORDOFFSET);
    _eL5.writeComprBufferIfNeeded();
    writeStartOffset(_eL5,
                     _l3StartOffset,
                     _l5StartOffset,
                     K_VALUE_COUNTFILE_L5_FILEOFFSET,
                     K_VALUE_COUNTFILE_L5_ACCNUMDOCS);
    _eL5.encodeExpGolomb(_l3WordNum - _l5WordNum,
                         K_VALUE_COUNTFILE_L5_WORDNUM);
    _eL5.writeComprBufferIfNeeded();
    _eL5.encodeExpGolomb(_l3Size - _curL3OffsetL5,
                         K_VALUE_COUNTFILE_L5_L3OFFSET);
    _eL5.encodeExpGolomb(_l4Size - _curL4OffsetL5,
                         K_VALUE_COUNTFILE_L5_L4OFFSET);
    _eL5.writeComprBufferIfNeeded();
    _l5StartOffset = _l3StartOffset;
    _l5WordNum = _l3WordNum;
    _curL3OffsetL5 = _l3Size;
    _curL4OffsetL5 = _l4Size;
    _l5Size = _eL5.getWriteOffset();
    _l5Word = _l3Word;
    _l5WordOffset = _l3WordOffset + 2 + _l3Word.size() - lcp;
}


PageDict4PWriter::PageDict4PWriter(SPWriter &spWriter,
                                   EC &pe)
    : _eCounts(),
      _wcCounts(_eCounts),
      _eL1(),
      _wcL1(_eL1),
      _eL2(),
      _wcL2(_eL2),
      _countsWord(),
      _l1Word(),
      _l2Word(),
      _l3Word(),
      _pendingCountsWord(),
      _countsWordOffset(0u),
      _l1WordOffset(0u),
      _l2WordOffset(0u),
      _countsStartOffset(),
      _l1StartOffset(),
      _l2StartOffset(),
      _l3StartOffset(),
      _curCountOffsetL1(0u),
      _curCountOffsetL2(0u),
      _curL1OffsetL2(0u),
      _headerSize(getPageHeaderBitSize()),
      _countsEntries(0u),
      _l1StrideCheck(0u),
      _l2StrideCheck(0u),
      _countsSize(0u),
      _l1Size(0u),
      _l2Size(0u),
      _prevL1Size(0u),
      _prevL2Size(0u),
      _pageNum(0u),
      _l3WordNum(1u),
      _wordNum(1u),
      _words(),
      _spWriter(spWriter),
      _pe(pe)
{
}


void
PageDict4PWriter::setup()
{
    _eCounts.copyParams(_pe);
    _eL1.copyParams(_pe);
    _eL2.copyParams(_pe);
    _l3Word.clear();
    _wcCounts.allocComprBuf(getPageByteSize() * 2, getPageByteSize() * 2);
    _wcL1.allocComprBuf(getPageByteSize() * 2, getPageByteSize() * 2);
    _wcL2.allocComprBuf(getPageByteSize() * 2, getPageByteSize() * 2);
    _eCounts.setWriteContext(&_wcCounts);
    _eL1.setWriteContext(&_wcL1);
    _eL2.setWriteContext(&_wcL2);
    _countsWord = _l3Word;
    _l1Word = _l3Word;
    _l2Word = _l3Word;
    _pendingCountsWord.clear();
    _countsWordOffset = 0u;
    _l1WordOffset = 0u;
    _l2WordOffset = 0u;
    _countsStartOffset = _l3StartOffset;
    // Handle extra padding after file header
    _pe.padBits(getFileHeaderPad(_pe.getWriteOffset()));
    resetPage();
    _headerSize += _pe.getWriteOffset() & (getPageBitSize() - 1);
}


PageDict4PWriter::~PageDict4PWriter()
{
}


void
PageDict4PWriter::flushPage()
{
    assert(_countsEntries > 0);
    assert(_countsSize > 0);
    assert(_headerSize >= getPageHeaderBitSize());
    assert(_countsSize + _l1Size + _l2Size + _headerSize +
           8 * _countsWordOffset <= getPageBitSize());
    assert(_l2Size < (1u << 15));
    assert(_l1Size < (1u << 15));
    assert(_countsEntries < (1u << 15));
    assert(_countsWordOffset < (1u << 12));

    uint32_t l1Residue = getL1Entries(_countsEntries);
    uint32_t l2Residue = getL2Entries(l1Residue);

    assert((l1Residue == 0) == (_l1Size == 0));
    assert((l2Residue == 0) == (_l2Size == 0));
    (void) l2Residue;

    EC &e = _pe;
    e.writeBits(_l2Size, 15);
    e.writeBits(_l1Size, 15);
    e.writeBits(_countsEntries, 15);
    e.writeBits(_countsWordOffset, 12);
    e.writeComprBufferIfNeeded();
    if (_l2Size > 0) {
        _eL2.flush();
        const uint64_t *l2Buf = static_cast<const uint64_t *>(_wcL2._comprBuf);
        e.writeBits(l2Buf, 0, _l2Size);
    }
    if (_l1Size > 0) {
        _eL1.flush();
        const uint64_t *l1Buf = static_cast<const uint64_t *>(_wcL1._comprBuf);
        e.writeBits(l1Buf, 0, _l1Size);
    }
    _eCounts.flush();
    const uint64_t *countsBuf = static_cast<const uint64_t *>
                                (_wcCounts._comprBuf);
    e.writeBits(countsBuf, 0, _countsSize);
    uint32_t padding = getPageBitSize() - _headerSize - _l2Size - _l1Size -
                       _countsSize - _countsWordOffset * 8;
    e.padBits(padding);
    if (_countsWordOffset > 0) {
        // Pad with 7 NUL bytes to silence testing tools.
        _words.reserve(_words.size() + 7);
        memset(&*_words.end(), '\0', 7);
        const char *wordsBufX = static_cast<const char *>(&_words[0]);
        size_t wordsBufXOff = reinterpret_cast<unsigned long>(wordsBufX) & 7;
        const uint64_t *wordsBuf = reinterpret_cast<const uint64_t *>
                                   (wordsBufX - wordsBufXOff);
        e.writeBits(wordsBuf, 8 * wordsBufXOff, _countsWordOffset * 8);
    }
    assert((e.getWriteOffset() & (getPageBitSize() - 1)) == 0);
    _l3Word = _pendingCountsWord;
    _l3StartOffset = _countsStartOffset;
    _l3WordNum = _wordNum;
    ++_pageNum;
}


void
PageDict4PWriter::flush()
{
    if (!empty()) {
        flushPage();
        _spWriter.addL3Skip(_l3Word,
                            _l3StartOffset,
                            _l3WordNum,
                            getPageNum());
    }
    _spWriter.flush();
}


void
PageDict4PWriter::resetPage()
{
    _eCounts.setupWrite(_wcCounts);
    _eL1.setupWrite(_wcL1);
    _eL2.setupWrite(_wcL2);
    assert(_eCounts.getWriteOffset() == 0);
    assert(_eL1.getWriteOffset() == 0);
    assert(_eL2.getWriteOffset() == 0);
    _countsWord = _l3Word;
    _l1Word = _l3Word;
    _l2Word = _l3Word;
    _pendingCountsWord.clear();
    _countsWordOffset = 0u;
    _l1WordOffset = 0u;
    _l2WordOffset = 0u;
    _countsStartOffset = _l3StartOffset;
    _l1StartOffset = _l3StartOffset;
    _l2StartOffset = _l3StartOffset;
    _curCountOffsetL1 = 0u;
    _curCountOffsetL2 = 0u;
    _curL1OffsetL2 = 0u;
    _countsEntries = 0u;
    _l1StrideCheck = 0u;
    _l2StrideCheck = 0u;
    _countsSize = 0u;
    _l1Size = 0u;
    _l2Size = 0u;
    _prevL1Size = 0u;
    _prevL2Size = 0u;
    _words.clear();
    _headerSize = getPageHeaderBitSize();
}


void
PageDict4PWriter::
addCounts(const vespalib::stringref &word,
          const Counts &counts)
{
#if 0
    std::ostringstream txtcounts;
    txtcounts << counts;
    LOG(info,
        "addCounts(\"%s\", %s), wordNum=%d",
        word.c_str(),
        txtcounts.str().c_str(),
        (int) _wordNum);
#endif
    assert(_countsWordOffset == _words.size());
    size_t lcp = getLCP(_pendingCountsWord, _countsWord);
    if (_l1StrideCheck >= getL1SkipStride())
        addL1Skip(lcp);
    if (_countsEntries > 0)
        addLCPWord(_pendingCountsWord, lcp, _words);
    _eCounts.writeCounts(counts);
    uint32_t eCountsOffset = static_cast<uint32_t>(_eCounts.getWriteOffset());
    if (eCountsOffset + _l1Size + _l2Size + _headerSize +
        8 * (_countsWordOffset + 2 + _pendingCountsWord.size() - lcp) >
        getPageBitSize()) {
#if 0
        LOG(info,
            "Backtrack: eCountsOffset=%d, l1size=%d, l2size=%d, hdrsize=%d",
            (int) eCountsOffset,
            (int) _l1Size,
            (int) _l2Size,
            (int) _headerSize);
#endif
        if (_l1StrideCheck == 0u) {
            _l1Size = _prevL1Size;  // Undo L1
            _l2Size = _prevL2Size;  // Undo L2
        }
        if (_countsEntries > 0) {
            flushPage();
            _spWriter.addL3Skip(_l3Word,
                                _l3StartOffset,
                                _l3WordNum,
                                getPageNum());
            resetPage();
            _eCounts.writeCounts(counts);
            eCountsOffset = static_cast<uint32_t>(_eCounts.getWriteOffset());
        }
        if (eCountsOffset + _headerSize > getPageBitSize()) {
            // overflow page.
            addOverflowCounts(word, counts);
            _spWriter.addOverflowCounts(word, counts, _countsStartOffset,
                                        _l3WordNum);
            _spWriter.addL3Skip(_l3Word,
                                _l3StartOffset,
                                _l3WordNum,
                                getPageNum());
            resetPage();
#if 0
            std::ostringstream txtoffsets;
            txtoffsets << _countsStartOffset;
            LOG(info, "countsStartOffsets=%s", txtoffsets.str().c_str());
#endif
            return;
        }
    }
    _countsSize = eCountsOffset;
    ++_countsEntries;
    ++_l1StrideCheck;
    _countsStartOffset.adjust(counts);
#if 0
    std::ostringstream txtoffsets;
    txtoffsets << _countsStartOffset;
    LOG(info, "countsStartOffsets=%s", txtoffsets.str().c_str());
#endif
    _countsWord = _pendingCountsWord;
    _countsWordOffset = _words.size();
    _pendingCountsWord = word;
    _wordNum++;
}


/* Private use */
void
PageDict4PWriter::addOverflowCounts(const vespalib::stringref &word,
                                    const Counts &counts)
{
    assert(_countsEntries == 0);
    assert(_countsSize == 0);
    assert(_headerSize >= getPageHeaderBitSize());
    assert(_countsSize + _l1Size + _l2Size + _headerSize <= getPageBitSize());
    assert(_l2Size == 0);
    assert(_l1Size == 0);
    assert(_countsSize == 0);
    assert(_countsWordOffset == 0);

    EC &e = _pe;
    e.writeBits(0, 15);
    e.writeBits(0, 15);
    e.writeBits(0, 15);
    e.writeBits(0, 12);
    e.smallAlign(64);
    e.writeComprBufferIfNeeded();
    e.writeBits(_wordNum, 64);  // Identifies overflow for later read
#if 0
    LOG(info,
        "AddOverflowCounts wordnum %d", (int) _wordNum);
#endif
    uint32_t alignedHeaderSize = (_headerSize + 63) & -64;
    uint32_t padding = getPageBitSize() - alignedHeaderSize - 64;
    e.padBits(padding);
    assert((e.getWriteOffset() & (getPageBitSize() - 1)) == 0);
    _l3Word = word;
    _l3StartOffset = _countsStartOffset;
    _l3StartOffset.adjust(counts);
    ++_pageNum;
    ++_wordNum;
    _l3WordNum = _wordNum;
}


void
PageDict4PWriter::addL1Skip(size_t &lcp)
{
    _prevL1Size = _l1Size;  // Prepare for undo
    _prevL2Size = _l2Size;  // Prepare for undo
    size_t tlcp = getLCP(_pendingCountsWord, _l1Word);
    assert(tlcp <= lcp);
    if (tlcp < lcp)
        lcp = tlcp;
    _l1StrideCheck = 0u;
#if 0
    LOG(info,
        "addL1SKip(\"%s\"), lcp=%d, offset=%d -> %d",
        _pendingCountsWord.c_str(),
        (int) lcp,
        (int) _l1WordOffset,
        (int) _countsWordOffset);
#endif
    _eL1.encodeExpGolomb(_countsWordOffset - _l1WordOffset,
                         K_VALUE_COUNTFILE_L1_WORDOFFSET);
    _eL1.writeComprBufferIfNeeded();
    writeStartOffset(_eL1,
                     _countsStartOffset,
                     _l1StartOffset,
                     K_VALUE_COUNTFILE_L1_FILEOFFSET,
                     K_VALUE_COUNTFILE_L1_ACCNUMDOCS);
    _eL1.encodeExpGolomb(_countsSize - _curCountOffsetL1,
                         K_VALUE_COUNTFILE_L1_COUNTOFFSET);
    _eL1.writeComprBufferIfNeeded();
    _l1StartOffset = _countsStartOffset;
    _curCountOffsetL1 = _countsSize;
    _l1Size = _eL1.getWriteOffset();
    ++_l2StrideCheck;
    if (_l2StrideCheck >= getL2SkipStride())
        addL2Skip(lcp);
    _l1WordOffset = _countsWordOffset + 2 + _pendingCountsWord.size() - lcp;
}


void
PageDict4PWriter::addL2Skip(size_t &lcp)
{
    size_t tlcp = getLCP(_pendingCountsWord, _l2Word);
    assert(tlcp <= lcp);
    if (tlcp < lcp)
        lcp = tlcp;
    _l2StrideCheck = 0;
#if 0
    LOG(info,
        "addL2SKip(\"%s\"), lcp=%d, offset=%d -> %d",
        _pendingCountsWord.c_str(),
        (int) lcp,
        (int) _l2WordOffset,
        (int) _countsWordOffset);
#endif
    _eL2.encodeExpGolomb(_countsWordOffset - _l2WordOffset,
                         K_VALUE_COUNTFILE_L2_WORDOFFSET);
    _eL2.writeComprBufferIfNeeded();
    writeStartOffset(_eL2,
                     _countsStartOffset,
                     _l2StartOffset,
                     K_VALUE_COUNTFILE_L2_FILEOFFSET,
                     K_VALUE_COUNTFILE_L2_ACCNUMDOCS);
    _eL2.encodeExpGolomb(_countsSize - _curCountOffsetL2,
                         K_VALUE_COUNTFILE_L2_COUNTOFFSET);
    _eL2.encodeExpGolomb(_l1Size - _curL1OffsetL2,
                         K_VALUE_COUNTFILE_L2_L1OFFSET);
    _eL2.writeComprBufferIfNeeded();
    _l2StartOffset = _countsStartOffset;
    _curCountOffsetL2 = _countsSize;
    _curL1OffsetL2 = _l1Size;
    _l2Size = _eL2.getWriteOffset();
    _l2WordOffset = _countsWordOffset + 2 + _pendingCountsWord.size() - lcp;
}


PageDict4SSLookupRes::
PageDict4SSLookupRes()
    : _l6Word(),
      _lastWord(),
      _l6StartOffset(),
      _counts(),
      _pageNum(0u),
      _sparsePageNum(0u),
      _l6WordNum(1u),
      _startOffset(),
      _res(false),
      _overflow(false)
{
}


PageDict4SSLookupRes::
~PageDict4SSLookupRes()
{
}


PageDict4SSReader::
PageDict4SSReader(ComprBuffer &cb,
                  uint32_t ssFileHeaderSize,
                  uint64_t ssFileBitLen,
                  uint32_t spFileHeaderSize,
                  uint64_t spFileBitLen,
                  uint32_t pFileHeaderSize,
                  uint64_t pFileBitLen)
    : _cb(sizeof(uint64_t)),
      _ssFileBitLen(ssFileBitLen),
      _ssStartOffset(ssFileHeaderSize * 8),
      _l7(),
      _ssd(),
      _spFileBitLen(spFileBitLen),
      _pFileBitLen(pFileBitLen),
      _spStartOffset(spFileHeaderSize * 8),
      _pStartOffset(pFileHeaderSize * 8),
      _spFirstPageNum(0u),
      _spFirstPageOffset(0u),
      _pFirstPageNum(0u),
      _pFirstPageOffset(0u),
      _overflows()
{
    // Reference existing compressed buffer
    _cb._comprBuf = cb._comprBuf;
    _cb._comprBufSize = cb._comprBufSize;
}


PageDict4SSReader::
~PageDict4SSReader()
{
}


void
PageDict4SSReader::setup(DC &ssd)
{
    _ssd.copyParams(ssd);
    // Handle extra padding after file header
    uint32_t offset = _spStartOffset + getFileHeaderPad(_spStartOffset);
    _spFirstPageNum = offset / getPageBitSize();
    _spFirstPageOffset = offset & (getPageBitSize() - 1);
    offset = _pStartOffset + getFileHeaderPad(_pStartOffset);
    _pFirstPageNum = offset / getPageBitSize();
    _pFirstPageOffset = offset & (getPageBitSize() - 1);
    // setup();

    DC dL6;

#if 0
    LOG(info,
        "comprBuf=%p, comprBufSize=%d",
        static_cast<const void *>(_cb._comprBuf),
        (int) _cb._comprBufSize);
#endif
    setDecoderPosition(dL6, _cb, _ssStartOffset);

    dL6.copyParams(_ssd);

    _l7.clear();

    vespalib::string word;
    Counts counts;
    StartOffset startOffset;
    uint64_t pageNum = _pFirstPageNum;
    uint32_t sparsePageNum = _spFirstPageNum;
    uint32_t l7StrideCheck = 0;
    uint32_t l7Ref = noL7Ref(); // Last L6 entry not after this L7 entry

    uint64_t l6Offset = dL6.getReadOffset();
    uint64_t l6WordNum = 1;
    bool forceL7Entry = false;
    bool overflow = false;

    while (l6Offset < _ssFileBitLen) {
#if 0
        LOG(info,
            "L6Offset=%" PRIu32 ", bitLen=%" PRIu64,
            l6Offset,
            _ssFileBitLen);
#endif
        UC64_DECODECONTEXT(o);
        uint32_t length;
        uint64_t val64;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, dL6._);
        overflow = ((oVal & TOP_BIT64) != 0);
        oVal <<= 1;
        length = 1;
        UC64_READBITS_NS(o, EC);
        UC64_DECODECONTEXT_STORE(o, dL6._);

        /*
         * L7 entry for each 16th L6 entry and right before and after any
         * overflow entry.
         */
        if (l7StrideCheck >= getL7SkipStride() ||
            (l7StrideCheck > 0 && (overflow || forceL7Entry))) {
            // Don't update l7Ref if this L7 entry points to an overflow entry
            if (!forceL7Entry)
                l7Ref = _l7.size(); // Self-ref if referencing L6 entry
            _l7.push_back(L7Entry(word, startOffset, l6WordNum,
                                  l6Offset, sparsePageNum, pageNum, l7Ref));
            l7StrideCheck = 0;
            forceL7Entry = false;
        }
        readStartOffset(dL6,
                        startOffset,
                        K_VALUE_COUNTFILE_L6_FILEOFFSET,
                        K_VALUE_COUNTFILE_L6_ACCNUMDOCS);
        UC64_DECODECONTEXT_LOAD(o, dL6._);
        UC64_DECODEEXPGOLOMB_NS(o,
                                K_VALUE_COUNTFILE_L6_WORDNUM,
                                EC);
#if 0
        LOG(info,
            "Bumping l6wordnum from %d to %d (delta %d)",
            (int) l6WordNum, (int) (l6WordNum + val64) , (int) val64);
#endif
        l6WordNum += val64;
        UC64_DECODECONTEXT_STORE(o, dL6._);
        dL6.smallAlign(8);
        const uint8_t *bytes = dL6.getByteCompr();
        size_t lcp = *bytes;
        ++bytes;
        assert(lcp <= word.size());
        word.resize(lcp);
        word += reinterpret_cast<const char *>(bytes);
        dL6.setByteCompr(bytes + word.size() + 1 - lcp);
        if (overflow) {
#if 0
            LOG(info,
                "AddOverflowRef2 wordnum %d", (int) (l6WordNum - 1));
#endif
            _overflows.push_back(OverflowRef(l6WordNum - 1, _l7.size()));
            dL6.readCounts(counts);
            startOffset.adjust(counts);
            forceL7Entry = true; // Add new L7 entry as soon as possible
        } else {
            UC64_DECODECONTEXT_LOAD(o, dL6._);
            UC64_DECODEEXPGOLOMB_NS(o,
                                    K_VALUE_COUNTFILE_L6_PAGENUM,
                                    EC);
            pageNum += val64;
            ++sparsePageNum;
            UC64_DECODECONTEXT_STORE(o, dL6._);
        }
#if 0
        std::ostringstream txtfileoffset;
        txtfileoffset << startOffset;
        LOG(info,
            "ssreader::setup "
            "word=%s, l6offset=%d->%d, startOffsets=%s overflow=%s",
            word.c_str(),
            (int) l6Offset,
            (int) dL6.getReadOffset(),
            txtfileoffset.str().c_str(),
            overflow ? "true" : "false");
#endif
        ++l7StrideCheck;
        l6Offset = dL6.getReadOffset();
    }
    if (l7StrideCheck > 0) {
        if (!forceL7Entry)
            l7Ref = _l7.size(); // Self-ref if referencing L6 entry
        _l7.push_back(L7Entry(word, startOffset, l6WordNum,
                              l6Offset, sparsePageNum, pageNum, l7Ref));
    }
    assert(l6Offset == _ssFileBitLen);
}


PageDict4SSLookupRes
PageDict4SSReader::
lookup(const vespalib::stringref &key)
{
    PageDict4SSLookupRes res;

    DC dL6;

    dL6.copyParams(_ssd);

    uint32_t l7Pos = 0;
    uint32_t l7Ref = noL7Ref();

    L7Vector::const_iterator l7lb;
    l7lb = std::lower_bound(_l7.begin(), _l7.end(), key);

    l7Pos = &*l7lb - &_l7[0];
    StartOffset startOffset;
    uint64_t pageNum = _pFirstPageNum;
    uint32_t sparsePageNum = _spFirstPageNum;
    uint64_t l6Offset = _ssStartOffset;
    uint64_t l6WordNum = 1;
    uint64_t wordNum = l6WordNum;

    vespalib::string l6Word;            // Last L6 entry word
    vespalib::string word;
    StartOffset l6StartOffset;  // Last L6 entry file offset

    // Setup for decoding of L6+overflow stream
    if (l7Pos > 0) {
        L7Entry &l7e = _l7[l7Pos - 1];
        l7Ref = l7e._l7Ref;
        startOffset = l7e._l7StartOffset;
        word = l7e._l7Word;
        l6Offset = l7e._l6Offset;
        wordNum = l7e._l7WordNum;
    }

    /*
     * Setup L6 only variables, used when no overflow matches.
     *
     * l7Ref == l7Pos - 1, when _l7[l7Pos -1] references end of L6
     * entry in L6+overflow stream.
     *
     * l7Ref != l7Pos - 1, when _l7[l7Pos -1] references end of overflow
     * entry in L6+overflow stream, and is used for backtracking to end
     * of previous L6 entry in L6+overflow stream.
     */
    if (l7Ref != noL7Ref()) {
        L7Entry &l7e = _l7[l7Ref];
        sparsePageNum = l7e._sparsePageNum;
        pageNum = l7e._pageNum;
        l6Word = l7e._l7Word;
        l6StartOffset = l7e._l7StartOffset;
        l6WordNum = l7e._l7WordNum;
    }

#if 0
    LOG(info,
        "sslookup1: l6WordNum=%d, l6Word=\"%s\", key=\"%s\", l6Offset=%d",
        (int) l6WordNum,
        l6Word.c_str(),
        key.c_str(),
        (int) l6Offset);
#endif

    setDecoderPosition(dL6, _cb, l6Offset);

    Counts counts;

    while (l6Offset < _ssFileBitLen) {
        UC64_DECODECONTEXT(o);
        uint32_t length;
        uint64_t val64;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, dL6._);
        bool overflow = ((oVal & TOP_BIT64) != 0);
        oVal <<= 1;
        length = 1;
        UC64_READBITS_NS(o, EC);
        UC64_DECODECONTEXT_STORE(o, dL6._);

        readStartOffset(dL6,
                        startOffset,
                        K_VALUE_COUNTFILE_L6_FILEOFFSET,
                        K_VALUE_COUNTFILE_L6_ACCNUMDOCS);
        UC64_DECODECONTEXT_LOAD(o, dL6._);
        UC64_DECODEEXPGOLOMB_NS(o,
                                K_VALUE_COUNTFILE_L6_WORDNUM,
                                EC);
        wordNum += val64;
        UC64_DECODECONTEXT_STORE(o, dL6._);
        dL6.smallAlign(8);
        const uint8_t *bytes = dL6.getByteCompr();
        size_t lcp = *bytes;
        ++bytes;
        assert(lcp <= word.size());
        word.resize(lcp);
        word += reinterpret_cast<const char *>(bytes);
        dL6.setByteCompr(bytes + word.size() + 1 - lcp);
        if (overflow) {
#if 0
            LOG(info,
                "sslookup: wordNum=%d, word=\"%s\", key=\"%s\"",
                (int) wordNum,
                word.c_str(),
                key.c_str());
#endif
            bool l6NotLessThanKey = !(word < key);
            if (l6NotLessThanKey) {
                if (key == word) {
                    dL6.readCounts(counts);
                    res._overflow = true;
                    res._counts = counts;
                    res._startOffset = startOffset;
                    l6WordNum = wordNum - 1;    // overloaded meaning
                }
                break;  // key < counts
            }
            LOG(error, "FATAL: Missing L7 entry for overflow entry");
            abort();    // counts < key, should not happen (missing L7 entry)
        } else {
            bool l6NotLessThanKey = !(word < key);
            if (l6NotLessThanKey)
                break;  // key <= counts
            UC64_DECODECONTEXT_LOAD(o, dL6._);
            UC64_DECODEEXPGOLOMB_NS(o,
                                    K_VALUE_COUNTFILE_L6_PAGENUM,
                                    EC);
            pageNum += val64;
            ++sparsePageNum;
            UC64_DECODECONTEXT_STORE(o, dL6._);
            l6Word = word;
            l6StartOffset = startOffset;
            l6WordNum = wordNum;
        }
        l6Offset = dL6.getReadOffset();
    }
    assert(l6Offset <= _ssFileBitLen);
    res._l6Word = l6Word;
    if (l6Offset >= _ssFileBitLen)
        res._lastWord.clear();  // Mark that word is beyond end of dictionary
    else
        res._lastWord = word;
    res._l6StartOffset = l6StartOffset;
    res._pageNum = pageNum;
    res._sparsePageNum = sparsePageNum;
    res._l6WordNum = l6WordNum;
    // Lookup succeeded if not run to end of L6 info or if overflow was found
    // Failed lookup means we want keys larger than the highest present.
    res._res = l6Offset < _ssFileBitLen || res._overflow;
    return res;
}


PageDict4SSLookupRes
PageDict4SSReader::
lookupOverflow(uint64_t wordNum) const
{
    PageDict4SSLookupRes res;

    assert(!_overflows.empty());

    OverflowVector::const_iterator lb =
        std::lower_bound(_overflows.begin(),
                         _overflows.end(),
                         wordNum);

    assert(lb != _overflows.end());
    assert(lb->_wordNum == wordNum);
    uint32_t l7Ref = lb->_l7Ref;
    assert(l7Ref < _l7.size());

    const vespalib::string &word = _l7[l7Ref]._l7Word;
#if 0
    LOG(info,
        "lookupOverflow: wordNum %d -> word %s, next l7 Pos is %d",
        (int) wordNum,
        word.c_str(),
        (int) l7Ref);
#endif
    uint64_t l6Offset = _ssStartOffset;
    StartOffset startOffset;
    if (l7Ref > 0) {
        l6Offset = _l7[l7Ref - 1]._l6Offset;
        startOffset = _l7[l7Ref - 1]._l7StartOffset;
    }

    StartOffset l6StartOffset;
    vespalib::string l6Word;

    uint32_t l7Ref2 = _l7[l7Ref]._l7Ref;
    if (l7Ref2 != noL7Ref()) {
        // last L6 entry before overflow entry
        const L7Entry &l6Ref = _l7[l7Ref2];
        l6Word = l6Ref._l7Word;
        l6StartOffset = l6Ref._l7StartOffset;
    }

    DC dL6;

    dL6.copyParams(_ssd);
    setDecoderPosition(dL6, _cb, l6Offset);

#if 0
    std::ostringstream txtStartOffset;
    std::ostringstream txtL6StartOffset;
    txtStartOffset << startOffset;
    txtL6StartOffset << l6StartOffset;
    LOG(info,
        "Lookupoverflow l6Offset=%d, l6fileoffset=%s, fileoffset=%s",
        (int) l6Offset,
        txtL6StartOffset.str().c_str(),
        txtStartOffset.str().c_str());
#endif

    UC64_DECODECONTEXT(o);
    uint32_t length;
    const bool bigEndian = true;
    UC64_DECODECONTEXT_LOAD(o, dL6._);
    bool overflow = ((oVal & TOP_BIT64) != 0);
    oVal <<= 1;
    length = 1;
    UC64_READBITS_NS(o, EC);
    assert(overflow);
    (void) overflow;
    UC64_DECODECONTEXT_STORE(o, dL6._);

    readStartOffset(dL6,
                    startOffset,
                    K_VALUE_COUNTFILE_L6_FILEOFFSET,
                    K_VALUE_COUNTFILE_L6_ACCNUMDOCS);
    UC64_DECODECONTEXT_LOAD(o, dL6._);
    UC64_SKIPEXPGOLOMB_NS(o,
                          K_VALUE_COUNTFILE_L6_WORDNUM,
                          EC);
    UC64_DECODECONTEXT_STORE(o, dL6._);

    dL6.smallAlign(8);
    const uint8_t *bytes = dL6.getByteCompr();
    size_t lcp = *bytes;
    ++bytes;
    assert(lcp <= word.size());
    vespalib::stringref suffix = reinterpret_cast<const char *>(bytes);
    dL6.setByteCompr(bytes + suffix.size() + 1);
    assert(lcp + suffix.size() == word.size());
    assert(suffix == word.substr(lcp));
    (void) lcp;
    Counts counts;
    dL6.readCounts(counts);
#if 0
    std::ostringstream txtCounts;
    txtStartOffset.str("");
    txtStartOffset << startOffset;
    txtCounts << counts;
    LOG(info,
        "Lookupoverflow fileoffset=%s, counts=%s",
        txtStartOffset.str().c_str(),
        txtCounts.str().c_str());
#endif
    res._overflow = true;
    res._counts = counts;
    res._startOffset = startOffset;
    res._l6StartOffset = l6StartOffset;
    res._l6Word = l6Word;
    res._lastWord = word;
    res._res = true;
    return res;
}


PageDict4SPLookupRes::
PageDict4SPLookupRes()
    : _l3Word(),
      _lastWord(),
      _l3StartOffset(),
      _pageNum(0u),
      _l3WordNum(1u)
{
}


PageDict4SPLookupRes::
~PageDict4SPLookupRes()
{
}


void
PageDict4SPLookupRes::
lookup(const SSReader &ssReader,
       const void *sparsePage,
       const vespalib::stringref &key,
       const vespalib::stringref &l6Word,
       const vespalib::stringref &lastSPWord,
       const StartOffset &l6StartOffset,
       uint64_t l6WordNum,
       uint64_t lowestPageNum)
{
//    const uint64_t *p = static_cast<const uint64_t *>(sparsePage);

    DC dL3; // L3 stream
    DC dL4; // L4 stream
    DC dL5; // L5 stream

    dL3.copyParams(ssReader.getSSD());
    dL4.copyParams(ssReader.getSSD());
    dL5.copyParams(ssReader.getSSD());
    uint32_t spStartOffset = 0;
    if (l6WordNum == 1)
        spStartOffset = ssReader._spFirstPageOffset;
    setDecoderPositionInPage(dL5, sparsePage, spStartOffset);

    uint32_t l5Size = dL5.readBits(15);
    uint32_t l4Size = dL5.readBits(15);
    uint32_t l3Entries = dL5.readBits(15);
    uint32_t wordsSize = dL5.readBits(12);
    uint32_t l3Residue = l3Entries;

    assert(l3Entries > 0);
    uint32_t l4Residue = getL4Entries(l3Entries);
    uint32_t l5Residue = getL5Entries(l4Residue);

    assert((l4Residue == 0) == (l4Size == 0));
    assert((l5Residue == 0) == (l5Size == 0));

    uint32_t l5Offset = getPageHeaderBitSize() + spStartOffset;
    uint32_t l4Offset = l5Offset + l5Size;
    uint32_t l3Offset = l4Offset + l4Size;

    assert(l5Offset == dL5.getReadOffset());

    uint32_t wordOffset = getPageByteSize() - wordsSize;
    const char *wordBuf = static_cast<const char *>(sparsePage) + wordOffset;

    _l3Word = l6Word;
    _l3StartOffset = l6StartOffset;
    vespalib::string word;
    uint32_t l3WordOffset = 0;
    uint32_t l5WordOffset = l3WordOffset;
    uint64_t l3WordNum = l6WordNum;

    while (l5Residue > 0) {
        UC64_DECODECONTEXT(o);
        uint32_t length;
        uint64_t val64;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, dL5._);
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L5_WORDOFFSET, EC);
        l5WordOffset += val64;
        UC64_DECODECONTEXT_STORE(o, dL5._);
        const char *l5WordBuf = wordBuf + l5WordOffset;
        size_t lcp = *reinterpret_cast<const unsigned char *>(l5WordBuf);
        ++l5WordBuf;
        assert(lcp <= _l3Word.size());
        word = _l3Word.substr(0, lcp) + l5WordBuf;
        bool l3NotLessThanKey = !(word < key);
        if (l3NotLessThanKey)
            break;
        _l3Word = word;
        l3WordOffset = l5WordOffset + 2 + word.size() - lcp;
        l5WordOffset = l3WordOffset;
        readStartOffset(dL5,
                        _l3StartOffset,
                        K_VALUE_COUNTFILE_L5_FILEOFFSET,
                        K_VALUE_COUNTFILE_L5_ACCNUMDOCS);
        UC64_DECODECONTEXT_LOAD(o, dL5._);
        UC64_DECODEEXPGOLOMB_NS(o,
                                K_VALUE_COUNTFILE_L5_WORDNUM,
                                EC);
        l3WordNum += val64;
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L5_L3OFFSET, EC);
        l3Offset += val64;
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L5_L4OFFSET, EC);
        l4Offset += val64;
        UC64_DECODECONTEXT_STORE(o, dL5._);
        --l5Residue;
        assert(l4Residue >= getL5SkipStride());
        l4Residue -= getL5SkipStride();
        assert(l3Residue > getL5SkipStride() * getL4SkipStride());
        l3Residue -= getL5SkipStride() * getL4SkipStride();
    }
    setDecoderPositionInPage(dL4, sparsePage, l4Offset);
    uint32_t l4WordOffset = l3WordOffset;
    while (l4Residue > 0) {
        UC64_DECODECONTEXT(o);
        uint32_t length;
        uint64_t val64;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, dL4._);
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L4_WORDOFFSET, EC);
        l4WordOffset += val64;
        UC64_DECODECONTEXT_STORE(o, dL4._);
        const char *l4WordBuf = wordBuf + l4WordOffset;
        size_t lcp = *reinterpret_cast<const unsigned char *>(l4WordBuf);
        ++l4WordBuf;
        assert(lcp <= _l3Word.size());
        word = _l3Word.substr(0, lcp) + l4WordBuf;
        bool l3NotLessThanKey = !(word < key);
        if (l3NotLessThanKey)
            break;
        _l3Word = word;
        l3WordOffset = l4WordOffset + 2 + word.size() - lcp;
        l4WordOffset = l3WordOffset;
        readStartOffset(dL4,
                        _l3StartOffset,
                        K_VALUE_COUNTFILE_L4_FILEOFFSET,
                        K_VALUE_COUNTFILE_L4_ACCNUMDOCS);
        UC64_DECODECONTEXT_LOAD(o, dL4._);
        UC64_DECODEEXPGOLOMB_NS(o,
                                K_VALUE_COUNTFILE_L4_WORDNUM,
                                EC);
        l3WordNum += val64;
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L4_L3OFFSET, EC);
        l3Offset += val64;
        UC64_DECODECONTEXT_STORE(o, dL4._);
        --l4Residue;
        assert(l3Residue > getL4SkipStride());
        l3Residue -= getL4SkipStride();
    }

    setDecoderPositionInPage(dL3, sparsePage, l3Offset);
    assert(l3Residue > 0);
    while (l3Residue > 0) {
        if (l3Residue > 1) {
            const char *l3WordBuf = wordBuf + l3WordOffset;
            size_t lcp = *reinterpret_cast<const unsigned char *>(l3WordBuf);
            ++l3WordBuf;
            assert(lcp <= _l3Word.size());
            word = _l3Word.substr(0, lcp) + l3WordBuf;
            bool l3NotLessThanKey = !(word < key);
            if (l3NotLessThanKey)
                break;
            _l3Word = word;
            l3WordOffset += 2 + word.size() - lcp;
        } else {
            word = lastSPWord;
            assert(!word.empty()); // Should've stopped at SS level
            bool l3NotLessThanKey = !(word < key);
            if (l3NotLessThanKey)
                break;
            abort();
            _l3Word = word;
        }
        readStartOffset(dL3,
                        _l3StartOffset,
                        K_VALUE_COUNTFILE_L3_FILEOFFSET,
                        K_VALUE_COUNTFILE_L3_ACCNUMDOCS);
        UC64_DECODECONTEXT(o);
        uint32_t length;
        uint64_t val64;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, dL3._);
        UC64_DECODEEXPGOLOMB_NS(o,
                                K_VALUE_COUNTFILE_L3_WORDNUM,
                                EC);
        UC64_DECODECONTEXT_STORE(o, dL3._);
        l3WordNum += val64;
        --l3Residue;
    }
    _lastWord = word;
    _pageNum = lowestPageNum + l3Entries - l3Residue;
    _l3WordNum = l3WordNum;
    // Lookup succeded if not run to end of L3 info.
    // Shoudn't have tried to look at page if word < key, i.e. lookup at this
    // level should always succeed.
    assert(l3Residue > 0);
}


PageDict4PLookupRes::
PageDict4PLookupRes()
    : _counts(),
      _startOffset(),
      _wordNum(1u),
      _res(false),
      _nextWord(NULL)
{
}


PageDict4PLookupRes::
~PageDict4PLookupRes()
{
}

bool
PageDict4PLookupRes::
lookup(const SSReader &ssReader,
       const void *page,
       const vespalib::stringref &key,
       const vespalib::stringref &l3Word,
       const vespalib::stringref &lastPWord,
       const StartOffset &l3StartOffset,
       uint64_t l3WordNum)
{
    DC dCounts; // counts stream (sparse counts)
    DC dL1;         // L1 stream
    DC dL2;         // L2 stream

    dCounts.copyParams(ssReader.getSSD());
    dL1.copyParams(ssReader.getSSD());
    dL2.copyParams(ssReader.getSSD());

    uint32_t pStartOffset = 0;
    if (l3WordNum == 1)
        pStartOffset = ssReader._pFirstPageOffset;
    setDecoderPositionInPage(dL2, page, pStartOffset);

    uint32_t l2Size = dL2.readBits(15);
    uint32_t l1Size = dL2.readBits(15);
    uint32_t countsEntries = dL2.readBits(15);
    uint32_t wordsSize = dL2.readBits(12);
    uint32_t countsResidue = countsEntries;

    if (countsEntries == 0) {
        /*
         * Tried to lookup word that is between an overflow word and
         * the previous word in the dictionary.
         */
        _startOffset = l3StartOffset;
        _wordNum = l3WordNum;
        return false;
    }

    uint32_t l1Residue = getL1Entries(countsEntries);
    uint32_t l2Residue = getL2Entries(l1Residue);

    assert((l1Residue == 0) == (l1Size == 0));
    assert((l2Residue == 0) == (l2Size == 0));

    uint32_t l2Offset = getPageHeaderBitSize() + pStartOffset;
    uint32_t l1Offset = l2Offset + l2Size;
    uint32_t countsOffset = l1Offset + l1Size;

    assert(l2Offset == dL2.getReadOffset());

    uint32_t wordOffset = getPageByteSize() - wordsSize;
    const char *wordBuf = static_cast<const char *>(page) + wordOffset;

    vespalib::string countsWord = l3Word;
    StartOffset countsStartOffset = l3StartOffset;
    vespalib::string word;
    Counts counts;

    uint32_t countsWordOffset = 0;
    uint32_t l2WordOffset = countsWordOffset;
    uint64_t wordNum = l3WordNum;
    while (l2Residue > 0) {
        UC64_DECODECONTEXT(o);
        uint32_t length;
        uint64_t val64;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, dL2._);
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L2_WORDOFFSET, EC);
        l2WordOffset += val64;
        UC64_DECODECONTEXT_STORE(o, dL2._);
        const char *l2WordBuf = wordBuf + l2WordOffset;
        size_t lcp = *reinterpret_cast<const unsigned char *>(l2WordBuf);
        ++l2WordBuf;
        assert(lcp <= countsWord.size());
        word = countsWord.substr(0, lcp) + l2WordBuf;
        bool countsNotLessThanKey = !(word < key);
        if (countsNotLessThanKey)
            break;
        countsWord = word;
        countsWordOffset = l2WordOffset + 2 + word.size() - lcp;
        l2WordOffset = countsWordOffset;

        readStartOffset(dL2,
                        countsStartOffset,
                        K_VALUE_COUNTFILE_L2_FILEOFFSET,
                        K_VALUE_COUNTFILE_L2_ACCNUMDOCS);
        UC64_DECODECONTEXT_LOAD(o, dL2._);
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L2_COUNTOFFSET, EC);
        countsOffset += val64;
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L2_L1OFFSET, EC);
        l1Offset += val64;
        UC64_DECODECONTEXT_STORE(o, dL2._);
        --l2Residue;
        assert(l1Residue >= getL2SkipStride());
        l1Residue -= getL2SkipStride();
        assert(countsResidue > getL2SkipStride() * getL1SkipStride());
        countsResidue -= getL2SkipStride() * getL1SkipStride();
        wordNum += getL2SkipStride() * getL1SkipStride();
    }
    setDecoderPositionInPage(dL1, page, l1Offset);
    uint32_t l1WordOffset = countsWordOffset;
    while (l1Residue > 0) {
        UC64_DECODECONTEXT(o);
        uint32_t length;
        uint64_t val64;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, dL1._);
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L1_WORDOFFSET, EC);
        l1WordOffset += val64;
        UC64_DECODECONTEXT_STORE(o, dL1._);
        const char *l1WordBuf = wordBuf + l1WordOffset;
        size_t lcp = *reinterpret_cast<const unsigned char *>(l1WordBuf);
        ++l1WordBuf;
        assert(lcp <= countsWord.size());
        word = countsWord.substr(0, lcp) + l1WordBuf;
        bool countsNotLessThanKey = !(word < key);
        if (countsNotLessThanKey)
            break;
        countsWord = word;
        countsWordOffset = l1WordOffset + 2 + word.size() - lcp;
        l1WordOffset = countsWordOffset;

        readStartOffset(dL1,
                        countsStartOffset,
                        K_VALUE_COUNTFILE_L1_FILEOFFSET,
                        K_VALUE_COUNTFILE_L1_ACCNUMDOCS);
        UC64_DECODECONTEXT_LOAD(o, dL1._);
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L1_COUNTOFFSET, EC);
        countsOffset += val64;
        UC64_DECODECONTEXT_STORE(o, dL1._);
        --l1Residue;
        assert(countsResidue > getL1SkipStride());
        countsResidue -= getL1SkipStride();
        wordNum += getL1SkipStride();
    }

    setDecoderPositionInPage(dCounts, page, countsOffset);
    assert(countsResidue > 0);
    while (countsResidue > 0) {
        dCounts.readCounts(counts);
        if (countsResidue > 1) {
            const char *countsWordBuf = wordBuf + countsWordOffset;
            size_t lcp =
                *reinterpret_cast<const unsigned char *>(countsWordBuf);
            ++countsWordBuf;
            assert(lcp <= countsWord.size());
            word = countsWord.substr(0, lcp) + countsWordBuf;
            bool countsNotLessThanKey = !(word < key);
            if (countsNotLessThanKey)
                break;
            countsWordOffset += 2 + word.size() - lcp;
            countsWord = word;
        } else {
            word = lastPWord;
            assert(!word.empty()); // Should've stopped at SS level
            bool countsNotLessThanKey = !(word < key);
            if (countsNotLessThanKey)
                break;
        }
        countsStartOffset.adjust(counts);
        ++wordNum;
        --countsResidue;
    }
    _startOffset = countsStartOffset;
    _wordNum = wordNum;
    // Lookup succeded if word found.
    if (key == word) {
        _counts = counts;
        _res = true;
    } else {
        // Shouldn't have tried to look at page if word < key, and we know
        // that key != word.  Thus we can assert that key < word.
        assert(key < word);
    }
   return _res;
}

PageDict4Reader::PageDict4Reader(const SSReader &ssReader,
                                 DC &spd,
                                 DC &pd)
    : _pd(pd),
      _countsResidue(0),
      _ssReader(ssReader),
      _pFileBitLen(ssReader._pFileBitLen),
      _startOffset(),
      _overflowPage(false),
      _counts(),
      _cc(),
      _ce(),
      _words(),
      _wc(),
      _we(),
      _lastWord(),
      _lastSSWord(),
      _spd(spd),
      _l3Residue(0u),
      _spwords(),
      _spwc(),
      _spwe(),
      _ssd(),
      _wordNum(1u)
{
}


void
PageDict4Reader::setup()
{
    _ssd.copyParams(_ssReader.getSSD());
    _spd.copyParams(_ssReader.getSSD());
    _pd.copyParams(_ssReader.getSSD());
    assert(_pd.getReadOffset() == _ssReader._pStartOffset);
    assert(_spd.getReadOffset() == _ssReader._spStartOffset);
    // Handle extra padding after file header
    _pd.skipBits(getFileHeaderPad(_ssReader._pStartOffset));
    _spd.skipBits(getFileHeaderPad(_ssReader._spStartOffset));
    assert(_pFileBitLen >= _pd.getReadOffset());
    if (_pFileBitLen > _pd.getReadOffset()) {
        setupPage();
        setupSPage();
    }

    const ComprBuffer &sscb  = _ssReader._cb;
    uint32_t ssStartOffset = _ssReader._ssStartOffset;
    setDecoderPosition(_ssd, sscb, ssStartOffset);
}


PageDict4Reader::~PageDict4Reader()
{
}


void
PageDict4Reader::setupPage()
{
#if 0
    LOG(info,
        "setupPage(%ld), "
        (long int) _pd.getReadOffset());
#endif
    uint32_t l2Size = _pd.readBits(15);
    uint32_t l1Size = _pd.readBits(15);
    uint32_t countsEntries = _pd.readBits(15);
    uint32_t wordsSize = _pd.readBits(12);
    _countsResidue = countsEntries;

#if 0
    _pd.skipBits(l2Size + l1Size);
    Counts counts;
#else
    if (countsEntries == 0 && l1Size == 0 && l2Size == 0) {
        _pd.smallAlign(64);
        _overflowPage = true;
        return;
    }
    _overflowPage = false;
    assert(countsEntries > 0);
    uint32_t l1Residue = getL1Entries(countsEntries);
    uint32_t l2Residue = getL2Entries(l1Residue);

    uint64_t beforePos = _pd.getReadOffset();
    Counts counts;
    StartOffset startOffset;
    while (l2Residue > 0) {
        UC64_DECODECONTEXT(o);
        uint32_t length;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, _pd._);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L2_WORDOFFSET, EC);
        UC64_DECODECONTEXT_STORE(o, _pd._);
        readStartOffset(_pd,
                        startOffset,
                        K_VALUE_COUNTFILE_L2_FILEOFFSET,
                        K_VALUE_COUNTFILE_L2_ACCNUMDOCS);
        UC64_DECODECONTEXT_LOAD(o, _pd._);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L2_COUNTOFFSET, EC);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L2_L1OFFSET, EC);
        UC64_DECODECONTEXT_STORE(o, _pd._);
        --l2Residue;
    }
    assert(_pd.getReadOffset() == beforePos + l2Size);
    while (l1Residue > 0) {
        UC64_DECODECONTEXT(o);
        uint32_t length;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, _pd._);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L1_WORDOFFSET, EC);
        UC64_DECODECONTEXT_STORE(o, _pd._);
        readStartOffset(_pd,
                        startOffset,
                        K_VALUE_COUNTFILE_L1_FILEOFFSET,
                        K_VALUE_COUNTFILE_L1_ACCNUMDOCS);
        UC64_DECODECONTEXT_LOAD(o, _pd._);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L1_COUNTOFFSET, EC);
        UC64_DECODECONTEXT_STORE(o, _pd._);
        --l1Residue;
    }
    assert(_pd.getReadOffset() == beforePos + l2Size + l1Size);
    (void) beforePos;
#endif
    _counts.clear();
    while (countsEntries > 0) {
        _pd.readCounts(counts);
        _counts.push_back(counts);
        --countsEntries;
    }
    _cc = _counts.begin();
    _ce = _counts.end();
    uint32_t pageOffset = _pd.getReadOffset() & (getPageBitSize() - 1);
    uint32_t padding = getPageBitSize() - wordsSize * 8 - pageOffset;
    _pd.skipBits(padding);
    _words.resize(wordsSize);
    _pd.readBytes(reinterpret_cast<uint8_t *>(&_words[0]), wordsSize);
    _wc = _words.begin();
    _we = _words.end();
}


void
PageDict4Reader::setupSPage()
{
#if 0
    LOG(info, "setupSPage(%d),", (int) _spd.getReadOffset());
#endif
    uint32_t l5Size = _spd.readBits(15);
    uint32_t l4Size = _spd.readBits(15);
    uint32_t l3Entries = _spd.readBits(15);
    uint32_t wordsSize = _spd.readBits(12);
    _l3Residue = l3Entries;

#if 0
    _spd.skipBits(l5Size + l4Size);
#else

    assert(l3Entries > 0);
    uint32_t l4Residue = getL4Entries(l3Entries);
    uint32_t l5Residue = getL5Entries(l4Residue);

    uint64_t beforePos = _spd.getReadOffset();
    StartOffset startOffset;
    while (l5Residue > 0) {
        UC64_DECODECONTEXT(o);
        uint32_t length;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, _spd._);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L5_WORDOFFSET, EC);
        UC64_DECODECONTEXT_STORE(o, _spd._);
        readStartOffset(_spd,
                        startOffset,
                        K_VALUE_COUNTFILE_L5_FILEOFFSET,
                        K_VALUE_COUNTFILE_L5_ACCNUMDOCS);
        UC64_DECODECONTEXT_LOAD(o, _spd._);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L5_WORDNUM, EC);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L5_L3OFFSET, EC);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L5_L4OFFSET, EC);
        UC64_DECODECONTEXT_STORE(o, _spd._);
        --l5Residue;
    }
    assert(_spd.getReadOffset() == beforePos + l5Size);
    while (l4Residue > 0) {
        UC64_DECODECONTEXT(o);
        uint32_t length;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, _spd._);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L4_WORDOFFSET, EC);
        UC64_DECODECONTEXT_STORE(o, _spd._);
        readStartOffset(_spd,
                        startOffset,
                        K_VALUE_COUNTFILE_L4_FILEOFFSET,
                        K_VALUE_COUNTFILE_L4_ACCNUMDOCS);
        UC64_DECODECONTEXT_LOAD(o, _spd._);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L4_WORDNUM, EC);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L4_L3OFFSET, EC);
        UC64_DECODECONTEXT_STORE(o, _spd._);
        --l4Residue;
    }
    assert(_spd.getReadOffset() == beforePos + l5Size + l4Size);
    (void) l4Size;
    (void) l5Size;
    (void) beforePos;
#endif
    while (l3Entries > 1) {
        readStartOffset(_spd,
                        startOffset,
                        K_VALUE_COUNTFILE_L3_FILEOFFSET,
                        K_VALUE_COUNTFILE_L3_ACCNUMDOCS);
        UC64_DECODECONTEXT(o);
        uint32_t length;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, _spd._);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L3_WORDNUM, EC);
        UC64_DECODECONTEXT_STORE(o, _spd._);
        --l3Entries;
    }
    uint32_t pageOffset = _spd.getReadOffset() & (getPageBitSize() - 1);
    uint32_t padding = getPageBitSize() - wordsSize * 8 - pageOffset;
    _spd.skipBits(padding);
    _spwords.resize(wordsSize);
    _spd.readBytes(reinterpret_cast<uint8_t *>(&_spwords[0]), wordsSize);
    _spwc = _spwords.begin();
    _spwe = _spwords.end();
}


void
PageDict4Reader::decodePWord(vespalib::string &word)
{
    assert(_wc != _we);
    size_t lcp = static_cast<unsigned char>(*_wc);
    ++_wc;
    assert(lcp <= _lastWord.size());
    assert(_wc != _we);
    word = _lastWord.substr(0, lcp);
    while (*_wc != 0) {
        word += *_wc;
        assert(_wc != _we);
        ++_wc;
    }
    assert(_wc != _we);
    ++_wc;
}


void
PageDict4Reader::decodeSPWord(vespalib::string &word)
{
    assert(_spwc != _spwe);
    size_t lcp = static_cast<unsigned char>(*_spwc);
    ++_spwc;
    assert(lcp <= _lastWord.size());
    assert(_spwc != _spwe);
    word = _lastWord.substr(0, lcp);
    while (*_spwc != 0) {
        word += *_spwc;
        assert(_spwc != _spwe);
        ++_spwc;
    }
    assert(_spwc != _spwe);
    ++_spwc;
}


void
PageDict4Reader::decodeSSWord(vespalib::string &word)
{
    uint64_t l6Offset = _ssd.getReadOffset();

    while (l6Offset < _ssReader._ssFileBitLen) {
        UC64_DECODECONTEXT(o);
        uint32_t length;
        const bool bigEndian = true;
        UC64_DECODECONTEXT_LOAD(o, _ssd._);
        bool overflow = ((oVal & TOP_BIT64) != 0);
        oVal <<= 1;
        length = 1;
        UC64_READBITS_NS(o, EC);
        UC64_DECODECONTEXT_STORE(o, _ssd._);

        StartOffset startOffset;
        readStartOffset(_ssd,
                        startOffset,
                        K_VALUE_COUNTFILE_L6_FILEOFFSET,
                        K_VALUE_COUNTFILE_L6_ACCNUMDOCS);
        UC64_DECODECONTEXT_LOAD(o, _ssd._);
        UC64_SKIPEXPGOLOMB_NS(o, K_VALUE_COUNTFILE_L6_WORDNUM, EC);
        UC64_DECODECONTEXT_STORE(o, _ssd._);

        _ssd.smallAlign(8);
        const uint8_t *bytes = _ssd.getByteCompr();
        size_t lcp = *bytes;
        ++bytes;
        assert(lcp <= _lastSSWord.size());
        word = _lastSSWord.substr(0, lcp);
        word += reinterpret_cast<const char *>(bytes);
        _ssd.setByteCompr(bytes + word.size() + 1 - lcp);
        _lastSSWord = word;
#if 0
        LOG(info,
            "word is %s LCP %d, overflow=%s",
            word.c_str(),
            (int) lcp,
            overflow ? "true" : "false");
#endif
        if (overflow) {
            Counts counts;
            _ssd.readCounts(counts);
        } else {
            UC64_DECODECONTEXT_LOAD(o, _ssd._);
            UC64_SKIPEXPGOLOMB_NS(o,
                                  K_VALUE_COUNTFILE_L6_PAGENUM,
                                  EC);
            UC64_DECODECONTEXT_STORE(o, _ssd._);
            break;
        }
        l6Offset = _ssd.getReadOffset();
    }
}

void
PageDict4Reader::readCounts(vespalib::string &word,
                            uint64_t &wordNum,
                            Counts &counts)
{
    if (_countsResidue > 0) {
        assert(_cc != _ce);
        counts = *_cc;
        ++_cc;
        if (_countsResidue > 1) {
            assert(_cc != _ce);
        } else {
            assert(_cc == _ce);
        }
        _startOffset.adjust(counts);
        if (_countsResidue > 1) {
            decodePWord(word);
            _lastWord = word;
            if (_countsResidue == 2) {
                assert(_wc == _we);
            } else {
                assert(_wc != _we);
            }
        } else {
            assert(_l3Residue > 0);
            if (_l3Residue > 1)
                decodeSPWord(word);
            else
                decodeSSWord(word);
            _lastWord = word;
            --_l3Residue;
        }
        --_countsResidue;
        if (_countsResidue == 0) {
            assert((_pd.getReadOffset() & (getPageBitSize() - 1)) == 0);
            if (_pd.getReadOffset() < _pFileBitLen) {
                setupPage();
                if (_l3Residue == 0)
                    setupSPage();
            } else {
                assert(_pd.getReadOffset() == _pFileBitLen);
            }
        }
        wordNum = _wordNum++;
    } else if (_overflowPage) {
        readOverflowCounts(word, counts);
        _overflowPage = false;
        assert(_l3Residue > 0);
        vespalib::string tword;
        if (_l3Residue > 1)
            decodeSPWord(tword);
        else
            decodeSSWord(tword);
        assert(tword == word);
        --_l3Residue;
        _lastWord = word;
        _pd.align(getPageBitSize());
        if (_pd.getReadOffset() < _pFileBitLen) {
            setupPage();
            if (_l3Residue == 0)
                setupSPage();
        } else {
            assert(_pd.getReadOffset() == _pFileBitLen);
        }
        wordNum = _wordNum++;
    } else {
        // Mark end of file.
        word.clear();
        counts.clear();
        wordNum = search::index::DictionaryFileSeqRead::noWordNumHigh();
    }
}


void
PageDict4Reader::readOverflowCounts(vespalib::string &word,
                                    Counts &counts)
{
    uint64_t wordNum = _pd.readBits(64);

    PageDict4SSLookupRes wtsslr;
    wtsslr = _ssReader.lookupOverflow(wordNum);
    assert(wtsslr._overflow);
    assert(wtsslr._res);

    word = wtsslr._lastWord;
    counts = wtsslr._counts;

#if 0
    std::ostringstream txtCounts;
    std::ostringstream txtStartOffset;
    std::ostringstream txtLRStartOffset;

    txtCounts << counts;
    txtStartOffset << _startOffset;
    txtLRStartOffset << wtsslr._startOffset;
    LOG(info,
        "readOverflowCounts _wordNum=%" PRIu64
        ", counts=%s, startOffset=%s (should be %s)",
        _wordNum,
        txtCounts.str().c_str(),
        txtLRStartOffset.str().c_str(),
        txtStartOffset.str().c_str());
#endif

    assert(wtsslr._startOffset == _startOffset);
    _startOffset.adjust(counts);
}

} // namespace bitcompression

} // namespace search

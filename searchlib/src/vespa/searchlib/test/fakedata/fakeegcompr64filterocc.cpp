// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fakeegcompr64filterocc.h"
#include "bitencode64.h"
#include "bitdecode64.h"
#include "fpfactory.h"
#include <vespa/searchlib/queryeval/iterators.h>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.test.fake_eg_compr64_filter_occ");

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;

namespace search::fakedata {

#define DEBUG_EGCOMPR64FILTEROCC_PRINTF 0
#define DEBUG_EGCOMPR64FILTEROCC_ASSERT 1

static FPFactoryInit
init(std::make_pair("EGCompr64FilterOcc",
                    makeFPFactory<FPFactoryT<FakeEGCompr64FilterOcc> >));

#define K_VALUE_FILTEROCC_RESIDUE 8

#define K_VALUE_FILTEROCC_FIRST_DOCID 22

#define K_VALUE_FILTEROCC_DELTA_DOCID 7

#define K_VALUE_FILTEROCC_L1SKIPDELTA_DOCID 13

#define K_VALUE_FILTEROCC_L1SKIPDELTA_BITPOS 10

#define K_VALUE_FILTEROCC_L2SKIPDELTA_DOCID 15

#define K_VALUE_FILTEROCC_L2SKIPDELTA_BITPOS 12

#define K_VALUE_FILTEROCC_L2SKIPDELTA_L1SKIPBITPOS 10

#define K_VALUE_FILTEROCC_L3SKIPDELTA_DOCID 18

#define K_VALUE_FILTEROCC_L3SKIPDELTA_BITPOS 15

#define K_VALUE_FILTEROCC_L3SKIPDELTA_L1SKIPBITPOS 13

#define K_VALUE_FILTEROCC_L3SKIPDELTA_L2SKIPBITPOS 10

#define K_VALUE_FILTEROCC_L4SKIPDELTA_DOCID 21

#define K_VALUE_FILTEROCC_L4SKIPDELTA_BITPOS 18

#define K_VALUE_FILTEROCC_L4SKIPDELTA_L1SKIPBITPOS 16

#define K_VALUE_FILTEROCC_L4SKIPDELTA_L2SKIPBITPOS 13

#define K_VALUE_FILTEROCC_L4SKIPDELTA_L3SKIPBITPOS 10

#define L1SKIPSTRIDE 16
#define L2SKIPSTRIDE 8
#define L3SKIPSTRIDE 8
#define L4SKIPSTRIDE 8

FakeEGCompr64FilterOcc::FakeEGCompr64FilterOcc(const FakeWord &fw)
    : FakePosting(fw.getName() + ".egc64filterocc"),
      _compressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _l1SkipCompressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _l2SkipCompressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _l3SkipCompressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _l4SkipCompressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _compressedAlloc(),
      _l1SkipCompressedAlloc(),
      _l2SkipCompressedAlloc(),
      _l3SkipCompressedAlloc(),
      _l4SkipCompressedAlloc(),
      _docIdLimit(0),
      _hitDocs(0),
      _lastDocId(0u),
      _bitSize(0),
      _l1SkipBitSize(0),
      _l2SkipBitSize(0),
      _l3SkipBitSize(0),
      _l4SkipBitSize(0),
      _bigEndian(true)
{
    setup(fw);
}


FakeEGCompr64FilterOcc::FakeEGCompr64FilterOcc(const FakeWord &fw,
        bool bigEndian,
        const char *nameSuffix)
    : FakePosting(fw.getName() + nameSuffix),
      _compressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _l1SkipCompressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _l2SkipCompressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _l3SkipCompressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _l4SkipCompressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _compressedAlloc(),
      _l1SkipCompressedAlloc(),
      _l2SkipCompressedAlloc(),
      _l3SkipCompressedAlloc(),
      _l4SkipCompressedAlloc(),
      _docIdLimit(0),
      _hitDocs(0),
      _lastDocId(0u),
      _bitSize(0),
      _l1SkipBitSize(0),
      _l2SkipBitSize(0),
      _l3SkipBitSize(0),
      _l4SkipBitSize(0),
      _bigEndian(bigEndian)
{
    setup(fw);
}


void
FakeEGCompr64FilterOcc::setup(const FakeWord &fw)
{
    if (_bigEndian)
        setupT<true>(fw);
    else
        setupT<false>(fw);
}


template <bool bigEndian>
void
FakeEGCompr64FilterOcc::
setupT(const FakeWord &fw)
{
    BitEncode64<bigEndian> bits;
    BitEncode64<bigEndian> l1SkipBits;
    BitEncode64<bigEndian> l2SkipBits;
    BitEncode64<bigEndian> l3SkipBits;
    BitEncode64<bigEndian> l4SkipBits;
    uint32_t lastDocId = 0u;
    uint32_t lastL1SkipDocId = 0u;
    uint64_t lastL1SkipDocIdPos = 0;
    uint32_t l1SkipCnt = 0;
    uint32_t lastL2SkipDocId = 0u;
    uint64_t lastL2SkipDocIdPos = 0;
    uint64_t lastL2SkipL1SkipPos = 0;
    unsigned int l2SkipCnt = 0;
    uint32_t lastL3SkipDocId = 0u;
    uint64_t lastL3SkipDocIdPos = 0;
    uint64_t lastL3SkipL1SkipPos = 0;
    uint64_t lastL3SkipL2SkipPos = 0;
    unsigned int l3SkipCnt = 0;
    uint32_t lastL4SkipDocId = 0u;
    uint64_t lastL4SkipDocIdPos = 0;
    uint64_t lastL4SkipL1SkipPos = 0;
    uint64_t lastL4SkipL2SkipPos = 0;
    uint64_t lastL4SkipL3SkipPos = 0;
    unsigned int l4SkipCnt = 0;

    auto d = fw._postings.begin();
    auto de = fw._postings.end();

    if (d != de) {
        // Prefix support needs counts embedded in posting list
        // if selector bits are dropped.
        bits.encodeExpGolomb(fw._postings.size(),
                              K_VALUE_FILTEROCC_RESIDUE);
        bits.writeComprBufferIfNeeded();
        lastL1SkipDocIdPos = bits.getWriteOffset();
        lastL2SkipDocIdPos = bits.getWriteOffset();
        lastL3SkipDocIdPos = bits.getWriteOffset();
        lastL4SkipDocIdPos = bits.getWriteOffset();
    }
    while (d != de) {
        if (l1SkipCnt >= L1SKIPSTRIDE) {
            uint32_t docIdDelta = lastDocId - lastL1SkipDocId;
            assert(static_cast<int32_t>(docIdDelta) > 0);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
            uint64_t prevL1SkipPos = l1SkipBits.getWriteOffset();
#endif
            l1SkipBits.encodeExpGolomb(docIdDelta - 1,
                                     K_VALUE_FILTEROCC_L1SKIPDELTA_DOCID);
            uint64_t lastDocIdPos = bits.getWriteOffset();
            uint32_t docIdPosDelta = lastDocIdPos - lastL1SkipDocIdPos;
            l1SkipBits.encodeExpGolomb(docIdPosDelta - 1,
                                     K_VALUE_FILTEROCC_L1SKIPDELTA_BITPOS);
            l1SkipBits.writeComprBufferIfNeeded();
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
            printf("L1Encode docId=%d (+%u), docIdPos=%d (+%u), "
                   "L1SkipPos=%d -> %d\n",
                   lastDocId,
                   docIdDelta,
                   (int) lastDocIdPos,
                   docIdPosDelta,
                   (int) prevL1SkipPos,
                   (int) l1SkipBits.getWriteOffset());
#endif
            lastL1SkipDocId = lastDocId;
            lastL1SkipDocIdPos = lastDocIdPos;
            l1SkipCnt = 0;
            ++l2SkipCnt;
            if (l2SkipCnt >= L2SKIPSTRIDE) {
                docIdDelta = lastDocId - lastL2SkipDocId;
                docIdPosDelta = lastDocIdPos - lastL2SkipDocIdPos;
                uint64_t lastL1SkipPos = l1SkipBits.getWriteOffset();
                uint32_t l1SkipPosDelta = lastL1SkipPos - lastL2SkipL1SkipPos;
                l2SkipBits.encodeExpGolomb(docIdDelta - 1,
                        K_VALUE_FILTEROCC_L2SKIPDELTA_DOCID);
                l2SkipBits.encodeExpGolomb(docIdPosDelta - 1,
                        K_VALUE_FILTEROCC_L2SKIPDELTA_BITPOS);
                l2SkipBits.encodeExpGolomb(l1SkipPosDelta - 1,
                        K_VALUE_FILTEROCC_L2SKIPDELTA_L1SKIPBITPOS);
                l2SkipBits.writeComprBufferIfNeeded();
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
                printf("L2Encode docId=%d (+%u), docIdPos=%d (+%u), "
                       "L1SkipPos=%d (+%u)\n",
                       lastDocId,
                       docIdDelta,
                       (int) lastDocIdPos,
                       docIdPosDelta,
                       (int) lastL1SkipPos,
                       l1SkipPosDelta);
#endif
                lastL2SkipDocId = lastDocId;
                lastL2SkipDocIdPos = lastDocIdPos;
                lastL2SkipL1SkipPos = lastL1SkipPos;
                l2SkipCnt = 0;
                ++l3SkipCnt;
                if (l3SkipCnt >= L3SKIPSTRIDE) {
                    docIdDelta = lastDocId - lastL3SkipDocId;
                    docIdPosDelta = lastDocIdPos - lastL3SkipDocIdPos;
                    l1SkipPosDelta = lastL1SkipPos - lastL3SkipL1SkipPos;
                    uint64_t lastL2SkipPos = l2SkipBits.getWriteOffset();
                    uint32_t l2SkipPosDelta = lastL2SkipPos -
                                              lastL3SkipL2SkipPos;
                    l3SkipBits.encodeExpGolomb(docIdDelta - 1,
                            K_VALUE_FILTEROCC_L3SKIPDELTA_DOCID);
                    l3SkipBits.encodeExpGolomb(docIdPosDelta - 1,
                            K_VALUE_FILTEROCC_L3SKIPDELTA_BITPOS);
                    l3SkipBits.writeComprBufferIfNeeded();
                    l3SkipBits.encodeExpGolomb(l1SkipPosDelta - 1,
                            K_VALUE_FILTEROCC_L3SKIPDELTA_L1SKIPBITPOS);
                    l3SkipBits.encodeExpGolomb(l2SkipPosDelta - 1,
                            K_VALUE_FILTEROCC_L3SKIPDELTA_L2SKIPBITPOS);
                    l3SkipBits.writeComprBufferIfNeeded();
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
                    printf("L3Encode docId=%d (+%u), docIdPos=%d (+%u), "
                           "L1SkipPos=%d (+%u) L2SkipPos=%d (+%u)\n",
                           lastDocId,
                           docIdDelta,
                           (int) lastDocIdPos,
                           docIdPosDelta,
                           (int) lastL1SkipPos,
                           l1SkipPosDelta,
                           (int) lastL2SkipPos,
                           l2SkipPosDelta);
#endif
                    lastL3SkipDocId = lastDocId;
                    lastL3SkipDocIdPos = lastDocIdPos;
                    lastL3SkipL1SkipPos = lastL1SkipPos;
                    lastL3SkipL2SkipPos = lastL2SkipPos;
                    l3SkipCnt = 0;
                    ++l4SkipCnt;
                    if (l4SkipCnt >= L4SKIPSTRIDE) {
                        docIdDelta = lastDocId - lastL4SkipDocId;
                        docIdPosDelta = lastDocIdPos - lastL4SkipDocIdPos;
                        l1SkipPosDelta = lastL1SkipPos - lastL4SkipL1SkipPos;
                        l2SkipPosDelta = lastL2SkipPos - lastL4SkipL2SkipPos;
                        uint64_t lastL3SkipPos = l3SkipBits.getWriteOffset();
                        uint32_t l3SkipPosDelta = lastL3SkipPos -
                                                  lastL4SkipL3SkipPos;
                        l4SkipBits.encodeExpGolomb(docIdDelta - 1,
                                K_VALUE_FILTEROCC_L4SKIPDELTA_DOCID);
                        l4SkipBits.encodeExpGolomb(docIdPosDelta - 1,
                                K_VALUE_FILTEROCC_L4SKIPDELTA_BITPOS);
                        l4SkipBits.writeComprBufferIfNeeded();
                        l4SkipBits.encodeExpGolomb(l1SkipPosDelta - 1,
                                K_VALUE_FILTEROCC_L4SKIPDELTA_L1SKIPBITPOS);
                        l4SkipBits.encodeExpGolomb(l2SkipPosDelta - 1,
                                K_VALUE_FILTEROCC_L4SKIPDELTA_L2SKIPBITPOS);
                        l4SkipBits.encodeExpGolomb(l3SkipPosDelta - 1,
                                K_VALUE_FILTEROCC_L4SKIPDELTA_L3SKIPBITPOS);
                        l4SkipBits.writeComprBufferIfNeeded();
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
                        printf("L4Encode docId=%d (+%u), docIdPos=%d (+%u), "
                               "L1SkipPos=%d (+%u) L2SkipPos=%d (+%u)"
                               "L3SkipPos=%d (+%u)\n",
                               lastDocId,
                               docIdDelta,
                               (int) lastDocIdPos,
                               docIdPosDelta,
                               (int) lastL1SkipPos,
                               l1SkipPosDelta,
                               (int) lastL2SkipPos,
                               l2SkipPosDelta,
                               (int) lastL3SkipPos,
                               l3SkipPosDelta);
#endif
                        lastL4SkipDocId = lastDocId;
                        lastL4SkipDocIdPos = lastDocIdPos;
                        lastL4SkipL1SkipPos = lastL1SkipPos;
                        lastL4SkipL2SkipPos = lastL2SkipPos;
                        lastL4SkipL3SkipPos = lastL3SkipPos;
                        l4SkipCnt = 0;
                    }
                }
            }
        }
        if (lastDocId == 0u) {
            bits.encodeExpGolomb(d->_docId - 1,
                                 K_VALUE_FILTEROCC_FIRST_DOCID);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
            printf("Encode docId=%d\n", d->_docId);
#endif
        } else {
            uint32_t docIdDelta = d->_docId - lastDocId;
            bits.encodeExpGolomb(docIdDelta - 1,
                                 K_VALUE_FILTEROCC_DELTA_DOCID);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
            printf("Encode docId=%d (+%u)\n",
                   d->_docId,
                   docIdDelta);
#endif
        }
        bits.writeComprBufferIfNeeded();
        lastDocId = d->_docId;
        ++l1SkipCnt;
        ++d;
    }
    // Extra partial entries for skip tables to simplify iterator during search
    uint32_t docIdDelta = lastDocId - lastL1SkipDocId;
    assert(static_cast<int32_t>(docIdDelta) > 0);
    l1SkipBits.encodeExpGolomb(docIdDelta - 1,
                               K_VALUE_FILTEROCC_L1SKIPDELTA_DOCID);
    docIdDelta = lastDocId - lastL2SkipDocId;
    assert(static_cast<int32_t>(docIdDelta) > 0);
    l2SkipBits.encodeExpGolomb(docIdDelta - 1,
                               K_VALUE_FILTEROCC_L2SKIPDELTA_DOCID);
    docIdDelta = lastDocId - lastL3SkipDocId;
    assert(static_cast<int32_t>(docIdDelta) > 0);
    l3SkipBits.encodeExpGolomb(docIdDelta - 1,
                               K_VALUE_FILTEROCC_L3SKIPDELTA_DOCID);
    docIdDelta = lastDocId - lastL4SkipDocId;
    assert(static_cast<int32_t>(docIdDelta) > 0);
    l4SkipBits.encodeExpGolomb(docIdDelta - 1,
                               K_VALUE_FILTEROCC_L4SKIPDELTA_DOCID);
    _hitDocs = fw._postings.size();
    _bitSize = bits.getWriteOffset();
    _l1SkipBitSize = l1SkipBits.getWriteOffset();
    _l2SkipBitSize = l2SkipBits.getWriteOffset();
    _l3SkipBitSize = l3SkipBits.getWriteOffset();
    _l4SkipBitSize = l4SkipBits.getWriteOffset();
    bits.writeComprBufferIfNeeded();
    bits.writeBits(static_cast<uint64_t>(-1), 64);
    bits.writeBits(static_cast<uint64_t>(-1), 64);
    bits.writeComprBufferIfNeeded();
    bits.writeBits(static_cast<uint64_t>(-1), 64);
    bits.writeBits(static_cast<uint64_t>(-1), 64);
    bits.flush();
    bits.writeComprBuffer();
    l1SkipBits.writeComprBufferIfNeeded();
    l1SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l1SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l1SkipBits.writeComprBufferIfNeeded();
    l1SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l1SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l1SkipBits.flush();
    l1SkipBits.writeComprBuffer();
    l2SkipBits.writeComprBufferIfNeeded();
    l2SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l2SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l2SkipBits.writeComprBufferIfNeeded();
    l2SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l2SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l2SkipBits.flush();
    l2SkipBits.writeComprBuffer();
    l3SkipBits.writeComprBufferIfNeeded();
    l3SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l3SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l3SkipBits.writeComprBufferIfNeeded();
    l3SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l3SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l3SkipBits.flush();
    l3SkipBits.writeComprBuffer();
    l4SkipBits.writeComprBufferIfNeeded();
    l4SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l4SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l4SkipBits.writeComprBufferIfNeeded();
    l4SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l4SkipBits.writeBits(static_cast<uint64_t>(-1), 64);
    l4SkipBits.flush();
    l4SkipBits.writeComprBuffer();
    _compressed = bits.grabComprBuffer(_compressedAlloc);
    _l1SkipCompressed = l1SkipBits.grabComprBuffer(_l1SkipCompressedAlloc);
    _l2SkipCompressed = l2SkipBits.grabComprBuffer(_l2SkipCompressedAlloc);
    _l3SkipCompressed = l3SkipBits.grabComprBuffer(_l3SkipCompressedAlloc);
    _l4SkipCompressed = l4SkipBits.grabComprBuffer(_l4SkipCompressedAlloc);
    _docIdLimit = fw._docIdLimit;
    _lastDocId = lastDocId;
}


FakeEGCompr64FilterOcc::~FakeEGCompr64FilterOcc() = default;


void
FakeEGCompr64FilterOcc::forceLink()
{
}


size_t
FakeEGCompr64FilterOcc::bitSize() const
{
    return _bitSize;
}


bool
FakeEGCompr64FilterOcc::hasWordPositions() const
{
    return false;
}


size_t
FakeEGCompr64FilterOcc::skipBitSize() const
{
    return _l1SkipBitSize + _l2SkipBitSize + _l3SkipBitSize + _l4SkipBitSize;
}


size_t
FakeEGCompr64FilterOcc::l1SkipBitSize() const
{
    return _l1SkipBitSize;
}


size_t
FakeEGCompr64FilterOcc::l2SkipBitSize() const
{
    return _l2SkipBitSize;
}


size_t
FakeEGCompr64FilterOcc::l3SkipBitSize() const
{
    return _l3SkipBitSize;
}


size_t
FakeEGCompr64FilterOcc::l4SkipBitSize() const
{
    return _l4SkipBitSize;
}


int
FakeEGCompr64FilterOcc::lowLevelSinglePostingScan() const
{
    return 0;
}


int
FakeEGCompr64FilterOcc::lowLevelSinglePostingScanUnpack() const
{
    return 0;
}


int
FakeEGCompr64FilterOcc::
lowLevelAndPairPostingScan(const FakePosting &rhs) const
{
    (void) rhs;
    return 0;
}


int
FakeEGCompr64FilterOcc::
lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const
{
    (void) rhs;
    return 0;
}

#define UC64_FILTEROCC_READ_RESIDUE(val, valI, preRead, cacheInt, \
                    residue, EC)                                  \
  do {                                                            \
    UC64_DECODEEXPGOLOMB(val, valI, preRead, cacheInt,            \
             K_VALUE_FILTEROCC_RESIDUE, EC);                      \
    residue = val64;                                              \
  } while (0)


#define UC64_FILTEROCC_READ_FIRST_DOC(val, valI, preRead, cacheInt, \
                      docId, EC)                                    \
  do {                                                              \
    UC64_DECODEEXPGOLOMB(val, valI, preRead, cacheInt,              \
             K_VALUE_FILTEROCC_FIRST_DOCID, EC);                    \
    docId = val64 + 1;                                              \
  } while (0)


#define UC64_FILTEROCC_READ_NEXT_DOC(val, valI, preRead, cacheInt, \
                     docId, EC)                                    \
  do {                                                             \
    UC64_DECODEEXPGOLOMB_SMALL(val, valI, preRead, cacheInt,       \
                   K_VALUE_FILTEROCC_DELTA_DOCID, EC);             \
    docId += val64 + 1;                                            \
  } while (0)


#define UC64_FILTEROCC_READ_NEXT_DOC_NS(prefix, EC)              \
  do {                                                           \
    UC64_FILTEROCC_READ_NEXT_DOC(prefix ## Val, prefix ## Compr, \
                 prefix ## PreRead,                              \
                 prefix ## CacheInt,                             \
                 prefix ## DocId, EC);                           \
  } while (0)


#define UC64_FILTEROCC_DECODECONTEXT \
  uint64_t val64;                    \
  unsigned int length;


class BitDecode64BEDocIds : public BitDecode64BE
{
public:
    BitDecode64BEDocIds(const uint64_t *compr,
                        int bitOffset)
        : BitDecode64BE(compr, bitOffset)
    {
    }

    uint32_t
    getDocIdDelta()
    {
        uint32_t ret;
        unsigned int length;
        const bool bigEndian = true;
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(_val, _valI, _preRead, _cacheInt,
                K_VALUE_FILTEROCC_DELTA_DOCID, EC,
                ret = 1 +);
        return ret;
    }

    uint32_t
    getL1SkipDocIdDelta()
    {
        uint32_t ret;
        unsigned int length;
        const bool bigEndian = true;
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(_val, _valI, _preRead, _cacheInt,
                K_VALUE_FILTEROCC_L1SKIPDELTA_DOCID, EC,
                ret = 1 +);
        return ret;
    }

    uint32_t
    getL2SkipDocIdDelta()
    {
        uint32_t ret;
        unsigned int length;
        const bool bigEndian = true;
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(_val, _valI, _preRead, _cacheInt,
                K_VALUE_FILTEROCC_L2SKIPDELTA_DOCID, EC,
                ret = 1 +);
        return ret;
    }

    uint32_t
    getL3SkipDocIdDelta()
    {
        uint32_t ret;
        unsigned int length;
        UC64BE_DECODEEXPGOLOMB_SMALL_APPLY(_val, _valI, _preRead, _cacheInt,
                K_VALUE_FILTEROCC_L3SKIPDELTA_DOCID, EC,
                ret = 1 +);
        return ret;
    }
};

template <bool bigEndian>
class FakeFilterOccEGCompressed64ArrayIterator
    : public queryeval::RankedSearchIteratorBase
{
private:

    FakeFilterOccEGCompressed64ArrayIterator(const FakeFilterOccEGCompressed64ArrayIterator &other);

    FakeFilterOccEGCompressed64ArrayIterator&
    operator=(const FakeFilterOccEGCompressed64ArrayIterator &other);

    using EC = BitEncode64<bigEndian>;
    using DC = BitDecode64<bigEndian>;

public:
    DC       _docIdBits;
    uint32_t _residue;
    uint32_t _lastDocId;

    FakeFilterOccEGCompressed64ArrayIterator(const uint64_t *compressedOccurrences,
                                             int compressedBitOffset,
                                             uint32_t residue,
                                             uint32_t lastDocId,
                                             const search::fef::TermFieldMatchDataArray &matchData);

    ~FakeFilterOccEGCompressed64ArrayIterator() override;

    void doUnpack(uint32_t docId) override;
    void doSeek(uint32_t docId) override;
    void initRange(uint32_t begin, uint32_t end) override;
    Trinary is_strict() const override { return Trinary::True; }
};


template <bool bigEndian>
FakeFilterOccEGCompressed64ArrayIterator<bigEndian>::
FakeFilterOccEGCompressed64ArrayIterator(const uint64_t *compressedOccurrences,
                                         int compressedBitOffset,
                                         uint32_t residue,
                                         uint32_t lastDocId,
                                         const search::fef::TermFieldMatchDataArray &matchData)
    : queryeval::RankedSearchIteratorBase(matchData),
      _docIdBits(compressedOccurrences, compressedBitOffset),
      _residue(residue),
      _lastDocId(lastDocId)
{
    clearUnpacked();
}

template <bool bigEndian>
void
FakeFilterOccEGCompressed64ArrayIterator<bigEndian>::
initRange(uint32_t begin, uint32_t end)
{
    queryeval::RankedSearchIteratorBase::initRange(begin, end);
    UC64_FILTEROCC_DECODECONTEXT;
    uint32_t docId = 0;
    uint32_t myResidue = 0;
    UC64_FILTEROCC_READ_RESIDUE(_docIdBits._val,
                                _docIdBits._valI,
                                _docIdBits._preRead,
                                _docIdBits._cacheInt, myResidue, EC);
    assert(myResidue == _residue);
    (void) myResidue;
    if (_residue > 0) {
        UC64_FILTEROCC_READ_FIRST_DOC(_docIdBits._val,
                                      _docIdBits._valI,
                                      _docIdBits._preRead,
                                      _docIdBits._cacheInt, docId, EC);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("DecodeInit docId=%d\n",
               docId);
#endif
        setDocId(docId);
    } else {
        setAtEnd();
    }
}


template <bool bigEndian>
FakeFilterOccEGCompressed64ArrayIterator<bigEndian>::
~FakeFilterOccEGCompressed64ArrayIterator()
{
}


template <bool bigEndian>
void
FakeFilterOccEGCompressed64ArrayIterator<bigEndian>::doSeek(uint32_t docId)
{
    unsigned int length;
    uint32_t oDocId = getDocId();
    UC64_DECODECONTEXT_CONSTRUCTOR(o, this->_docIdBits._);

    if (getUnpacked())
        clearUnpacked();
    while (__builtin_expect(oDocId < docId, true)) {
        if (__builtin_expect(--_residue == 0, false))
            goto atbreak;
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(oVal, oCompr,
                oPreRead, oCacheInt,
                K_VALUE_FILTEROCC_DELTA_DOCID, EC,
                oDocId += 1 +);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("Decode docId=%d\n",
               oDocId);
#endif
    }
    UC64_DECODECONTEXT_STORE(o, this->_docIdBits._);
    setDocId(oDocId);
    return;
 atbreak:
    UC64_DECODECONTEXT_STORE(o, this->_docIdBits._);
    setAtEnd();                // Mark end of data
    return;
}


template <bool bigEndian>
void
FakeFilterOccEGCompressed64ArrayIterator<bigEndian>::doUnpack(uint32_t docId)
{
    if (_matchData.size() != 1) {
        return;
    }
    _matchData[0]->clear_hidden_from_ranking();
    if (getUnpacked()) {
        return;
    }
    assert(docId == getDocId());
    _matchData[0]->reset(docId);
    setUnpacked();
}


std::unique_ptr<search::queryeval::SearchIterator>
FakeEGCompr64FilterOcc::
createIterator(const fef::TermFieldMatchDataArray &matchData) const
{
    const uint64_t *arr = _compressed.first;
    if (_bigEndian) {
        return std::make_unique<FakeFilterOccEGCompressed64ArrayIterator<true>>(arr, 0, _hitDocs, _lastDocId, matchData);
    } else {
        return std::make_unique<FakeFilterOccEGCompressed64ArrayIterator<false>>(arr, 0, _hitDocs, _lastDocId, matchData);
    }
}


class FakeEGCompr64LEFilterOcc : public FakeEGCompr64FilterOcc
{
public:
    FakeEGCompr64LEFilterOcc(const FakeWord &fw);

    ~FakeEGCompr64LEFilterOcc() override;
};


FakeEGCompr64LEFilterOcc::FakeEGCompr64LEFilterOcc(const FakeWord &fw)
    : FakeEGCompr64FilterOcc(fw, false, ".egc64lefilterocc")
{
}


FakeEGCompr64LEFilterOcc::~FakeEGCompr64LEFilterOcc() = default;


static FPFactoryInit
initLE(std::make_pair("EGCompr64LEFilterOcc",
                      makeFPFactory<FPFactoryT<FakeEGCompr64LEFilterOcc> >));


template <bool doSkip>
class FakeEGCompr64SkipFilterOcc : public FakeEGCompr64FilterOcc
{
public:
    FakeEGCompr64SkipFilterOcc(const FakeWord &fw);
    ~FakeEGCompr64SkipFilterOcc() override;
    std::unique_ptr<search::queryeval::SearchIterator> createIterator(const fef::TermFieldMatchDataArray &matchData) const override;
};


static FPFactoryInit
initNoSkip(std::make_pair("EGCompr64NoSkipFilterOcc",
                          makeFPFactory<FPFactoryT<FakeEGCompr64SkipFilterOcc<false> > >));


static FPFactoryInit
initSkip(std::make_pair("EGCompr64SkipFilterOcc",
                        makeFPFactory<FPFactoryT<FakeEGCompr64SkipFilterOcc<true> > >));


template<>
FakeEGCompr64SkipFilterOcc<true>::FakeEGCompr64SkipFilterOcc(const FakeWord &fw)
    : FakeEGCompr64FilterOcc(fw, true, ".egc64skipfilterocc")
{
}


template<>
FakeEGCompr64SkipFilterOcc<false>::FakeEGCompr64SkipFilterOcc(const FakeWord &fw)
    : FakeEGCompr64FilterOcc(fw, true, ".egc64noskipfilterocc")
{
}


template <bool doSkip>
FakeEGCompr64SkipFilterOcc<doSkip>::~FakeEGCompr64SkipFilterOcc()
{
}


template <bool doSkip>
class FakeFilterOccEGCompressed64SkipArrayIterator
    : public queryeval::RankedSearchIteratorBase
{
private:

    FakeFilterOccEGCompressed64SkipArrayIterator(const FakeFilterOccEGCompressed64SkipArrayIterator &other);

    FakeFilterOccEGCompressed64SkipArrayIterator&
    operator=(const FakeFilterOccEGCompressed64SkipArrayIterator &other);

    using EC = bitcompression::EncodeContext64BE;

public:
    BitDecode64BEDocIds _docIdBits;
    uint32_t _lastDocId;
    uint32_t _l1SkipDocId;
    uint32_t _l2SkipDocId;
    uint32_t _l3SkipDocId;
    uint32_t _l4SkipDocId;
    uint64_t _l1SkipDocIdBitsOffset;
    uint64_t _l2SkipDocIdBitsOffset;
    uint64_t _l2SkipL1SkipBitsOffset;
    uint64_t _l3SkipDocIdBitsOffset;
    uint64_t _l3SkipL1SkipBitsOffset;
    uint64_t _l3SkipL2SkipBitsOffset;
    uint64_t _l4SkipDocIdBitsOffset;
    uint64_t _l4SkipL1SkipBitsOffset;
    uint64_t _l4SkipL2SkipBitsOffset;
    uint64_t _l4SkipL3SkipBitsOffset;
    BitDecode64BEDocIds _l1SkipBits;
    BitDecode64BEDocIds _l2SkipBits;
    BitDecode64BEDocIds _l3SkipBits;
    BitDecode64BE _l4SkipBits;
    std::string _name;

    FakeFilterOccEGCompressed64SkipArrayIterator(const uint64_t *compressedOccurrences,
            int compressedBitOffset,
            uint32_t lastDocId,
            const uint64_t *compressedL1SkipOccurrences,
            int compressedL1SkipBitOffset,
            const uint64_t *compressedL2SkipOccurrences,
            int compressedL2SkipBitOffset,
            const uint64_t *compressedL3SkipOccurrences,
            int compressedL3SkipBitOffset,
            const uint64_t *compressedL4SkipOccurrences,
            int compressedL4SkipBitOffset,
            const std::string &name,
            const fef::TermFieldMatchDataArray &matchData);

    ~FakeFilterOccEGCompressed64SkipArrayIterator() override;

    void doL4SkipSeek(uint32_t docid);
    void doL3SkipSeek(uint32_t docid); 
    void doL2SkipSeek(uint32_t docid); 
    void doL1SkipSeek(uint32_t docId);

    void doUnpack(uint32_t docId) override;
    void doSeek(uint32_t docId) override;
    void initRange(uint32_t begin, uint32_t end) override;
    Trinary is_strict() const override { return Trinary::True; }
};


template <bool doSkip>
FakeFilterOccEGCompressed64SkipArrayIterator<doSkip>::
FakeFilterOccEGCompressed64SkipArrayIterator(const uint64_t *compressedOccurrences,
        int compressedBitOffset,
        uint32_t lastDocId,
        const uint64_t *compressedL1SkipOccurrences,
        int compressedL1SkipBitOffset,
        const uint64_t *compressedL2SkipOccurrences,
        int compressedL2SkipBitOffset,
        const uint64_t *compressedL3SkipOccurrences,
        int compressedL3SkipBitOffset,
        const uint64_t *compressedL4SkipOccurrences,
        int compressedL4SkipBitOffset,
        const std::string &name,
        const fef::TermFieldMatchDataArray &matchData)
    : queryeval::RankedSearchIteratorBase(matchData),
      _docIdBits(compressedOccurrences, compressedBitOffset),
      _lastDocId(lastDocId),
      _l1SkipDocId(0),
      _l2SkipDocId(0),
      _l3SkipDocId(0),
      _l4SkipDocId(0),
      _l1SkipDocIdBitsOffset(0),
      _l2SkipDocIdBitsOffset(0),
      _l2SkipL1SkipBitsOffset(0),
      _l3SkipDocIdBitsOffset(0),
      _l3SkipL1SkipBitsOffset(0),
      _l3SkipL2SkipBitsOffset(0),
      _l4SkipDocIdBitsOffset(0),
      _l4SkipL1SkipBitsOffset(0),
      _l4SkipL2SkipBitsOffset(0),
      _l4SkipL3SkipBitsOffset(0),
      _l1SkipBits(compressedL1SkipOccurrences, compressedL1SkipBitOffset),
      _l2SkipBits(compressedL2SkipOccurrences, compressedL2SkipBitOffset),
      _l3SkipBits(compressedL3SkipOccurrences, compressedL3SkipBitOffset),
      _l4SkipBits(compressedL4SkipOccurrences, compressedL4SkipBitOffset),
      _name(name)
{
    clearUnpacked();
}

template <bool doSkip>
void
FakeFilterOccEGCompressed64SkipArrayIterator<doSkip>::
initRange(uint32_t begin, uint32_t end)
{
    queryeval::RankedSearchIteratorBase::initRange(begin, end);

    const bool bigEndian = true;
    UC64_FILTEROCC_DECODECONTEXT;
    assert(_docIdBits.getOffset() == 0);
    uint32_t docId = 0;
    if (_lastDocId > 0) {
        UC64_FILTEROCC_READ_FIRST_DOC(_docIdBits._val,
                                      _docIdBits._valI,
                                      _docIdBits._preRead,
                                      _docIdBits._cacheInt, docId, EC);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("DecodeInit docId=%d\n",
               docId);
#endif
        UC64_DECODECONTEXT_CONSTRUCTOR(s, _l1SkipBits._);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L1SKIPDELTA_DOCID, EC,
                _l1SkipDocId = 1 +);
        UC64_DECODECONTEXT_STORE(s, _l1SkipBits._);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("L1DecodeInit docId=%d, docIdPos=%d, L1SkipPos=%d\n",
               _l1SkipDocId,
               (int) _l1SkipDocIdBitsOffset,
               (int) _l1SkipBits.getOffset());
#endif
        UC64_DECODECONTEXT_LOAD(s, _l2SkipBits._);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L2SKIPDELTA_DOCID, EC,
                _l2SkipDocId = 1 +);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("L2DecodeInit docId=%d, docIdPos=%d, L1SkipPos=%d\n",
               _l2SkipDocId,
               (int) _l2SkipDocIdBitsOffset,
               (int) _l2SkipL1SkipBitsOffset);
#endif
        UC64_DECODECONTEXT_STORE(s, _l2SkipBits._);
        UC64_DECODECONTEXT_LOAD(s, _l3SkipBits._);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L3SKIPDELTA_DOCID, EC,
                _l3SkipDocId = 1 +);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("L3DecodeInit docId=%d, docIdPos=%d, L1SkipPos=%d\n",
               _l3SkipDocId,
               (int) _l3SkipDocIdBitsOffset,
               (int) _l3SkipL1SkipBitsOffset);
#endif
        UC64_DECODECONTEXT_STORE(s, _l3SkipBits._);
        UC64_DECODECONTEXT_LOAD(s, _l4SkipBits._);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L4SKIPDELTA_DOCID, EC,
                _l4SkipDocId = 1 +);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("L4DecodeInit docId=%d, docIdPos=%d, L1SkipPos=%d\n",
               _l4SkipDocId,
               (int) _l4SkipDocIdBitsOffset,
               (int) _l4SkipL1SkipBitsOffset);
#endif
        UC64_DECODECONTEXT_STORE(s, _l4SkipBits._);
        setDocId(docId);
    } else {
        setAtEnd();
        _l1SkipDocId = _l2SkipDocId = _l3SkipDocId = _l4SkipDocId = search::endDocId;
    }
}


template <bool doSkip>
FakeFilterOccEGCompressed64SkipArrayIterator<doSkip>::
~FakeFilterOccEGCompressed64SkipArrayIterator()
{
}


template<>
void
FakeFilterOccEGCompressed64SkipArrayIterator<true>::
doL4SkipSeek(uint32_t docId)
{
    unsigned int length;
    uint32_t lastL4SkipDocId;
    const bool bigEndian = true;

    if (__builtin_expect(docId > _lastDocId, false)) {
        _l1SkipDocId = _l2SkipDocId = _l3SkipDocId = _l4SkipDocId = search::endDocId;
        setAtEnd();
        return;
    }

    UC64_DECODECONTEXT_CONSTRUCTOR(s, _l4SkipBits._);
    do {
        lastL4SkipDocId = _l4SkipDocId;
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L4SKIPDELTA_BITPOS, EC,
                _l4SkipDocIdBitsOffset += 1 +);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L4SKIPDELTA_L1SKIPBITPOS, EC,
                _l4SkipL1SkipBitsOffset += 1 +);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L4SKIPDELTA_L2SKIPBITPOS, EC,
                _l4SkipL2SkipBitsOffset += 1 +);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L4SKIPDELTA_L3SKIPBITPOS, EC,
                _l4SkipL3SkipBitsOffset += 1 +);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L4SKIPDELTA_DOCID, EC,
                _l4SkipDocId += 1 +);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("L4Decode docId=%d, docIdPos=%d, l1SkipPos=%d, nextDocId %d\n",
               lastL4SkipDocId,
               (int) _l4SkipDocIdBitsOffset,
               (int) _l4SkipL1SkipBitsOffset,
               _l4SkipDocId);
#endif
    } while (docId > _l4SkipDocId);
    UC64_DECODECONTEXT_STORE(s, _l4SkipBits._);
    _l1SkipDocId = _l2SkipDocId = _l3SkipDocId = lastL4SkipDocId;
    _l1SkipDocIdBitsOffset = _l2SkipDocIdBitsOffset = _l3SkipDocIdBitsOffset =
                             _l4SkipDocIdBitsOffset;
    _l2SkipL1SkipBitsOffset = _l3SkipL1SkipBitsOffset =_l4SkipL1SkipBitsOffset;
    _l3SkipL2SkipBitsOffset =_l4SkipL2SkipBitsOffset;
    _docIdBits.seek(_l4SkipDocIdBitsOffset);
    _l1SkipBits.seek(_l4SkipL1SkipBitsOffset);
    _l2SkipBits.seek(_l4SkipL2SkipBitsOffset);
    _l3SkipBits.seek(_l4SkipL3SkipBitsOffset);
    lastL4SkipDocId += _docIdBits.getDocIdDelta();
    _l1SkipDocId += _l1SkipBits.getL1SkipDocIdDelta();
    _l2SkipDocId += _l2SkipBits.getL2SkipDocIdDelta();
    _l3SkipDocId += _l3SkipBits.getL3SkipDocIdDelta();
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
    printf("L4Seek, docId %d docIdPos %d L1SkipPos %d, nextDocId %d\n",
           lastL4SkipDocId,
           (int) _l4SkipDocIdBitsOffset,
           (int) _l4SkipL1SkipBitsOffset,
           _l4SkipDocId);
#endif
    setDocId(lastL4SkipDocId);
}


template<>
void
FakeFilterOccEGCompressed64SkipArrayIterator<true>::
doL3SkipSeek(uint32_t docId)
{
    unsigned int length;
    uint32_t lastL3SkipDocId;
    const bool bigEndian = true;

    if (__builtin_expect(docId > _l4SkipDocId, false)) {
        doL4SkipSeek(docId);
        if (docId <= _l3SkipDocId)
            return;
    }

    UC64_DECODECONTEXT_CONSTRUCTOR(s, _l3SkipBits._);
    do {
        lastL3SkipDocId = _l3SkipDocId;
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L3SKIPDELTA_BITPOS, EC,
                _l3SkipDocIdBitsOffset += 1 +);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L3SKIPDELTA_L1SKIPBITPOS, EC,
                _l3SkipL1SkipBitsOffset += 1 +);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L3SKIPDELTA_L2SKIPBITPOS, EC,
                _l3SkipL2SkipBitsOffset += 1 +);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L3SKIPDELTA_DOCID, EC,
                _l3SkipDocId += 1 +);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("L3Decode docId=%d, docIdPos=%d, l1SkipPos=%d, nextDocId %d\n",
               lastL3SkipDocId,
               (int) _l3SkipDocIdBitsOffset,
               (int) _l3SkipL1SkipBitsOffset,
               _l3SkipDocId);
#endif
    } while (docId > _l3SkipDocId);
    UC64_DECODECONTEXT_STORE(s, _l3SkipBits._);
    _l1SkipDocId = _l2SkipDocId = lastL3SkipDocId;
    _l1SkipDocIdBitsOffset = _l2SkipDocIdBitsOffset = _l3SkipDocIdBitsOffset;
    _l2SkipL1SkipBitsOffset = _l3SkipL1SkipBitsOffset;
    _docIdBits.seek(_l3SkipDocIdBitsOffset);
    _l1SkipBits.seek(_l3SkipL1SkipBitsOffset);
    _l2SkipBits.seek(_l3SkipL2SkipBitsOffset);
    lastL3SkipDocId += _docIdBits.getDocIdDelta();
    _l1SkipDocId += _l1SkipBits.getL1SkipDocIdDelta();
    _l2SkipDocId += _l2SkipBits.getL2SkipDocIdDelta();
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
    printf("L3Seek, docId %d docIdPos %d L1SkipPos %d, nextDocId %d\n",
           lastL3SkipDocId,
           (int) _l3SkipDocIdBitsOffset,
           (int) _l3SkipL1SkipBitsOffset,
           _l3SkipDocId);
#endif
    setDocId(lastL3SkipDocId);
}


template<>
void
FakeFilterOccEGCompressed64SkipArrayIterator<true>::
doL2SkipSeek(uint32_t docId)
{
    unsigned int length;
    uint32_t lastL2SkipDocId;
    const bool bigEndian = true;

    if (__builtin_expect(docId > _l3SkipDocId, false)) {
        doL3SkipSeek(docId);
        if (docId <= _l2SkipDocId)
            return;
    }

    UC64_DECODECONTEXT_CONSTRUCTOR(s, _l2SkipBits._);
    do {
        lastL2SkipDocId = _l2SkipDocId;
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L2SKIPDELTA_BITPOS, EC,
                _l2SkipDocIdBitsOffset += 1 +);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L2SKIPDELTA_L1SKIPBITPOS, EC,
                _l2SkipL1SkipBitsOffset += 1 +);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L2SKIPDELTA_DOCID, EC,
                _l2SkipDocId += 1 +);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("L2Decode docId=%d, docIdPos=%d, l1SkipPos=%d, nextDocId %d\n",
               lastL2SkipDocId,
               (int) _l2SkipDocIdBitsOffset,
               (int) _l2SkipL1SkipBitsOffset,
               _l2SkipDocId);
#endif
    } while (docId > _l2SkipDocId);
    UC64_DECODECONTEXT_STORE(s, _l2SkipBits._);
    _l1SkipDocId = lastL2SkipDocId;
    _l1SkipDocIdBitsOffset = _l2SkipDocIdBitsOffset;
    _docIdBits.seek(_l2SkipDocIdBitsOffset);
    _l1SkipBits.seek(_l2SkipL1SkipBitsOffset);
    lastL2SkipDocId += _docIdBits.getDocIdDelta();
    _l1SkipDocId += _l1SkipBits.getL1SkipDocIdDelta();
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
    printf("L2Seek, docId %d docIdPos %d L1SkipPos %d, nextDocId %d\n",
           lastL2SkipDocId,
           (int) _l2SkipDocIdBitsOffset,
           (int) _l2SkipL1SkipBitsOffset,
           _l2SkipDocId);
#endif
    setDocId(lastL2SkipDocId);
}


template<>
void
FakeFilterOccEGCompressed64SkipArrayIterator<false>::doL1SkipSeek(uint32_t docId)
{
    (void) docId;
}


template<>
void
FakeFilterOccEGCompressed64SkipArrayIterator<true>::doL1SkipSeek(uint32_t docId)
{
    unsigned int length;
    uint32_t lastL1SkipDocId;
    const bool bigEndian = true;

    if (__builtin_expect(docId > _l2SkipDocId, false)) {
        doL2SkipSeek(docId);
        if (docId <= _l1SkipDocId)
            return;
    }
    UC64_DECODECONTEXT_CONSTRUCTOR(s, _l1SkipBits._);
    do {
        lastL1SkipDocId = _l1SkipDocId;
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L1SKIPDELTA_BITPOS, EC,
                _l1SkipDocIdBitsOffset += 1 +);
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(sVal, sCompr, sPreRead, sCacheInt,
                K_VALUE_FILTEROCC_L1SKIPDELTA_DOCID, EC,
                _l1SkipDocId += 1 +);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("L1Decode docId=%d docIdPos=%d, L1SkipPos=%d, nextDocId %d\n",
               lastL1SkipDocId,
               (int) _l1SkipDocIdBitsOffset,
               (int) _l1SkipBits.getOffset(sCompr, sPreRead),
               _l1SkipDocId);
#endif
    } while (docId > _l1SkipDocId);
    UC64_DECODECONTEXT_STORE(s, _l1SkipBits._);
    _docIdBits.seek(_l1SkipDocIdBitsOffset);
    lastL1SkipDocId += _docIdBits.getDocIdDelta();
    setDocId(lastL1SkipDocId);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
    printf("L1SkipSeek, docId %d docIdPos %d, nextDocId %d\n",
           lastL1SkipDocId,
           (int) _l1SkipDocIdBitsOffset,
           _l1SkipDocId);
#endif
}


template <bool doSkip>
void
FakeFilterOccEGCompressed64SkipArrayIterator<doSkip>::doSeek(uint32_t docId)
{
    if (getUnpacked())
        clearUnpacked();
    if (doSkip && docId > _l1SkipDocId) {
        doL1SkipSeek(docId);
    }
    unsigned int length;
    uint32_t oDocId = getDocId();
    const bool bigEndian = true;
    if (doSkip) {
#if DEBUG_EGCOMPR64FILTEROCC_ASSERT
        assert(oDocId <= _l1SkipDocId);
        assert(docId <= _l1SkipDocId);
        assert(oDocId <= _l2SkipDocId);
        assert(docId <= _l2SkipDocId);
        assert(oDocId <= _l3SkipDocId);
        assert(docId <= _l3SkipDocId);
        assert(oDocId <= _l4SkipDocId);
        assert(docId <= _l4SkipDocId);
#endif
    }
    UC64_DECODECONTEXT_CONSTRUCTOR(o, this->_docIdBits._);
    while (__builtin_expect(oDocId < docId, true)) {
        if (!doSkip) {
            if (__builtin_expect(oDocId >= _lastDocId, false)) {
#if DEBUG_ZCFILTEROCC_ASSERT
                assert(_l1SkipDocId == _lastDocId);
                assert(_l2SkipDocId == _lastDocId);
                assert(_l3SkipDocId == _lastDocId);
                assert(_l4SkipDocId == _lastDocId);
#endif
                oDocId = _l1SkipDocId = _l2SkipDocId = _l3SkipDocId = _l4SkipDocId = search::endDocId;
                break;
            }
        }
        if (doSkip) {
#if DEBUG_EGCOMPR64FILTEROCC_ASSERT
            assert(oDocId <= _l1SkipDocId);
            assert(oDocId <= _l2SkipDocId);
            assert(oDocId <= _l3SkipDocId);
            assert(oDocId <= _l4SkipDocId);
#endif
        } else if (__builtin_expect(oDocId >= _l1SkipDocId, false)) {
            // Validate L1 Skip information
            assert(oDocId == _l1SkipDocId);
            uint64_t docIdBitsOffset = _docIdBits.getOffset(oCompr, oPreRead);
            UC64_DECODECONTEXT_CONSTRUCTOR(s1, _l1SkipBits._);
            UC64_DECODEEXPGOLOMB_SMALL_APPLY(s1Val, s1Compr, s1PreRead,
                    s1CacheInt,
                    K_VALUE_FILTEROCC_L1SKIPDELTA_BITPOS, EC,
                    _l1SkipDocIdBitsOffset += 1 +);
            assert(docIdBitsOffset == _l1SkipDocIdBitsOffset);
            if (__builtin_expect(oDocId >= _l2SkipDocId, false)) {
                // Validate L2 Skip information
                assert(oDocId == _l2SkipDocId);
                uint64_t l1SkipBitsOffset =
                    _l1SkipBits.getOffset(s1Compr, s1PreRead);
                UC64_DECODECONTEXT_CONSTRUCTOR(s2, _l2SkipBits._);
                UC64_DECODEEXPGOLOMB_SMALL_APPLY(s2Val, s2Compr, s2PreRead,
                        s2CacheInt,
                        K_VALUE_FILTEROCC_L2SKIPDELTA_BITPOS, EC,
                        _l2SkipDocIdBitsOffset += 1 +);
                UC64_DECODEEXPGOLOMB_SMALL_APPLY(s2Val, s2Compr, s2PreRead,
                        s2CacheInt,
                        K_VALUE_FILTEROCC_L2SKIPDELTA_L1SKIPBITPOS, EC,
                        _l2SkipL1SkipBitsOffset += 1 +);
                assert(docIdBitsOffset == _l2SkipDocIdBitsOffset);
                assert(l1SkipBitsOffset == _l2SkipL1SkipBitsOffset);
                if (__builtin_expect(oDocId >= _l3SkipDocId, false)) {
                    // Validate L3 Skip information
                    assert(oDocId == _l3SkipDocId);
                    uint64_t l2SkipBitsOffset =
                        _l2SkipBits.getOffset(s2Compr, s2PreRead);
                    UC64_DECODECONTEXT_CONSTRUCTOR(s3, _l3SkipBits._);
                    UC64_DECODEEXPGOLOMB_SMALL_APPLY(s3Val, s3Compr,
                            s3PreRead,
                            s3CacheInt,
                            K_VALUE_FILTEROCC_L3SKIPDELTA_BITPOS, EC,
                            _l3SkipDocIdBitsOffset += 1 +);
                    UC64_DECODEEXPGOLOMB_SMALL_APPLY(s3Val, s3Compr,
                            s3PreRead,
                            s3CacheInt,
                            K_VALUE_FILTEROCC_L3SKIPDELTA_L1SKIPBITPOS, EC,
                            _l3SkipL1SkipBitsOffset += 1 +);
                    UC64_DECODEEXPGOLOMB_SMALL_APPLY(s3Val, s3Compr,
                            s3PreRead,
                            s3CacheInt,
                            K_VALUE_FILTEROCC_L3SKIPDELTA_L2SKIPBITPOS, EC,
                            _l3SkipL2SkipBitsOffset += 1 +);
                    assert(docIdBitsOffset == _l3SkipDocIdBitsOffset);
                    assert(l1SkipBitsOffset == _l3SkipL1SkipBitsOffset);
                    assert(l2SkipBitsOffset == _l3SkipL2SkipBitsOffset);
                    if (__builtin_expect(oDocId >= _l4SkipDocId, false)) {
                        // Validate L4 Skip information
                        assert(oDocId == _l4SkipDocId);
                        uint64_t l3SkipBitsOffset =
                            _l3SkipBits.getOffset(s3Compr, s3PreRead);
                        UC64_DECODECONTEXT_CONSTRUCTOR(s4, _l4SkipBits._);
                        UC64_DECODEEXPGOLOMB_SMALL_APPLY(s4Val, s4Compr,
                                s4PreRead,
                                s4CacheInt,
                                K_VALUE_FILTEROCC_L4SKIPDELTA_BITPOS, EC,
                                _l4SkipDocIdBitsOffset += 1 +);
                        UC64_DECODEEXPGOLOMB_SMALL_APPLY(s4Val, s4Compr,
                                s4PreRead,
                                s4CacheInt,
                                K_VALUE_FILTEROCC_L4SKIPDELTA_L1SKIPBITPOS, EC,
                                _l4SkipL1SkipBitsOffset += 1 +);
                        UC64_DECODEEXPGOLOMB_SMALL_APPLY(s4Val, s4Compr,
                                s4PreRead,
                                s4CacheInt,
                                K_VALUE_FILTEROCC_L4SKIPDELTA_L2SKIPBITPOS, EC,
                                _l4SkipL2SkipBitsOffset += 1 +);
                        UC64_DECODEEXPGOLOMB_SMALL_APPLY(s4Val, s4Compr,
                                s4PreRead,
                                s4CacheInt,
                                K_VALUE_FILTEROCC_L4SKIPDELTA_L3SKIPBITPOS, EC,
                                _l4SkipL3SkipBitsOffset += 1 +);
                        assert(docIdBitsOffset == _l4SkipDocIdBitsOffset);
                        (void) docIdBitsOffset;
                        assert(l1SkipBitsOffset == _l4SkipL1SkipBitsOffset);
                        (void) l1SkipBitsOffset;
                        assert(l2SkipBitsOffset == _l4SkipL2SkipBitsOffset);
                        (void) l2SkipBitsOffset;
                        assert(l3SkipBitsOffset == _l4SkipL3SkipBitsOffset);
                        (void) l3SkipBitsOffset;
                        UC64_DECODEEXPGOLOMB_SMALL_APPLY(s4Val, s4Compr,
                                s4PreRead,
                                s4CacheInt,
                                K_VALUE_FILTEROCC_L4SKIPDELTA_DOCID, EC,
                                _l4SkipDocId += 1 +);
                        UC64_DECODECONTEXT_STORE(s4, _l4SkipBits._);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
                        printf("L4DecodeV docId=%d docIdPos=%d L1SkipPos=%d\n",
                               _l4SkipDocId,
                               (int) _l4SkipDocIdBitsOffset,
                               (int) _l4SkipL1SkipBitsOffset);
#endif
                    }
                    UC64_DECODEEXPGOLOMB_SMALL_APPLY(s3Val, s3Compr,
                            s3PreRead,
                            s3CacheInt,
                            K_VALUE_FILTEROCC_L3SKIPDELTA_DOCID, EC,
                            _l3SkipDocId += 1 +);
                    UC64_DECODECONTEXT_STORE(s3, _l3SkipBits._);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
                    printf("L3DecodeV docId=%d docIdPos=%d L1SkipPos=%d\n",
                           _l3SkipDocId,
                           (int) _l3SkipDocIdBitsOffset,
                           (int) _l3SkipL1SkipBitsOffset);
#endif
                }
                UC64_DECODEEXPGOLOMB_SMALL_APPLY(s2Val, s2Compr, s2PreRead,
                        s2CacheInt,
                        K_VALUE_FILTEROCC_L2SKIPDELTA_DOCID, EC,
                        _l2SkipDocId += 1 +);
                UC64_DECODECONTEXT_STORE(s2, _l2SkipBits._);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
                printf("L2DecodeV docId=%d docIdPos=%d L1SkipPos=%d\n",
                       _l2SkipDocId,
                       (int) _l2SkipDocIdBitsOffset,
                       (int) _l2SkipL1SkipBitsOffset);
#endif
            }
            UC64_DECODEEXPGOLOMB_SMALL_APPLY(s1Val, s1Compr, s1PreRead,
                    s1CacheInt,
                    K_VALUE_FILTEROCC_L1SKIPDELTA_DOCID, EC,
                    _l1SkipDocId += 1 +);
            UC64_DECODECONTEXT_STORE(s1, _l1SkipBits._);
            assert(docIdBitsOffset == _l1SkipDocIdBitsOffset);
            BitDecode64BE
                checkDocIdBits(_docIdBits.getComprBase(),
                               _docIdBits.getBitOffsetBase());
            checkDocIdBits.seek(_l1SkipDocIdBitsOffset);
            if (checkDocIdBits._valI != oCompr ||
                checkDocIdBits._val != oVal ||
                checkDocIdBits._cacheInt != oCacheInt ||
                checkDocIdBits._preRead != oPreRead) {
                printf("seek problem: check "
                       "(%p,%d) "
                       "%p,%" PRIu64 ",%" PRIu64 ",%u != "
                       "(%p,%d) "
                       "%p,%" PRIu64 ",%" PRIu64 ",%u for "
                       "offset %" PRIu64 "\n",
                       checkDocIdBits.getComprBase(),
                       checkDocIdBits.getBitOffsetBase(),
                       checkDocIdBits._valI,
                       checkDocIdBits._val,
                       checkDocIdBits._cacheInt,
                       checkDocIdBits._preRead,
                       _docIdBits.getComprBase(),
                       _docIdBits.getBitOffsetBase(),
                       oCompr,
                       oVal,
                       oCacheInt,
                       oPreRead,
                       _l1SkipDocIdBitsOffset);
                LOG_ABORT("should not be reached");
            }
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
            printf("L1DecodeV docId=%d docIdPos=%d L1SkipPos=%d\n",
                   _l1SkipDocId,
                   (int) _l2SkipDocIdBitsOffset,
                   (int) _l2SkipL1SkipBitsOffset);
#endif
        }
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(oVal, oCompr, oPreRead, oCacheInt,
                K_VALUE_FILTEROCC_DELTA_DOCID, EC,
                oDocId += 1 +);
#if DEBUG_EGCOMPR64FILTEROCC_PRINTF
        printf("Decode docId=%d\n",
               oDocId);
#endif
    }
    UC64_DECODECONTEXT_STORE(o, this->_docIdBits._);
    setDocId(oDocId);
    return;
}


template <bool doSkip>
void
FakeFilterOccEGCompressed64SkipArrayIterator<doSkip>::doUnpack(uint32_t docId)
{
    if (_matchData.size() != 1) {
        return;
    }
    _matchData[0]->clear_hidden_from_ranking();
    if (getUnpacked()) {
        return;
    }
    assert(docId == getDocId());
    _matchData[0]->reset(docId);
    setUnpacked();
}


template <bool doSkip>
std::unique_ptr<search::queryeval::SearchIterator>
FakeEGCompr64SkipFilterOcc<doSkip>::
createIterator(const fef::TermFieldMatchDataArray &matchData) const
{
    unsigned int length;
    uint64_t val64;
    const uint64_t *arr = _compressed.first;
    const bool bigEndian = true;
    BitDecode64BE docIdBits(arr, 0);
    assert(docIdBits.getCompr() == arr);
    assert(docIdBits.getBitOffset() == 0);
    assert(docIdBits.getOffset() == 0);

    using EC = bitcompression::EncodeContext64BE;

    uint32_t myResidue = 0;
    UC64_FILTEROCC_READ_RESIDUE(docIdBits._val,
                                docIdBits._valI,
                                docIdBits._preRead,
                                docIdBits._cacheInt, myResidue, EC);
    assert(myResidue == _hitDocs);
    (void) myResidue;

    const uint64_t *l1SkipArr = _l1SkipCompressed.first;
    const uint64_t *l2SkipArr = _l2SkipCompressed.first;
    const uint64_t *l3SkipArr = _l3SkipCompressed.first;
    const uint64_t *l4SkipArr = _l4SkipCompressed.first;
    return std::make_unique<FakeFilterOccEGCompressed64SkipArrayIterator<doSkip>>
        (docIdBits.getCompr(),
         docIdBits.getBitOffset(),
         _lastDocId,
         l1SkipArr, 0,
         l2SkipArr, 0,
         l3SkipArr, 0,
         l4SkipArr, 0,
         getName(),
         matchData);
}

}

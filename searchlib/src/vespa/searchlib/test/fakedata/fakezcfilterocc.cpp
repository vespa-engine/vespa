// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fakezcfilterocc");
#include "fakezcfilterocc.h"
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/diskindex/zcposocciterators.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/postinglistcounts.h>
#include "fpfactory.h"


using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::fef::TermFieldMatchDataPosition;
using search::queryeval::SearchIterator;
using search::index::PostingListParams;
using search::index::DocIdAndFeatures;
using search::index::DocIdAndPosOccFeatures;
using search::bitcompression::PosOccFieldParams;
using search::bitcompression::EGPosOccEncodeContext;
using search::bitcompression::EG2PosOccEncodeContext;
using search::bitcompression::FeatureEncodeContext;
using search::ComprFileWriteContext;
using namespace search::diskindex;

namespace search
{

namespace fakedata
{


#define L1SKIPSTRIDE 16
#define L2SKIPSTRIDE 8
#define L3SKIPSTRIDE 8
#define L4SKIPSTRIDE 8

#define DEBUG_ZCFILTEROCC_PRINTF 0
#define DEBUG_ZCFILTEROCC_ASSERT 0

static FPFactoryInit
init(std::make_pair("ZcFilterOcc",
                    makeFPFactory<FPFactoryT<FakeZcFilterOcc> >));

static void
zcEncode(std::vector<uint8_t> &bytes,
         uint32_t num)
{
    for (;;) {
        if (num < (1 << 7)) {
            bytes.push_back(num);
            break;
        }
        bytes.push_back((num & ((1 << 7) - 1)) | (1 << 7));
        num >>= 7;
    }
}

#define ZCDECODE(valI, resop)						\
do {									\
    if (__builtin_expect(valI[0] < (1 << 7), true)) {			\
	resop valI[0];							\
	valI += 1;							\
    } else if (__builtin_expect(valI[1] < (1 << 7), true)) {		\
        resop (valI[0] & ((1 << 7) - 1)) +				\
              (valI[1] << 7);						\
        valI += 2;							\
    } else if (__builtin_expect(valI[2] < (1 << 7), true)) {		\
        resop (valI[0] & ((1 << 7) - 1)) +				\
              ((valI[1] & ((1 << 7) - 1)) << 7) +			\
              (valI[2] << 14);						\
        valI += 3;							\
    } else if (__builtin_expect(valI[3] < (1 << 7), true)) {		\
        resop (valI[0] & ((1 << 7) - 1)) +				\
              ((valI[1] & ((1 << 7) - 1)) << 7) +			\
              ((valI[2] & ((1 << 7) - 1)) << 14) +			\
              (valI[3] << 21);						\
        valI += 4;							\
    } else {								\
        resop (valI[0] & ((1 << 7) - 1)) +				\
              ((valI[1] & ((1 << 7) - 1)) << 7) +			\
              ((valI[2] & ((1 << 7) - 1)) << 14) +			\
              ((valI[3] & ((1 << 7) - 1)) << 21) +			\
              (valI[4] << 28);						\
        valI += 5;							\
    }									\
} while (0)

FakeZcFilterOcc::FakeZcFilterOcc(const FakeWord &fw)
    : FakePosting(fw.getName() + ".zcfilterocc"),
      _docIdsSize(0),
      _l1SkipSize(0),
      _l2SkipSize(0),
      _l3SkipSize(0),
      _l4SkipSize(0),
      _docIdLimit(0),
      _hitDocs(0),
      _lastDocId(0u),
      _compressedBits(0),
      _compressed(std::make_pair(static_cast<uint64_t *>(NULL), 0)),
      _compressedMalloc(NULL),
      _featuresSize(0),
      _fieldsParams(fw.getFieldsParams()),
      _bigEndian(true)
{
    setup(fw, false, true);
}


FakeZcFilterOcc::FakeZcFilterOcc(const FakeWord &fw,
                                 bool bigEndian,
                                 const char *nameSuffix)
    : FakePosting(fw.getName() + nameSuffix),
      _docIdsSize(0),
      _l1SkipSize(0),
      _l2SkipSize(0),
      _l3SkipSize(0),
      _l4SkipSize(0),
      _docIdLimit(0),
      _hitDocs(0),
      _lastDocId(0u),
      _compressedBits(0),
      _compressed(std::make_pair(static_cast<uint64_t *>(NULL), 0)),
      _featuresSize(0),
      _fieldsParams(fw.getFieldsParams()),
      _bigEndian(bigEndian)
{
    // subclass responsible for calling setup(fw, false/true);
}


void
FakeZcFilterOcc::setup(const FakeWord &fw, bool doFeatures,
                       bool dynamicK)
{
    if (_bigEndian)
        setupT<true>(fw, doFeatures, dynamicK);
    else
        setupT<false>(fw, doFeatures, dynamicK);
}


template <bool bigEndian>
void
FakeZcFilterOcc::setupT(const FakeWord &fw, bool doFeatures,
                        bool dynamicK)
{
    std::vector<uint8_t> bytes;
    std::vector<uint8_t> l1SkipBytes;
    std::vector<uint8_t> l2SkipBytes;
    std::vector<uint8_t> l3SkipBytes;
    std::vector<uint8_t> l4SkipBytes;
    uint32_t lastDocId = 0u;
    uint32_t lastL1SkipDocId = 0u;
    uint64_t lastL1SkipDocIdPos = 0;
    uint64_t lastL1SkipFeaturePos = 0;
    unsigned int l1SkipCnt = 0;
    uint32_t lastL2SkipDocId = 0u;
    uint64_t lastL2SkipDocIdPos = 0;
    uint64_t lastL2SkipFeaturePos = 0;
    uint64_t lastL2SkipL1SkipPos = 0;
    unsigned int l2SkipCnt = 0;
    uint32_t lastL3SkipDocId = 0u;
    uint64_t lastL3SkipDocIdPos = 0;
    uint64_t lastL3SkipFeaturePos = 0;
    uint64_t lastL3SkipL1SkipPos = 0;
    uint64_t lastL3SkipL2SkipPos = 0;
    unsigned int l3SkipCnt = 0;
    uint32_t lastL4SkipDocId = 0u;
    uint64_t lastL4SkipDocIdPos = 0;
    uint64_t lastL4SkipFeaturePos = 0;
    uint64_t lastL4SkipL1SkipPos = 0;
    uint64_t lastL4SkipL2SkipPos = 0;
    uint64_t lastL4SkipL3SkipPos = 0;
    unsigned int l4SkipCnt = 0;
    uint64_t featurePos = 0;

    typedef FakeWord FW;
    typedef FW::DocWordFeatureList DWFL;
    typedef FW::DocWordPosFeatureList DWPFL;

    DWFL::const_iterator d(fw._postings.begin());
    DWFL::const_iterator de(fw._postings.end());
    DWPFL::const_iterator p(fw._wordPosFeatures.begin());
    DWPFL::const_iterator pe(fw._wordPosFeatures.end());
    DocIdAndPosOccFeatures features;
    EGPosOccEncodeContext<bigEndian> f1(&_fieldsParams);
    EG2PosOccEncodeContext<bigEndian> f0(&_fieldsParams);
    FeatureEncodeContext<bigEndian> &f = (dynamicK ?
            static_cast<FeatureEncodeContext<bigEndian> &>(f1) :
            static_cast<FeatureEncodeContext<bigEndian> &>(f0));
    search::ComprFileWriteContext fctx(f);
    f.setWriteContext(&fctx);
    fctx.allocComprBuf(64, 1);
    f.afterWrite(fctx, 0, 0);

    while (d != de) {
        if (l1SkipCnt >= L1SKIPSTRIDE) {
            uint32_t docIdDelta = lastDocId - lastL1SkipDocId;
            assert(static_cast<int32_t>(docIdDelta) > 0);
            zcEncode(l1SkipBytes, docIdDelta - 1);
            uint64_t lastDocIdPos = bytes.size();
            uint32_t docIdPosDelta = lastDocIdPos - lastL1SkipDocIdPos;
            zcEncode(l1SkipBytes, docIdPosDelta - 1);
            if (doFeatures) {
                featurePos = f.getWriteOffset();
                zcEncode(l1SkipBytes, featurePos - lastL1SkipFeaturePos - 1);
                lastL1SkipFeaturePos = featurePos;
            }
#if DEBUG_ZCFILTEROCC_PRINTF
            printf("L1Encode docId=%d (+%d), docIdPos=%d (+%u)\n",
                   lastDocId, docIdDelta,
                   (int) lastDocIdPos, docIdPosDelta);
#endif
            lastL1SkipDocId = lastDocId;
            lastL1SkipDocIdPos = lastDocIdPos;
            l1SkipCnt = 0;
            ++l2SkipCnt;
            if (l2SkipCnt >= L2SKIPSTRIDE) {
                docIdDelta = lastDocId - lastL2SkipDocId;
                docIdPosDelta = lastDocIdPos - lastL2SkipDocIdPos;
                uint64_t lastL1SkipPos = l1SkipBytes.size();
                uint32_t l1SkipPosDelta = lastL1SkipPos - lastL2SkipL1SkipPos;
                zcEncode(l2SkipBytes, docIdDelta - 1);
                zcEncode(l2SkipBytes, docIdPosDelta - 1);
                if (doFeatures) {
                    zcEncode(l2SkipBytes,
                             featurePos - lastL2SkipFeaturePos - 1);
                    lastL2SkipFeaturePos = featurePos;
                }
                zcEncode(l2SkipBytes, l1SkipPosDelta - 1);
#if DEBUG_ZCFILTEROCC_PRINTF
                printf("L2Encode docId=%d (+%d), docIdPos=%d (+%u),"
                       " l1SkipPos=%d (+%u)\n",
                       lastDocId, docIdDelta,
                       (int) lastDocIdPos, docIdPosDelta,
                       (int) lastL1SkipPos, l1SkipPosDelta);
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
                    uint64_t lastL2SkipPos = l2SkipBytes.size();
                    uint32_t l2SkipPosDelta = lastL2SkipPos -
                                              lastL3SkipL2SkipPos;
                    zcEncode(l3SkipBytes, docIdDelta - 1);
                    zcEncode(l3SkipBytes, docIdPosDelta - 1);
                    if (doFeatures) {
                        zcEncode(l3SkipBytes,
                                 featurePos - lastL3SkipFeaturePos - 1);
                        lastL3SkipFeaturePos = featurePos;
                    }
                    zcEncode(l3SkipBytes, l1SkipPosDelta - 1);
                    zcEncode(l3SkipBytes, l2SkipPosDelta - 1);
#if DEBUG_ZCFILTEROCC_PRINTF
                    printf("L3Encode docId=%d (+%d), docIdPos=%d (+%u),"
                           " l1SkipPos=%d (+%u) l2SkipPos %d (+%u)\n",
                           lastDocId, docIdDelta,
                           (int) lastDocIdPos, docIdPosDelta,
                           (int) lastL1SkipPos, l1SkipPosDelta,
                           (int) lastL2SkipPos, l2SkipPosDelta);
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
                        uint64_t lastL3SkipPos = l3SkipBytes.size();
                        uint32_t l3SkipPosDelta = lastL3SkipPos -
                                                  lastL4SkipL3SkipPos;
                        zcEncode(l4SkipBytes, docIdDelta - 1);
                        zcEncode(l4SkipBytes, docIdPosDelta - 1);
                        if (doFeatures) {
                            zcEncode(l4SkipBytes,
                                     featurePos - lastL4SkipFeaturePos - 1);
                            lastL4SkipFeaturePos = featurePos;
                        }
                        zcEncode(l4SkipBytes, l1SkipPosDelta - 1);
                        zcEncode(l4SkipBytes, l2SkipPosDelta - 1);
                        zcEncode(l4SkipBytes, l3SkipPosDelta - 1);
#if DEBUG_ZCFILTEROCC_PRINTF
                        printf("L4Encode docId=%d (+%d), docIdPos=%d (+%u),"
                           " l1SkipPos=%d (+%u) l2SkipPos %d (+%u)"
                           " l3SkipPos=%d (+%u)\n",
                               lastDocId, docIdDelta,
                               (int) lastDocIdPos, docIdPosDelta,
                               (int) lastL1SkipPos, l1SkipPosDelta,
                               (int) lastL2SkipPos, l2SkipPosDelta,
                               (int) lastL3SkipPos, l3SkipPosDelta);
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
            zcEncode(bytes, d->_docId - 1);
#if DEBUG_ZCFILTEROCC_PRINTF
            printf("Encode docId=%d\n",
                   d->_docId);
#endif
        } else {
            uint32_t docIdDelta = d->_docId - lastDocId;
            zcEncode(bytes, docIdDelta - 1);
#if DEBUG_ZCFILTEROCC_PRINTF
            printf("Encode docId=%d (+%d)\n",
                   d->_docId, docIdDelta);
#endif
        }
        if (doFeatures) {
            fw.setupFeatures(*d, &*p, features);
            p += d->_positions;
            f.writeFeatures(features);
        }
        lastDocId = d->_docId;
        ++l1SkipCnt;
        ++d;
    }
    if (doFeatures) {
        assert(p == pe);
        _featuresSize = f.getWriteOffset();
        // First pad to 64 bits.
        uint32_t pad = (64 - f.getWriteOffset()) & 63;
        while (pad > 0) {
            uint32_t now = std::min(32u, pad);
            f.writeBits(0, now);
            f.writeComprBufferIfNeeded();
            pad -= now;
        }

        // Then write 128 more bits.  This allows for 64-bit decoding
        // with a readbits that always leaves a nonzero preRead
        for (unsigned int i = 0; i < 4; i++) {
            f.writeBits(0, 32);
            f.writeComprBufferIfNeeded();
        }
        f.writeComprBufferIfNeeded();
        f.flush();
        f.writeComprBuffer();
    } else {
        _featuresSize = 0;
    }
    // Extra partial entries for skip tables to simplify iterator during search
    if (l1SkipBytes.size() > 0) {
        uint32_t docIdDelta = lastDocId - lastL1SkipDocId;
        assert(static_cast<int32_t>(docIdDelta) > 0);
        zcEncode(l1SkipBytes, docIdDelta - 1);
    }
    if (l2SkipBytes.size() > 0) {
        uint32_t docIdDelta = lastDocId - lastL2SkipDocId;
        assert(static_cast<int32_t>(docIdDelta) > 0);
        zcEncode(l2SkipBytes, docIdDelta - 1);
    }
    if (l3SkipBytes.size() > 0) {
        uint32_t docIdDelta = lastDocId - lastL3SkipDocId;
        assert(static_cast<int32_t>(docIdDelta) > 0);
        zcEncode(l3SkipBytes, docIdDelta - 1);
    }
    if (l4SkipBytes.size() > 0) {
        uint32_t docIdDelta = lastDocId - lastL4SkipDocId;
        assert(static_cast<int32_t>(docIdDelta) > 0);
        zcEncode(l4SkipBytes, docIdDelta - 1);
    }
    _hitDocs = fw._postings.size();
    _docIdLimit = fw._docIdLimit;
    _lastDocId = lastDocId;
    FeatureEncodeContext<bigEndian> e;
    ComprFileWriteContext ectx(e);
    e.setWriteContext(&ectx);
    ectx.allocComprBuf(64, 1);
    e.afterWrite(ectx, 0, 0);

    // Encode word header
    e.encodeExpGolomb(_hitDocs - 1, K_VALUE_ZCPOSTING_NUMDOCS);
    _docIdsSize = bytes.size() * 8;
    _l1SkipSize = l1SkipBytes.size();
    _l2SkipSize = _l3SkipSize = _l4SkipSize = 0;
    if (_l1SkipSize != 0)
        _l2SkipSize = l2SkipBytes.size();
    if (_l2SkipSize != 0)
        _l3SkipSize = l3SkipBytes.size();
    if (_l3SkipSize != 0)
        _l4SkipSize = l4SkipBytes.size();

    e.encodeExpGolomb(bytes.size() - 1, K_VALUE_ZCPOSTING_DOCIDSSIZE);
    e.encodeExpGolomb(_l1SkipSize, K_VALUE_ZCPOSTING_L1SKIPSIZE);
    e.writeComprBufferIfNeeded();
    if (_l1SkipSize != 0) {
        e.encodeExpGolomb(_l2SkipSize, K_VALUE_ZCPOSTING_L2SKIPSIZE);
        if (_l2SkipSize != 0) {
            e.writeComprBufferIfNeeded();
            e.encodeExpGolomb(_l3SkipSize, K_VALUE_ZCPOSTING_L3SKIPSIZE);
            if (_l3SkipSize != 0) {
                e.encodeExpGolomb(_l4SkipSize, K_VALUE_ZCPOSTING_L4SKIPSIZE);
            }
        }
    }
    e.writeComprBufferIfNeeded();
    if (doFeatures) {
        e.encodeExpGolomb(_featuresSize, K_VALUE_ZCPOSTING_FEATURESSIZE);
    }
    uint32_t docIdK = e.calcDocIdK(_hitDocs, _docIdLimit);
    if (dynamicK)
        e.encodeExpGolomb(_docIdLimit - 1 - _lastDocId, docIdK);
    else
        e.encodeExpGolomb(_docIdLimit - 1 - _lastDocId,
                          K_VALUE_ZCPOSTING_LASTDOCID);
    uint64_t bytePad = (- e.getWriteOffset()) & 7;
    if (bytePad > 0)
        e.writeBits(0, bytePad);
    size_t docIdSize = bytes.size();
    if (docIdSize > 0) {
        uint8_t *docIdBytes = &bytes[0];
        uint32_t docIdBytesOffset =
            reinterpret_cast<unsigned long>(docIdBytes) & 7;
        e.writeBits(reinterpret_cast<const uint64_t *>(docIdBytes -
                            docIdBytesOffset),
                    docIdBytesOffset * 8,
                    docIdSize * 8);
    }
    if (_l1SkipSize > 0) {
        uint8_t *l1Bytes = &l1SkipBytes[0];
        uint32_t l1BytesOffset = reinterpret_cast<unsigned long>(l1Bytes) & 7;
        e.writeBits(reinterpret_cast<const uint64_t *>(l1Bytes -
                            l1BytesOffset),
                    l1BytesOffset * 8,
                    _l1SkipSize * 8);
        if (_l2SkipSize > 0) {
            uint8_t *l2Bytes = &l2SkipBytes[0];
            uint32_t l2BytesOffset =
                reinterpret_cast<unsigned long>(l2Bytes) & 7;
            e.writeBits(reinterpret_cast<const uint64_t *>(l2Bytes -
                                l2BytesOffset),
                        l2BytesOffset * 8,
                        _l2SkipSize * 8);
            if (_l3SkipSize > 0) {
                uint8_t *l3Bytes = &l3SkipBytes[0];
                uint32_t l3BytesOffset =
                    reinterpret_cast<unsigned long>(l3Bytes) & 7;
                e.writeBits(reinterpret_cast<const uint64_t *>(l3Bytes -
                                    l3BytesOffset),
                            l3BytesOffset * 8,
                            _l3SkipSize * 8);
                if (_l4SkipSize > 0) {
                    uint8_t *l4Bytes = &l4SkipBytes[0];
                    uint32_t l4BytesOffset =
                        reinterpret_cast<unsigned long>(l4Bytes) & 7;
                    e.writeBits(reinterpret_cast<const uint64_t *>(l4Bytes -
                                        l4BytesOffset),
                                l4BytesOffset * 8,
                                _l4SkipSize * 8);
                }
            }
        }
    }
    if (doFeatures) {
        e.writeBits(static_cast<const uint64_t *>(fctx._comprBuf),
                    0,
                    _featuresSize);
    }
    _compressedBits = e.getWriteOffset();
    // First pad to 64 bits.
    uint32_t pad = (64 - e.getWriteOffset()) & 63;
    while (pad > 0) {
        uint32_t now = std::min(32u, pad);
        e.writeBits(0, now);
        e.writeComprBufferIfNeeded();
        pad -= now;
    }

    // Then write 128 more bits.  This allows for 64-bit decoding
    // with a readbits that always leaves a nonzero preRead
    for (unsigned int i = 0; i < 4; i++) {
        e.writeBits(0, 32);
        e.writeComprBufferIfNeeded();
    }
    e.writeComprBufferIfNeeded();
    e.flush();
    e.writeComprBuffer();

    std::pair<void *, size_t> ectxData = ectx.grabComprBuffer(_compressedMalloc);
    _compressed = std::make_pair(static_cast<uint64_t *>(ectxData.first),
                                 ectxData.second);
}


FakeZcFilterOcc::~FakeZcFilterOcc(void)
{
    free(_compressedMalloc);
}


void
FakeZcFilterOcc::forceLink(void)
{
}


size_t
FakeZcFilterOcc::bitSize(void) const
{
    return _compressedBits -
        (_l1SkipSize + _l2SkipSize + _l3SkipSize + _l4SkipSize) * 8;
}


bool
FakeZcFilterOcc::hasWordPositions(void) const
{
    return false;
}


size_t
FakeZcFilterOcc::skipBitSize(void) const
{
    return (_l1SkipSize + _l2SkipSize + _l3SkipSize + _l4SkipSize) * 8;
}


size_t
FakeZcFilterOcc::l1SkipBitSize(void) const
{
    return _l1SkipSize * 8;
}


size_t
FakeZcFilterOcc::l2SkipBitSize(void) const
{
    return _l2SkipSize * 8;
}


size_t
FakeZcFilterOcc::l3SkipBitSize(void) const
{
    return _l3SkipSize * 8;
}


size_t
FakeZcFilterOcc::l4SkipBitSize(void) const
{
    return _l4SkipSize * 8;
}


int
FakeZcFilterOcc::lowLevelSinglePostingScan(void) const
{
    return 0;
}


int
FakeZcFilterOcc::lowLevelSinglePostingScanUnpack(void) const
{
    return 0;
}


int
FakeZcFilterOcc::
lowLevelAndPairPostingScan(const FakePosting &rhs) const
{
    (void) rhs;
    return 0;
}


int
FakeZcFilterOcc::
lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const
{
    (void) rhs;
    return 0;
}


class FakeFilterOccZCArrayIterator
    : public queryeval::RankedSearchIteratorBase
{
private:
    FakeFilterOccZCArrayIterator(const FakeFilterOccZCArrayIterator &other);

    FakeFilterOccZCArrayIterator&
    operator=(const FakeFilterOccZCArrayIterator &other);

public:
    // Pointer to compressed data
    const uint8_t *_valI;
    unsigned int _residue;
    uint32_t _lastDocId;

    typedef search::bitcompression::FeatureDecodeContextBE DecodeContext;
    typedef search::bitcompression::FeatureEncodeContextBE EncodeContext;
    DecodeContext _decodeContext;
    uint32_t _docIdLimit;

    FakeFilterOccZCArrayIterator(const uint64_t *compressed,
                                 int bitOffset,
                                 uint32_t docIdLimit,
                                 const fef::TermFieldMatchDataArray &matchData);

    ~FakeFilterOccZCArrayIterator(void);

    void doUnpack(uint32_t docId) override; 
    void doSeek(uint32_t docId) override;
    void initRange(uint32_t begin, uint32_t end) override;
    Trinary is_strict() const override { return Trinary::True; }
};


FakeFilterOccZCArrayIterator::
FakeFilterOccZCArrayIterator(const uint64_t *compressed,
                             int bitOffset,
                             uint32_t docIdLimit,
                             const fef::TermFieldMatchDataArray &matchData)
    : queryeval::RankedSearchIteratorBase(matchData),
      _valI(NULL),
      _residue(0),
      _lastDocId(0),
      _decodeContext(compressed, bitOffset),
      _docIdLimit(docIdLimit)
{
    clearUnpacked();
}

void
FakeFilterOccZCArrayIterator::initRange(uint32_t begin, uint32_t end)
{
    queryeval::RankedSearchIteratorBase::initRange(begin, end);
    DecodeContext &d = _decodeContext;
    typedef EncodeContext EC;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);
    uint32_t length;
    uint64_t val64;

    UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUMDOCS, EC);
    uint32_t numDocs = static_cast<uint32_t>(val64) + 1;

    uint32_t docIdK = EC::calcDocIdK(numDocs, _docIdLimit);

    UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_DOCIDSSIZE, EC);
    uint32_t docIdsSize = val64 + 1;
    UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L1SKIPSIZE, EC);
    uint32_t l1SkipSize = val64;
    uint32_t l2SkipSize = 0;
    if (l1SkipSize != 0) {
        UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L2SKIPSIZE, EC);
        l2SkipSize = val64;
    }
    uint32_t l3SkipSize = 0;
    if (l2SkipSize != 0) {
        UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L3SKIPSIZE, EC);
        l3SkipSize = val64;
    }
    uint32_t l4SkipSize = 0;
    if (l3SkipSize != 0) {
        UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L4SKIPSIZE, EC);
        l4SkipSize = val64;
    }
    // Feature size would be here
    UC64BE_DECODEEXPGOLOMB_NS(o, docIdK, EC);
    _lastDocId = _docIdLimit - 1 - val64;
    UC64_DECODECONTEXT_STORE(o, d._);
    uint64_t bytePad = oPreRead & 7;
    if (bytePad > 0) {
        length = bytePad;
        oVal <<= length;
        UC64BE_READBITS_NS(o, EC);
    }
    UC64_DECODECONTEXT_STORE(o, d._);
    assert((d.getBitOffset() & 7) == 0);
    const uint8_t *bcompr = d.getByteCompr();
    _valI = bcompr;
    bcompr += docIdsSize;
    bcompr += l1SkipSize;
    bcompr += l2SkipSize;
    bcompr += l3SkipSize;
    bcompr += l4SkipSize;
    d.setByteCompr(bcompr);
    uint32_t oDocId;
    ZCDECODE(_valI, oDocId = 1 +);
#if DEBUG_ZCFILTEROCC_PRINTF
    printf("DecodeInit docId=%d\n",
           oDocId);
#endif
    setDocId(oDocId);
    _residue = numDocs;
}


FakeFilterOccZCArrayIterator::
~FakeFilterOccZCArrayIterator(void)
{
}


void
FakeFilterOccZCArrayIterator::doSeek(uint32_t docId)
{
    const uint8_t *oCompr = _valI;
    uint32_t oDocId = getDocId();

    if (getUnpacked())
        clearUnpacked();
    while (oDocId < docId) {
        if (--_residue == 0)
            goto atbreak;
        ZCDECODE(oCompr, oDocId += 1 +);
#if DEBUG_ZCFILTEROCC_PRINTF
        printf("Decode docId=%d\n",
               docId);
#endif
    }
    _valI = oCompr;
    setDocId(oDocId);
    return;
 atbreak:
    _valI = oCompr;
    setAtEnd();                     // Mark end of data
    return;
}


void
FakeFilterOccZCArrayIterator::doUnpack(uint32_t docId)
{
    if (_matchData.size() != 1 || getUnpacked()) {
        return;
    }
    assert(docId == getDocId());
    _matchData[0]->reset(docId);
    setUnpacked();
}


SearchIterator *
FakeZcFilterOcc::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return new FakeFilterOccZCArrayIterator(_compressed.first, 0, _docIdLimit, matchData);
}

template <bool doSkip>
class FakeZcSkipFilterOcc : public FakeZcFilterOcc
{
public:
    FakeZcSkipFilterOcc(const FakeWord &fw);

    ~FakeZcSkipFilterOcc(void);

    virtual SearchIterator *
    createIterator(const TermFieldMatchDataArray &matchData) const override;
};

static FPFactoryInit
initNoSkip(std::make_pair("ZcNoSkipFilterOcc",
                          makeFPFactory<FPFactoryT<FakeZcSkipFilterOcc<false> > >));


static FPFactoryInit
initSkip(std::make_pair("ZcSkipFilterOcc",
                        makeFPFactory<FPFactoryT<FakeZcSkipFilterOcc<true> > >));

template<>
FakeZcSkipFilterOcc<false>::FakeZcSkipFilterOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, true, ".zcnoskipfilterocc")
{
    setup(fw, false, true);
}


template<>
FakeZcSkipFilterOcc<true>::FakeZcSkipFilterOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, true, ".zcskipfilterocc")
{
    setup(fw, false, true);
}


template <bool doSkip>
FakeZcSkipFilterOcc<doSkip>::~FakeZcSkipFilterOcc(void)
{
}


template <bool doSkip>
class FakeFilterOccZCSkipArrayIterator
    : public queryeval::RankedSearchIteratorBase
{
private:

    FakeFilterOccZCSkipArrayIterator(const FakeFilterOccZCSkipArrayIterator &other);

    FakeFilterOccZCSkipArrayIterator&
    operator=(const FakeFilterOccZCSkipArrayIterator &other);

public:
    // Pointer to compressed data
    const uint8_t *_valI;
    uint32_t _lastDocId;
    uint32_t _l1SkipDocId;
    uint32_t _l2SkipDocId;
    uint32_t _l3SkipDocId;
    uint32_t _l4SkipDocId;
    const uint8_t *_l1SkipDocIdPos;
    const uint8_t *_l1SkipValI;
    const uint8_t *_valIBase;
    const uint8_t *_l1SkipValIBase;
    const uint8_t *_l2SkipDocIdPos;
    const uint8_t *_l2SkipValI;
    const uint8_t *_l2SkipL1SkipPos;
    const uint8_t *_l2SkipValIBase;
    const uint8_t *_l3SkipDocIdPos;
    const uint8_t *_l3SkipValI;
    const uint8_t *_l3SkipL1SkipPos;
    const uint8_t *_l3SkipL2SkipPos;
    const uint8_t *_l3SkipValIBase;
    const uint8_t *_l4SkipDocIdPos;
    const uint8_t *_l4SkipValI;
    const uint8_t *_l4SkipL1SkipPos;
    const uint8_t *_l4SkipL2SkipPos;
    const uint8_t *_l4SkipL3SkipPos;

    typedef search::bitcompression::FeatureDecodeContextBE DecodeContext;
    typedef search::bitcompression::FeatureEncodeContextBE EncodeContext;
    DecodeContext _decodeContext;
    uint32_t _docIdLimit;

    FakeFilterOccZCSkipArrayIterator(const uint64_t *compressed,
                                     int bitOffset,
                                     uint32_t docIdLimit,
                                     const TermFieldMatchDataArray &matchData);

    ~FakeFilterOccZCSkipArrayIterator(void);

    void doL4SkipSeek(uint32_t docId);
    void doL3SkipSeek(uint32_t docId);
    void doL2SkipSeek(uint32_t docId);
    void doL1SkipSeek(uint32_t docId);

    void doUnpack(uint32_t docId) override;
    void doSeek(uint32_t docId) override;
    void initRange(uint32_t begin, uint32_t end) override;
    Trinary is_strict() const override { return Trinary::True; }
};


template <bool doSkip>
FakeFilterOccZCSkipArrayIterator<doSkip>::
FakeFilterOccZCSkipArrayIterator(const uint64_t *compressed,
                                 int bitOffset,
                                 uint32_t docIdLimit,
                                 const fef::TermFieldMatchDataArray &matchData)
    : queryeval::RankedSearchIteratorBase(matchData),
      _valI(NULL),
      _lastDocId(0),
      _l1SkipDocId(0),
      _l2SkipDocId(0),
      _l3SkipDocId(0),
      _l4SkipDocId(0),
      _l1SkipDocIdPos(NULL),
      _l1SkipValI(NULL),
      _valIBase(NULL),
      _l1SkipValIBase(NULL),
      _l2SkipDocIdPos(NULL),
      _l2SkipValI(NULL),
      _l2SkipL1SkipPos(NULL),
      _l2SkipValIBase(NULL),
      _l3SkipDocIdPos(NULL),
      _l3SkipValI(NULL),
      _l3SkipL1SkipPos(NULL),
      _l3SkipL2SkipPos(NULL),
      _l3SkipValIBase(NULL),
      _l4SkipDocIdPos(NULL),
      _l4SkipValI(NULL),
      _l4SkipL1SkipPos(NULL),
      _l4SkipL2SkipPos(NULL),
      _l4SkipL3SkipPos(NULL),
      _decodeContext(compressed, bitOffset),
      _docIdLimit(docIdLimit)
{
}

template <bool doSkip>
void
FakeFilterOccZCSkipArrayIterator<doSkip>::
initRange(uint32_t begin, uint32_t end)
{
    queryeval::RankedSearchIteratorBase::initRange(begin, end);
    DecodeContext &d = _decodeContext;
    typedef EncodeContext EC;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);
    uint32_t length;
    uint64_t val64;

    UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUMDOCS, EC);
    uint32_t numDocs = static_cast<uint32_t>(val64) + 1;

    uint32_t docIdK = EC::calcDocIdK(numDocs, _docIdLimit);

    UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_DOCIDSSIZE, EC);
    uint32_t docIdsSize = val64 + 1;
    UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L1SKIPSIZE, EC);
    uint32_t l1SkipSize = val64;
    uint32_t l2SkipSize = 0;
    if (l1SkipSize != 0) {
        UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L2SKIPSIZE, EC);
        l2SkipSize = val64;
    }
    uint32_t l3SkipSize = 0;
    if (l2SkipSize != 0) {
        UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L3SKIPSIZE, EC);
        l3SkipSize = val64;
    }
    uint32_t l4SkipSize = 0;
    if (l3SkipSize != 0) {
        UC64BE_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L4SKIPSIZE, EC);
        l4SkipSize = val64;
    }
    // Feature size would be here
    UC64BE_DECODEEXPGOLOMB_NS(o, docIdK, EC);
    _lastDocId = _docIdLimit - 1 - val64;
    UC64_DECODECONTEXT_STORE(o, d._);
    uint64_t bytePad = oPreRead & 7;
    if (bytePad > 0) {
        length = bytePad;
        oVal <<= length;
        UC64BE_READBITS_NS(o, EC);
    }
    UC64_DECODECONTEXT_STORE(o, d._);
    assert((d.getBitOffset() & 7) == 0);
    const uint8_t *bcompr = d.getByteCompr();
    _valIBase = _valI = bcompr;
    _l1SkipDocIdPos = _l2SkipDocIdPos = bcompr;
    _l3SkipDocIdPos = _l4SkipDocIdPos = bcompr;
    bcompr += docIdsSize;
    if (l1SkipSize != 0) {
        _l1SkipValIBase = _l1SkipValI = bcompr;
        _l2SkipL1SkipPos = _l3SkipL1SkipPos = _l4SkipL1SkipPos = bcompr;
        bcompr += l1SkipSize;
    } else {
        _l1SkipValIBase = _l1SkipValI = NULL;
        _l2SkipL1SkipPos = _l3SkipL1SkipPos = _l4SkipL1SkipPos = NULL;
    }
    if (l2SkipSize != 0) {
        _l2SkipValIBase = _l2SkipValI = bcompr;
        _l3SkipL2SkipPos = _l4SkipL2SkipPos = bcompr;
        bcompr += l2SkipSize;
    } else {
        _l2SkipValIBase = _l2SkipValI = NULL;
        _l3SkipL2SkipPos = _l4SkipL2SkipPos = NULL;
    }
    if (l3SkipSize != 0) {
        _l3SkipValIBase = _l3SkipValI = bcompr;
        _l4SkipL3SkipPos = bcompr;
        bcompr += l3SkipSize;
    } else {
        _l3SkipValIBase = _l3SkipValI = NULL;
        _l4SkipL3SkipPos = NULL;
    }
    if (l4SkipSize != 0) {
        _l4SkipValI = bcompr;
        bcompr += l4SkipSize;
    } else {
        _l4SkipValI = NULL;
    }
    d.setByteCompr(bcompr);
    uint32_t oDocId;
    ZCDECODE(_valI, oDocId = 1 +);
#if DEBUG_ZCFILTEROCC_PRINTF
    printf("DecodeInit docId=%d\n",
           oDocId);
#endif
    setDocId(oDocId);
    if (_l1SkipValI != NULL) {
        ZCDECODE(_l1SkipValI, _l1SkipDocId = 1 +);
    } else
        _l1SkipDocId = _lastDocId;
#if DEBUG_ZCFILTEROCC_PRINTF
    printf("L1DecodeInit docId=%d\n",
           _l1SkipDocId);
#endif
    if (_l2SkipValI != NULL) {
        ZCDECODE(_l2SkipValI, _l2SkipDocId = 1 +);
    } else
        _l2SkipDocId = _lastDocId;
#if DEBUG_ZCFILTEROCC_PRINTF
    printf("L2DecodeInit docId=%d\n",
           _l2SkipDocId);
#endif
    if (_l3SkipValI != NULL) {
        ZCDECODE(_l3SkipValI, _l3SkipDocId = 1 +);
    } else
        _l3SkipDocId = _lastDocId;
#if DEBUG_ZCFILTEROCC_PRINTF
    printf("L3DecodeInit docId=%d\n",
           _l3SkipDocId);
#endif
    if (_l4SkipValI != NULL) {
        ZCDECODE(_l4SkipValI, _l4SkipDocId = 1 +);
    } else
          _l4SkipDocId = _lastDocId;
#if DEBUG_ZCFILTEROCC_PRINTF
    printf("L4DecodeInit docId=%d\n",
           _l4SkipDocId);
#endif
    clearUnpacked();
}


template <bool doSkip>
FakeFilterOccZCSkipArrayIterator<doSkip>::
~FakeFilterOccZCSkipArrayIterator(void)
{
}


template <>
void
FakeFilterOccZCSkipArrayIterator<true>::doL4SkipSeek(uint32_t docId)
{
    uint32_t lastL4SkipDocId;

    if (__builtin_expect(docId > _lastDocId, false)) {
        _l4SkipDocId = _l3SkipDocId = _l2SkipDocId = _l1SkipDocId = search::endDocId;
        setAtEnd();
        return;
    }
    do {
        lastL4SkipDocId = _l4SkipDocId;
        ZCDECODE(_l4SkipValI, _l4SkipDocIdPos += 1 +);
        ZCDECODE(_l4SkipValI, _l4SkipL1SkipPos += 1 + );
        ZCDECODE(_l4SkipValI, _l4SkipL2SkipPos += 1 + );
        ZCDECODE(_l4SkipValI, _l4SkipL3SkipPos += 1 + );
        ZCDECODE(_l4SkipValI, _l4SkipDocId += 1 + );
#if DEBUG_ZCFILTEROCC_PRINTF
        printf("L4Decode docId %d, docIdPos %d,"
               " l1SkipPos %d, l2SkipPos %d, l3SkipPos %d, nextDocId %d\n",
               lastL4SkipDocId,
               (int) (_l4SkipDocIdPos - _valIBase),
               (int) (_l4SkipL1SkipPos - _l1SkipValIBase),
               (int) (_l4SkipL2SkipPos - _l2SkipValIBase),
               (int) (_l4SkipL3SkipPos - _l3SkipValIBase),
               _l4SkipDocId);
#endif
    } while (docId > _l4SkipDocId);
    _valI = _l1SkipDocIdPos = _l2SkipDocIdPos = _l3SkipDocIdPos =
            _l4SkipDocIdPos;
    _l1SkipDocId = _l2SkipDocId = _l3SkipDocId = lastL4SkipDocId;
    _l1SkipValI = _l2SkipL1SkipPos = _l3SkipL1SkipPos = _l4SkipL1SkipPos;
    _l2SkipValI = _l3SkipL2SkipPos = _l4SkipL2SkipPos;
    _l3SkipValI = _l4SkipL3SkipPos;
    ZCDECODE(_valI, lastL4SkipDocId += 1 +);
    ZCDECODE(_l1SkipValI, _l1SkipDocId += 1 +);
    ZCDECODE(_l2SkipValI, _l2SkipDocId += 1 +);
    ZCDECODE(_l3SkipValI, _l3SkipDocId += 1 +);
#if DEBUG_ZCFILTEROCC_PRINTF
    printf("L4Seek, docId %d docIdPos %d"
           " L1SkipPos %d L2SkipPos %d L3SkipPos %d, nextDocId %d\n",
           lastL4SkipDocId,
           (int) (_l4SkipDocIdPos - _valIBase),
           (int) (_l4SkipL1SkipPos - _l1SkipValIBase),
           (int) (_l4SkipL2SkipPos - _l2SkipValIBase),
           (int) (_l4SkipL3SkipPos - _l3SkipValIBase),
           _l4SkipDocId);
#endif
    setDocId(lastL4SkipDocId);
}


template <>
void
FakeFilterOccZCSkipArrayIterator<true>::doL3SkipSeek(uint32_t docId)
{
    uint32_t lastL3SkipDocId;

    if (__builtin_expect(docId > _l4SkipDocId, false)) {
        doL4SkipSeek(docId);
        if (docId <= _l3SkipDocId)
            return;
    }
    do {
        lastL3SkipDocId = _l3SkipDocId;
        ZCDECODE(_l3SkipValI, _l3SkipDocIdPos += 1 +);
        ZCDECODE(_l3SkipValI, _l3SkipL1SkipPos += 1 + );
        ZCDECODE(_l3SkipValI, _l3SkipL2SkipPos += 1 + );
        ZCDECODE(_l3SkipValI, _l3SkipDocId += 1 + );
#if DEBUG_ZCFILTEROCC_PRINTF
        printf("L3Decode docId %d, docIdPos %d,"
               " l1SkipPos %d, l2SkipPos %d, nextDocId %d\n",
               lastL3SkipDocId,
               (int) (_l3SkipDocIdPos - _valIBase),
               (int) (_l3SkipL1SkipPos - _l1SkipValIBase),
               (int) (_l3SkipL2SkipPos - _l2SkipValIBase),
               _l3SkipDocId);
#endif
    } while (docId > _l3SkipDocId);
    _valI = _l1SkipDocIdPos = _l2SkipDocIdPos = _l3SkipDocIdPos;
    _l1SkipDocId = _l2SkipDocId = lastL3SkipDocId;
    _l1SkipValI = _l2SkipL1SkipPos = _l3SkipL1SkipPos;
    _l2SkipValI = _l3SkipL2SkipPos;
    ZCDECODE(_valI, lastL3SkipDocId += 1 +);
    ZCDECODE(_l1SkipValI, _l1SkipDocId += 1 +);
    ZCDECODE(_l2SkipValI, _l2SkipDocId += 1 +);
#if DEBUG_ZCFILTEROCC_PRINTF
    printf("L3Seek, docId %d docIdPos %d"
           " L1SkipPos %d L2SkipPos %d, nextDocId %d\n",
           lastL3SkipDocId,
           (int) (_l3SkipDocIdPos - _valIBase),
           (int) (_l3SkipL1SkipPos - _l1SkipValIBase),
           (int) (_l3SkipL2SkipPos - _l2SkipValIBase),
           _l3SkipDocId);
#endif
    setDocId(lastL3SkipDocId);
}


template <>
void
FakeFilterOccZCSkipArrayIterator<true>::doL2SkipSeek(uint32_t docId)
{
    uint32_t lastL2SkipDocId;

    if (__builtin_expect(docId > _l3SkipDocId, false)) {
        doL3SkipSeek(docId);
        if (docId <= _l2SkipDocId)
            return;
    }
    do {
        lastL2SkipDocId = _l2SkipDocId;
        ZCDECODE(_l2SkipValI, _l2SkipDocIdPos += 1 +);
        ZCDECODE(_l2SkipValI, _l2SkipL1SkipPos += 1 + );
        ZCDECODE(_l2SkipValI, _l2SkipDocId += 1 + );
#if DEBUG_ZCFILTEROCC_PRINTF
        printf("L2Decode docId %d, docIdPos %d, l1SkipPos %d, nextDocId %d\n",
               lastL2SkipDocId,
               (int) (_l2SkipDocIdPos - _valIBase),
               (int) (_l2SkipL1SkipPos - _l1SkipValIBase),
               _l2SkipDocId);
#endif
    } while (docId > _l2SkipDocId);
    _valI = _l1SkipDocIdPos = _l2SkipDocIdPos;
    _l1SkipDocId = lastL2SkipDocId;
    _l1SkipValI = _l2SkipL1SkipPos;
    ZCDECODE(_valI, lastL2SkipDocId += 1 +);
    ZCDECODE(_l1SkipValI, _l1SkipDocId += 1 +);
#if DEBUG_ZCFILTEROCC_PRINTF
    printf("L2Seek, docId %d docIdPos %d L1SkipPos %d, nextDocId %d\n",
           lastL2SkipDocId,
           (int) (_l2SkipDocIdPos - _valIBase),
           (int) (_l2SkipL1SkipPos - _l1SkipValIBase),
           _l2SkipDocId);
#endif
    setDocId(lastL2SkipDocId);
}


template <>
void
FakeFilterOccZCSkipArrayIterator<false>::doL1SkipSeek(uint32_t docId)
{
    (void) docId;
}


template <>
void
FakeFilterOccZCSkipArrayIterator<true>::doL1SkipSeek(uint32_t docId)
{
    uint32_t lastL1SkipDocId;
    if (__builtin_expect(docId > _l2SkipDocId, false)) {
        doL2SkipSeek(docId);
        if (docId <= _l1SkipDocId)
            return;
    }
    do {
        lastL1SkipDocId = _l1SkipDocId;
        ZCDECODE(_l1SkipValI, _l1SkipDocIdPos += 1 +);
        ZCDECODE(_l1SkipValI, _l1SkipDocId += 1 +);
#if DEBUG_ZCFILTEROCC_PRINTF
        printf("L1Decode docId %d, docIdPos %d, L1SkipPos %d, nextDocId %d\n",
               lastL1SkipDocId,
               (int) (_l1SkipDocIdPos - _valIBase),
               (int) (_l1SkipValI - _l1SkipValIBase),
                _l1SkipDocId);
#endif
    } while (docId > _l1SkipDocId);
    _valI = _l1SkipDocIdPos;
    ZCDECODE(_valI, lastL1SkipDocId += 1 +);
    setDocId(lastL1SkipDocId);
#if DEBUG_ZCFILTEROCC_PRINTF
    printf("L1SkipSeek, docId %d docIdPos %d, nextDocId %d\n",
           lastL1SkipDocId,
           (int) (_l1SkipDocIdPos - _valIBase),
           _l1SkipDocId);
#endif
}


template <bool doSkip>
void
FakeFilterOccZCSkipArrayIterator<doSkip>::doSeek(uint32_t docId)
{
    if (getUnpacked())
        clearUnpacked();
    if (doSkip && docId > _l1SkipDocId) {
        doL1SkipSeek(docId);
    }
    uint32_t oDocId = getDocId();
    if (doSkip) {
#if DEBUG_ZCFILTEROCC_ASSERT
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
    const uint8_t *oCompr = _valI;
    while (__builtin_expect(oDocId < docId, true)) {
        if (!doSkip) {
            if (__builtin_expect(oDocId >= _lastDocId, false)) {
#if DEBUG_ZCFILTEROCC_ASSERT
                assert(_l1SkipDocId == _lastDocId);
                assert(_l2SkipDocId == _lastDocId);
                assert(_l3SkipDocId == _lastDocId);
                assert(_l4SkipDocId == _lastDocId);
#endif
                oDocId = _l1SkipDocId = _l2SkipDocId = _l3SkipDocId =
                         _l4SkipDocId = search::endDocId;
                break;
            }
        }
        if (doSkip) {
#if DEBUG_ZCFILTEROCC_ASSERT
            assert(oDocId <= _l1SkipDocId);
            assert(oDocId <= _l2SkipDocId);
            assert(oDocId <= _l3SkipDocId);
            assert(oDocId <= _l4SkipDocId);
#endif
        } else if (__builtin_expect(oDocId >= _l1SkipDocId, false)) {
            // Validate L1 Skip information
            assert(oDocId == _l1SkipDocId);
            ZCDECODE(_l1SkipValI, _l1SkipDocIdPos += 1 +);
            assert(oCompr == _l1SkipDocIdPos);
            if (__builtin_expect(oDocId >= _l2SkipDocId, false)) {
                // Validate L2 Skip information
                assert(oDocId == _l2SkipDocId);
                ZCDECODE(_l2SkipValI, _l2SkipDocIdPos += 1 +);
                ZCDECODE(_l2SkipValI, _l2SkipL1SkipPos += 1 +);
                assert(oCompr = _l2SkipDocIdPos);
                assert(_l1SkipValI == _l2SkipL1SkipPos);
                if (__builtin_expect(oDocId >= _l3SkipDocId, false)) {
                    // Validate L3 Skip information
                    assert(oDocId == _l3SkipDocId);
                    ZCDECODE(_l3SkipValI, _l3SkipDocIdPos += 1 +);
                    ZCDECODE(_l3SkipValI, _l3SkipL1SkipPos += 1 +);
                    ZCDECODE(_l3SkipValI, _l3SkipL2SkipPos += 1 +);
                    assert(oCompr = _l3SkipDocIdPos);
                    assert(_l1SkipValI == _l3SkipL1SkipPos);
                    assert(_l2SkipValI == _l3SkipL2SkipPos);
                    if (__builtin_expect(oDocId >= _l4SkipDocId, false)) {
                        // Validate L4 Skip information
                        assert(oDocId == _l4SkipDocId);
                        ZCDECODE(_l4SkipValI, _l4SkipDocIdPos += 1 +);
                        ZCDECODE(_l4SkipValI, _l4SkipL1SkipPos += 1 +);
                        ZCDECODE(_l4SkipValI, _l4SkipL2SkipPos += 1 +);
                        ZCDECODE(_l4SkipValI, _l4SkipL3SkipPos += 1 +);
                        assert(oCompr = _l4SkipDocIdPos);
                        assert(_l1SkipValI == _l4SkipL1SkipPos);
                        assert(_l2SkipValI == _l4SkipL2SkipPos);
                        assert(_l3SkipValI == _l4SkipL3SkipPos);
                        ZCDECODE(_l4SkipValI, _l4SkipDocId += 1 +);
                        assert(_l4SkipDocId <= _lastDocId);
#if DEBUG_ZCFILTEROCC_PRINTF
                        printf("L4DecodeV docId=%d docIdPos=%d"
                               " L1SkipPos=%d L2SkipPos %d L3SkipPos %d\n",
                               _l4SkipDocId,
                               (int) (_l4SkipDocIdPos - _valIBase),
                               (int) (_l4SkipL1SkipPos - _l1SkipValIBase),
                               (int) (_l4SkipL2SkipPos - _l2SkipValIBase),
                               (int) (_l4SkipL3SkipPos - _l3SkipValIBase));
#endif
                    }
                    ZCDECODE(_l3SkipValI, _l3SkipDocId += 1 +);
                    assert(_l3SkipDocId <= _lastDocId);
                    assert(_l3SkipDocId <= _l4SkipDocId);
#if DEBUG_ZCFILTEROCC_PRINTF
                    printf("L3DecodeV docId=%d docIdPos=%d"
                           " L1SkipPos=%d L2SkipPos %d\n",
                           _l3SkipDocId,
                           (int) (_l3SkipDocIdPos - _valIBase),
                           (int) (_l3SkipL1SkipPos - _l1SkipValIBase),
                           (int) (_l3SkipL2SkipPos - _l2SkipValIBase));
#endif
                }
                ZCDECODE(_l2SkipValI, _l2SkipDocId += 1 +);
                assert(_l2SkipDocId <= _lastDocId);
                assert(_l2SkipDocId <= _l4SkipDocId);
                assert(_l2SkipDocId <= _l3SkipDocId);
#if DEBUG_ZCFILTEROCC_PRINTF
                printf("L2DecodeV docId=%d docIdPos=%d L1SkipPos=%d\n",
                       _l2SkipDocId,
                       (int) (_l2SkipDocIdPos - _valIBase),
                       (int) (_l2SkipL1SkipPos - _l1SkipValIBase));
#endif
            }
            ZCDECODE(_l1SkipValI, _l1SkipDocId += 1 +);
            assert(_l1SkipDocId <= _lastDocId);
            assert(_l1SkipDocId <= _l4SkipDocId);
            assert(_l1SkipDocId <= _l3SkipDocId);
            assert(_l1SkipDocId <= _l2SkipDocId);
#if DEBUG_ZCFILTEROCC_PRINTF
            printf("L1DecodeV docId=%d, docIdPos=%d\n",
                   _l1SkipDocId,
                   (int) (_l1SkipDocIdPos - _valIBase));
#endif
        }
        ZCDECODE(oCompr, oDocId += 1 +);
#if DEBUG_ZCFILTEROCC_PRINTF
        printf("Decode docId=%d\n",
               oDocId);
#endif
    }
    _valI = oCompr;
    setDocId(oDocId);
    return;
}


template <bool doSkip>
void
FakeFilterOccZCSkipArrayIterator<doSkip>::doUnpack(uint32_t docId)
{
    if (_matchData.size() != 1 || getUnpacked()) {
        return;
    }
    assert(docId == getDocId());
    _matchData[0]->reset(docId);
    setUnpacked();
}


template <bool doSkip>
SearchIterator *
FakeZcSkipFilterOcc<doSkip>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return new FakeFilterOccZCSkipArrayIterator<doSkip>(_compressed.first,
            0,
            _docIdLimit,
            matchData);
}


template <bool bigEndian>
class FakeEGCompr64PosOcc : public FakeZcFilterOcc
{
public:
    FakeEGCompr64PosOcc(const FakeWord &fw);

    ~FakeEGCompr64PosOcc(void);

    void setup(const FakeWord &fw);

    size_t bitSize(void) const override;

    virtual bool hasWordPositions(void) const override;

    virtual SearchIterator *
    createIterator(const TermFieldMatchDataArray &matchData) const override;
};


template <bool bigEndian>
FakeEGCompr64PosOcc<bigEndian>::FakeEGCompr64PosOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, bigEndian,
                      bigEndian ? ".zcposoccbe" : ".zcposoccle")
{
    setup(fw);
}


template <bool bigEndian>
FakeEGCompr64PosOcc<bigEndian>::~FakeEGCompr64PosOcc(void)
{
}


template <bool bigEndian>
void
FakeEGCompr64PosOcc<bigEndian>::setup(const FakeWord &fw)
{
    uint32_t lastDocId = 0u;

    typedef FakeWord FW;
    typedef FW::DocWordFeatureList DWFL;
    typedef FW::DocWordPosFeatureList DWPFL;

    DWFL::const_iterator d(fw._postings.begin());
    DWFL::const_iterator de(fw._postings.end());
    DWPFL::const_iterator p(fw._wordPosFeatures.begin());
    DWPFL::const_iterator pe(fw._wordPosFeatures.end());
    DocIdAndPosOccFeatures features;
    EGPosOccEncodeContext<bigEndian> e(&_fieldsParams);
    ComprFileWriteContext ectx(e);
    e.setWriteContext(&ectx);
    ectx.allocComprBuf(64, 1);
    e.afterWrite(ectx, 0, 0);

    _hitDocs = fw._postings.size();
    _docIdLimit = fw._docIdLimit;
    if (_hitDocs > 0)
        _lastDocId = fw._postings.back()._docId;
    else
        _lastDocId = 0u;
    e.encodeExpGolomb(_hitDocs - 1, K_VALUE_ZCPOSTING_NUMDOCS);

    uint32_t docIdK = e.calcDocIdK(_hitDocs, _docIdLimit);

    while (d != de) {
        e.encodeExpGolomb(d->_docId - lastDocId - 1, docIdK);
#if DEBUG_ZCFILTEROCC_PRINTF
        printf("Encode docId=%d (+%u + 1)\n",
               d->_docId, d->_docId - lastDocId - 1);
#endif
        fw.setupFeatures(*d, &*p, features);
        p += d->_positions;
        e.writeFeatures(features);
        lastDocId = d->_docId;
        ++d;
    }
    assert(p == pe);

    _compressedBits = e.getWriteOffset();

    // First pad to 64 bits.
    uint32_t pad = (64 - e.getWriteOffset()) & 63;
    while (pad > 0) {
        uint32_t now = std::min(32u, pad);
        e.writeBits(0, now);
        e.writeComprBufferIfNeeded();
        pad -= now;
    }

    // Then write 128 more bits.  This allows for 64-bit decoding
    // with a readbits that always leaves a nonzero preRead
    for (unsigned int i = 0; i < 4; i++) {
        e.writeBits(0, 32);
        e.writeComprBufferIfNeeded();
    }
    e.writeComprBufferIfNeeded();
    e.flush();
    e.writeComprBuffer();

    std::pair<void *, size_t> ectxData =
        ectx.grabComprBuffer(_compressedMalloc);
    _compressed = std::make_pair(static_cast<uint64_t *>(ectxData.first),
                                 ectxData.second);
}


template <bool bigEndian>
size_t
FakeEGCompr64PosOcc<bigEndian>::bitSize(void) const
{
    return _compressedBits;
}


template <bool bigEndian>
bool
FakeEGCompr64PosOcc<bigEndian>::hasWordPositions(void) const
{
    return true;
}


template <bool bigEndian>
SearchIterator *
FakeEGCompr64PosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return new ZcRareWordPosOccIterator<bigEndian>(Position(_compressed.first, 0),
                                                   _compressedBits, _docIdLimit, &_fieldsParams, matchData);
}


template <bool bigEndian>
class FakeEG2Compr64PosOcc : public FakeZcFilterOcc
{
public:
    FakeEG2Compr64PosOcc(const FakeWord &fw);

    ~FakeEG2Compr64PosOcc(void);

    void setup(const FakeWord &fw);

    size_t bitSize(void) const override;

    virtual bool hasWordPositions(void) const override;

    virtual SearchIterator *
    createIterator(const fef::TermFieldMatchDataArray &matchData) const override;
};


template <bool bigEndian>
FakeEG2Compr64PosOcc<bigEndian>::FakeEG2Compr64PosOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, bigEndian,
                      bigEndian ? ".zc2posoccbe" : ".zc2posoccle")
{
    setup(fw);
}


template <bool bigEndian>
FakeEG2Compr64PosOcc<bigEndian>::~FakeEG2Compr64PosOcc(void)
{
}


template <bool bigEndian>
void
FakeEG2Compr64PosOcc<bigEndian>::setup(const FakeWord &fw)
{
    uint32_t lastDocId = 0u;

    typedef FakeWord FW;
    typedef FW::DocWordFeatureList DWFL;
    typedef FW::DocWordPosFeatureList DWPFL;

    DWFL::const_iterator d(fw._postings.begin());
    DWFL::const_iterator de(fw._postings.end());
    DWPFL::const_iterator p(fw._wordPosFeatures.begin());
    DWPFL::const_iterator pe(fw._wordPosFeatures.end());
    DocIdAndPosOccFeatures features;
    EG2PosOccEncodeContext<bigEndian> e(&_fieldsParams);
    ComprFileWriteContext ectx(e);
    e.setWriteContext(&ectx);
    ectx.allocComprBuf(64, 1);
    e.afterWrite(ectx, 0, 0);

    _hitDocs = fw._postings.size();
    _docIdLimit = fw._docIdLimit;
    if (_hitDocs > 0)
        _lastDocId = fw._postings.back()._docId;
    else
        _lastDocId = 0u;
    e.encodeExpGolomb(_hitDocs - 1, K_VALUE_ZCPOSTING_NUMDOCS);

    while (d != de) {
        e.encodeExpGolomb(d->_docId - lastDocId - 1,
                          K_VALUE_ZCPOSTING_DELTA_DOCID);
#if DEBUG_ZCFILTEROCC_PRINTF
        printf("Encode docId=%d (+%u + 1)\n",
               d->_docId, d->_docId - lastDocId - 1);
#endif
        fw.setupFeatures(*d, &*p, features);
        p += d->_positions;
        e.writeFeatures(features);
        lastDocId = d->_docId;
        ++d;
    }
    assert(p == pe);

    _compressedBits = e.getWriteOffset();

    // First pad to 64 bits.
    uint32_t pad = (64 - e.getWriteOffset()) & 63;
    while (pad > 0) {
        uint32_t now = std::min(32u, pad);
        e.writeBits(0, now);
        e.writeComprBufferIfNeeded();
        pad -= now;
    }

    // Then write 128 more bits.  This allows for 64-bit decoding
    // with a readbits that always leaves a nonzero preRead
    for (unsigned int i = 0; i < 4; i++) {
        e.writeBits(0, 32);
        e.writeComprBufferIfNeeded();
    }
    e.writeComprBufferIfNeeded();
    e.flush();
    e.writeComprBuffer();

    std::pair<void *, size_t> ectxData =
        ectx.grabComprBuffer(_compressedMalloc);
    _compressed = std::make_pair(static_cast<uint64_t *>(ectxData.first),
                                 ectxData.second);
}


template <bool bigEndian>
size_t
FakeEG2Compr64PosOcc<bigEndian>::bitSize(void) const
{
    return _compressedBits;
}


template <bool bigEndian>
bool
FakeEG2Compr64PosOcc<bigEndian>::hasWordPositions(void) const
{
    return true;
}


template <bool bigEndian>
SearchIterator *
FakeEG2Compr64PosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return new Zc4RareWordPosOccIterator<bigEndian>(Position(_compressed.first, 0),
                                                    _compressedBits, _docIdLimit, &_fieldsParams, matchData);
}


template <bool bigEndian>
class FakeZcSkipPosOcc : public FakeZcFilterOcc
{
    search::index::PostingListCounts _counts;
public:
    FakeZcSkipPosOcc(const FakeWord &fw);

    ~FakeZcSkipPosOcc(void);

    size_t bitSize(void) const override;

    virtual bool hasWordPositions(void) const override;

    virtual SearchIterator *
    createIterator(const TermFieldMatchDataArray &matchData) const override;
};


template <bool bigEndian>
FakeZcSkipPosOcc<bigEndian>::FakeZcSkipPosOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, bigEndian,
                      bigEndian ? ".zcskipposoccbe" : ".zcskipposoccle")
{
    setup(fw, true, true);
    _counts._bitLength = _compressedBits;
}


template <bool bigEndian>
FakeZcSkipPosOcc<bigEndian>::~FakeZcSkipPosOcc(void)
{
}


template <bool bigEndian>
size_t
FakeZcSkipPosOcc<bigEndian>::bitSize(void) const
{
    return _compressedBits -
        _l1SkipSize - _l2SkipSize - _l3SkipSize - _l4SkipSize;
}


template <bool bigEndian>
bool
FakeZcSkipPosOcc<bigEndian>::hasWordPositions(void) const
{
    return true;
}


template <bool bigEndian>
SearchIterator *
FakeZcSkipPosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return new ZcPosOccIterator<bigEndian>(Position(_compressed.first, 0), _compressedBits, _docIdLimit,
                                           static_cast<uint32_t>(-1),
                                           _counts,
                                           &_fieldsParams,
                                           matchData);
}


template <bool bigEndian>
class FakeZc2SkipPosOcc : public FakeZcFilterOcc
{
    search::index::PostingListCounts _counts;
public:
    FakeZc2SkipPosOcc(const FakeWord &fw);

    ~FakeZc2SkipPosOcc(void);

    size_t bitSize(void) const override;

    virtual bool hasWordPositions(void) const override;

    virtual SearchIterator *
    createIterator(const TermFieldMatchDataArray &matchData) const override;
};


template <bool bigEndian>
FakeZc2SkipPosOcc<bigEndian>::FakeZc2SkipPosOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, bigEndian,
                      bigEndian ? ".zc2skipposoccbe" : ".zc2skipposoccle")
{
    setup(fw, true, false);
    _counts._bitLength = _compressedBits;
}


template <bool bigEndian>
FakeZc2SkipPosOcc<bigEndian>::~FakeZc2SkipPosOcc(void)
{
}


template <bool bigEndian>
size_t
FakeZc2SkipPosOcc<bigEndian>::bitSize(void) const
{
    return _compressedBits -
        _l1SkipSize - _l2SkipSize - _l3SkipSize - _l4SkipSize;
}


template <bool bigEndian>
bool
FakeZc2SkipPosOcc<bigEndian>::hasWordPositions(void) const
{
    return true;
}


template <bool bigEndian>
SearchIterator *
FakeZc2SkipPosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return new Zc4PosOccIterator<bigEndian>(Position(_compressed.first, 0), _compressedBits, _docIdLimit,
                                            static_cast<uint32_t>(-1), _counts, &_fieldsParams, matchData);
}


static FPFactoryInit
initPosbe(std::make_pair("EGCompr64PosOccBE",
                         makeFPFactory<FPFactoryT<FakeEGCompr64PosOcc<true> > >));

static FPFactoryInit
initPosle(std::make_pair("EGCompr64PosOccLE",
                         makeFPFactory<FPFactoryT<FakeEGCompr64PosOcc<false> > >));


static FPFactoryInit
initPos0be(std::make_pair("EG2Compr64PosOccBE",
                          makeFPFactory<FPFactoryT<FakeEG2Compr64PosOcc<true> > >));


static FPFactoryInit
initPos0le(std::make_pair("EG2Compr64PosOccLE",
                          makeFPFactory<FPFactoryT<FakeEG2Compr64PosOcc<false> > >));


static FPFactoryInit
initSkipPosbe(std::make_pair("ZcSkipPosOccBE",
                             makeFPFactory<FPFactoryT<FakeZcSkipPosOcc<true> > >));


static FPFactoryInit
initSkipPosle(std::make_pair("ZcSkipPosOccLE",
                             makeFPFactory<FPFactoryT<FakeZcSkipPosOcc<false> > >));


static FPFactoryInit
initSkipPos0be(std::make_pair("Zc2SkipPosOccBE",
                              makeFPFactory<FPFactoryT<FakeZc2SkipPosOcc<true> > >));


static FPFactoryInit
initSkipPos0le(std::make_pair("Zc2SkipPosOccLE",
                              makeFPFactory<FPFactoryT<FakeZc2SkipPosOcc<false> > >));


} // namespace fakedata

} // namespace search

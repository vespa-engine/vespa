// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fakezcfilterocc.h"
#include "fpfactory.h"
#include <vespa/searchlib/diskindex/zcposocciterators.h>
#include <vespa/searchlib/diskindex/zc4_posting_header.h>
#include <vespa/searchlib/diskindex/zc4_posting_params.h>
#include <vespa/searchlib/diskindex/zc4_posting_reader.h>
#include <vespa/searchlib/diskindex/zc4_posting_writer.h>

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::fef::TermFieldMatchDataPosition;
using search::queryeval::SearchIterator;
using search::index::PostingListCounts;
using search::index::PostingListParams;
using search::index::DocIdAndFeatures;
using search::index::DocIdAndPosOccFeatures;
using search::bitcompression::DecodeContext64;
using search::bitcompression::DecodeContext64Base;
using search::bitcompression::PosOccFieldParams;
using search::bitcompression::EGPosOccEncodeContext;
using search::bitcompression::EG2PosOccEncodeContext;
using search::bitcompression::FeatureEncodeContext;
using search::ComprFileWriteContext;
using namespace search::diskindex;

namespace search {

namespace fakedata {

namespace {

constexpr uint32_t disable_chunking = 1000000000;
constexpr uint32_t disable_skip = 1000000000;
constexpr uint32_t force_skip = 1;

}

#define DEBUG_ZCFILTEROCC_PRINTF 0
#define DEBUG_ZCFILTEROCC_ASSERT 0

static FPFactoryInit
init(std::make_pair("ZcFilterOcc",
                    makeFPFactory<FPFactoryT<FakeZcFilterOcc> >));


template <typename EC>
static void
writeZcBuf(EC &e, ZcBuf &buf)
{
    uint32_t size = buf.size();
    uint8_t *bytes = buf._mallocStart;
    uint32_t bytesOffset = reinterpret_cast<unsigned long>(bytes) & 7;
    e.writeBits(reinterpret_cast<const uint64_t *>(bytes - bytesOffset),
                bytesOffset * 8, size * 8);
}

#define ZCDECODE(valI, resop)                                \
do {                                                         \
    if (__builtin_expect(valI[0] < (1 << 7), true)) {        \
    resop valI[0];                                           \
    valI += 1;                                               \
    } else if (__builtin_expect(valI[1] < (1 << 7), true)) { \
        resop (valI[0] & ((1 << 7) - 1)) +                   \
              (valI[1] << 7);                                \
        valI += 2;                                           \
    } else if (__builtin_expect(valI[2] < (1 << 7), true)) { \
        resop (valI[0] & ((1 << 7) - 1)) +                   \
              ((valI[1] & ((1 << 7) - 1)) << 7) +            \
              (valI[2] << 14);                               \
        valI += 3;                                           \
    } else if (__builtin_expect(valI[3] < (1 << 7), true)) { \
        resop (valI[0] & ((1 << 7) - 1)) +                   \
              ((valI[1] & ((1 << 7) - 1)) << 7) +            \
              ((valI[2] & ((1 << 7) - 1)) << 14) +           \
              (valI[3] << 21);                               \
        valI += 4;                                           \
    } else {                                                 \
        resop (valI[0] & ((1 << 7) - 1)) +                   \
              ((valI[1] & ((1 << 7) - 1)) << 7) +            \
              ((valI[2] & ((1 << 7) - 1)) << 14) +           \
              ((valI[3] & ((1 << 7) - 1)) << 21) +           \
              (valI[4] << 28);                               \
        valI += 5;                                           \
    }                                                        \
} while (0)

FakeZcFilterOcc::FakeZcFilterOcc(const FakeWord &fw)
    : FakePosting(fw.getName() + ".zcfilterocc"),
      _docIdsSize(0),
      _l1SkipSize(0),
      _l2SkipSize(0),
      _l3SkipSize(0),
      _l4SkipSize(0),
      _hitDocs(0),
      _lastDocId(0u),
      _compressedBits(0),
      _compressed(std::make_pair(static_cast<uint64_t *>(NULL), 0)),
      _compressedMalloc(NULL),
      _featuresSize(0),
      _fieldsParams(fw.getFieldsParams()),
      _bigEndian(true),
      _posting_params(force_skip, disable_chunking, fw._docIdLimit, true, false, false)
{
    setup(fw);
}


FakeZcFilterOcc::FakeZcFilterOcc(const FakeWord &fw,
                                 bool bigEndian,
                                 const diskindex::Zc4PostingParams &posting_params,
                                 const char *nameSuffix)
    : FakePosting(fw.getName() + nameSuffix),
      _docIdsSize(0),
      _l1SkipSize(0),
      _l2SkipSize(0),
      _l3SkipSize(0),
      _l4SkipSize(0),
      _hitDocs(0),
      _lastDocId(0u),
      _compressedBits(0),
      _compressed(std::make_pair(static_cast<uint64_t *>(NULL), 0)),
      _featuresSize(0),
      _fieldsParams(fw.getFieldsParams()),
      _bigEndian(bigEndian),
      _posting_params(posting_params)
{
    // subclass responsible for calling setup(fw);
}


void
FakeZcFilterOcc::setup(const FakeWord &fw)
{
    if (_bigEndian) {
        setupT<true>(fw);
    } else {
        setupT<false>(fw);
    }
    validate_read(fw);
}


template <bool bigEndian>
void
FakeZcFilterOcc::setupT(const FakeWord &fw)
{
    PostingListCounts counts;
    Zc4PostingWriter<bigEndian> writer(counts);

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
    FeatureEncodeContext<bigEndian> &f = (_posting_params._dynamic_k ?
            static_cast<FeatureEncodeContext<bigEndian> &>(f1) :
            static_cast<FeatureEncodeContext<bigEndian> &>(f0));

    writer.set_dynamic_k(_posting_params._dynamic_k);
    if (_posting_params._encode_features) {
        writer.set_encode_features(&f);
    }
    PostingListParams params;
    params.set("docIdLimit", fw._docIdLimit);
    params.set("minChunkDocs", _posting_params._min_chunk_docs); // Control chunking
    params.set("minSkipDocs", _posting_params._min_skip_docs);   // Control skip info
    params.set("cheap_features", _posting_params._encode_cheap_features);
    writer.set_posting_list_params(params);
    auto &writeContext = writer.get_write_context();
    search::ComprBuffer &cb = writeContext;
    auto &e = writer.get_encode_context();
    writeContext.allocComprBuf(65536u, 32768u);
    e.setupWrite(cb);
    // Ensure that some space is initially available in encoding buffers
    while (d != de) {
        if (_posting_params._encode_features) {
            fw.setupFeatures(*d, &*p, features);
            p += d->_positions;
        } else {
            features.clear(d->_docId);
        }
        writer.write_docid_and_features(features);
        ++d;
    }
    if (_posting_params._encode_features) {
        assert(p == pe);
    }
    writer.flush_word();
    _featuresSize = 0;
    _hitDocs = fw._postings.size();
    _compressedBits = e.getWriteOffset();
    assert(_compressedBits == counts._bitLength);
    assert(_hitDocs == counts._numDocs);
    _lastDocId = fw._postings.back()._docId;
    writer.on_close();

    std::pair<void *, size_t> ectxData = writeContext.grabComprBuffer(_compressedMalloc);
    _compressed = std::make_pair(static_cast<uint64_t *>(ectxData.first),
                                 ectxData.second);
    read_header<bigEndian>();
}

template <bool bigEndian>
void
FakeZcFilterOcc::read_header()
{
    // read back word header to get skip sizes
    DecodeContext64<bigEndian> decode_context;
    decode_context.setPosition({ _compressed.first, 0 });
    Zc4PostingHeader header;
    header.read(decode_context, _posting_params);
    _docIdsSize = header._doc_ids_size;
    _l1SkipSize = header._l1_skip_size;
    _l2SkipSize = header._l2_skip_size;
    _l3SkipSize = header._l3_skip_size;
    _l4SkipSize = header._l4_skip_size;
    _featuresSize = header._features_size;
    assert(header._num_docs == _hitDocs);
    if (header._num_docs >= _posting_params._min_skip_docs) {
        assert(_lastDocId == header._last_doc_id);
    } else {
        assert(header._last_doc_id == 0);
    }
}


void
FakeZcFilterOcc::validate_read(const FakeWord &fw) const
{
    if (_bigEndian) {
        validate_read<true>(fw);
    } else {
        validate_read<false>(fw);
    }
}

template <bool bigEndian>
void
FakeZcFilterOcc::validate_read(const FakeWord &fw) const
{
    bitcompression::EGPosOccDecodeContextCooked<bigEndian> decode_context_dynamic_k(&_fieldsParams);
    bitcompression::EG2PosOccDecodeContextCooked<bigEndian> decode_context_static_k(&_fieldsParams);
    bitcompression::FeatureDecodeContext<bigEndian> &decode_context_dynamic_k_upcast = decode_context_dynamic_k;
    bitcompression::FeatureDecodeContext<bigEndian> &decode_context_static_k_upcast = decode_context_static_k;
    bitcompression::FeatureDecodeContext<bigEndian> &decode_context = _posting_params._dynamic_k ? decode_context_dynamic_k_upcast : decode_context_static_k_upcast;
    Zc4PostingReader<bigEndian> reader(_posting_params._dynamic_k);
    reader.set_decode_features(&decode_context);
    auto &params = reader.get_posting_params();
    params = _posting_params;
    reader.get_read_context().reference_compressed_buffer(_compressed.first, _compressed.second);
    assert(decode_context.getReadOffset() == 0u);
    PostingListCounts counts;
    counts._bitLength = _compressedBits;
    counts._numDocs = _hitDocs;
    reader.set_counts(counts);
    auto word_pos_iterator(fw._wordPosFeatures.begin());
    auto word_pos_iterator_end(fw._wordPosFeatures.end());
    DocIdAndPosOccFeatures check_features;
    DocIdAndFeatures features;
    uint32_t hits = 0;
    for (const auto &doc : fw._postings) {
        if (_posting_params._encode_features) {
            fw.setupFeatures(doc, &*word_pos_iterator, check_features);
            word_pos_iterator += doc._positions;
        } else {
            check_features.clear(doc._docId);
        }
        reader.read_doc_id_and_features(features);
        assert(features.doc_id() == doc._docId);
        assert(features.elements().size() == check_features.elements().size());
        assert(features.word_positions().size() == check_features.word_positions().size());
        if (_posting_params._encode_cheap_features) {
            assert(features.field_length() == doc._collapsedDocWordFeatures._field_len);
            assert(features.num_occs() == doc._collapsedDocWordFeatures._num_occs);
        }
        ++hits;
    }
    if (_posting_params._encode_features) {
        assert(word_pos_iterator == word_pos_iterator_end);
    }
    reader.read_doc_id_and_features(features);
    assert(static_cast<int32_t>(features.doc_id()) == -1);
}

FakeZcFilterOcc::~FakeZcFilterOcc()
{
    free(_compressedMalloc);
}


void
FakeZcFilterOcc::forceLink()
{
}


size_t
FakeZcFilterOcc::bitSize() const
{
    return _compressedBits -
        (_l1SkipSize + _l2SkipSize + _l3SkipSize + _l4SkipSize) * 8;
}


bool
FakeZcFilterOcc::hasWordPositions() const
{
    return false;
}


size_t
FakeZcFilterOcc::skipBitSize() const
{
    return (_l1SkipSize + _l2SkipSize + _l3SkipSize + _l4SkipSize) * 8;
}


size_t
FakeZcFilterOcc::l1SkipBitSize() const
{
    return _l1SkipSize * 8;
}


size_t
FakeZcFilterOcc::l2SkipBitSize() const
{
    return _l2SkipSize * 8;
}


size_t
FakeZcFilterOcc::l3SkipBitSize() const
{
    return _l3SkipSize * 8;
}


size_t
FakeZcFilterOcc::l4SkipBitSize() const
{
    return _l4SkipSize * 8;
}


int
FakeZcFilterOcc::lowLevelSinglePostingScan() const
{
    return 0;
}


int
FakeZcFilterOcc::lowLevelSinglePostingScanUnpack() const
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

    ~FakeFilterOccZCArrayIterator() override;

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
    Zc4PostingParams params(force_skip, disable_chunking, _docIdLimit, true, false, false);
    Zc4PostingHeader header;
    header.read(d, params);
    assert((d.getBitOffset() & 7) == 0);
    const uint8_t *bcompr = d.getByteCompr();
    _valI = bcompr;
    bcompr += header._doc_ids_size;
    bcompr += header._l1_skip_size;
    bcompr += header._l2_skip_size;
    bcompr += header._l3_skip_size;
    bcompr += header._l4_skip_size,
    d.setByteCompr(bcompr);
    uint32_t oDocId;
    ZCDECODE(_valI, oDocId = 1 +);
#if DEBUG_ZCFILTEROCC_PRINTF
    printf("DecodeInit docId=%d\n",
           oDocId);
#endif
    setDocId(oDocId);
    _residue = header._num_docs;
}


FakeFilterOccZCArrayIterator::
~FakeFilterOccZCArrayIterator()
{
}


void
FakeFilterOccZCArrayIterator::doSeek(uint32_t docId)
{
    const uint8_t *oCompr = _valI;
    uint32_t oDocId = getDocId();

    if (getUnpacked()) {
        clearUnpacked();
    }
    while (oDocId < docId) {
        if (--_residue == 0) {
            goto atbreak;
        }
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
    return new FakeFilterOccZCArrayIterator(_compressed.first, 0, _posting_params._doc_id_limit, matchData);
}

template <bool doSkip>
class FakeZcSkipFilterOcc : public FakeZcFilterOcc
{
public:
    FakeZcSkipFilterOcc(const FakeWord &fw);

    ~FakeZcSkipFilterOcc() override;
    SearchIterator *createIterator(const TermFieldMatchDataArray &matchData) const override;
};

static FPFactoryInit
initNoSkip(std::make_pair("ZcNoSkipFilterOcc",
                          makeFPFactory<FPFactoryT<FakeZcSkipFilterOcc<false> > >));


static FPFactoryInit
initSkip(std::make_pair("ZcSkipFilterOcc",
                        makeFPFactory<FPFactoryT<FakeZcSkipFilterOcc<true> > >));

template<>
FakeZcSkipFilterOcc<false>::FakeZcSkipFilterOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, true, Zc4PostingParams(force_skip, disable_chunking, fw._docIdLimit, true, false, false), ".zc5noskipfilterocc")
{
    setup(fw);
}


template<>
FakeZcSkipFilterOcc<true>::FakeZcSkipFilterOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, true, Zc4PostingParams(force_skip, disable_chunking, fw._docIdLimit, true, false, false), ".zc5skipfilterocc")
{
    setup(fw);
}


template <bool doSkip>
FakeZcSkipFilterOcc<doSkip>::~FakeZcSkipFilterOcc()
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

    ~FakeFilterOccZCSkipArrayIterator() override;

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
    Zc4PostingParams params(force_skip, disable_chunking, _docIdLimit, true, false, false);
    Zc4PostingHeader header;
    header.read(d, params);
    _lastDocId = header._last_doc_id;
    assert((d.getBitOffset() & 7) == 0);
    const uint8_t *bcompr = d.getByteCompr();
    _valIBase = _valI = bcompr;
    _l1SkipDocIdPos = _l2SkipDocIdPos = bcompr;
    _l3SkipDocIdPos = _l4SkipDocIdPos = bcompr;
    bcompr += header._doc_ids_size;
    if (header._l1_skip_size != 0) {
        _l1SkipValIBase = _l1SkipValI = bcompr;
        _l2SkipL1SkipPos = _l3SkipL1SkipPos = _l4SkipL1SkipPos = bcompr;
        bcompr += header._l1_skip_size;
    } else {
        _l1SkipValIBase = _l1SkipValI = NULL;
        _l2SkipL1SkipPos = _l3SkipL1SkipPos = _l4SkipL1SkipPos = NULL;
    }
    if (header._l2_skip_size != 0) {
        _l2SkipValIBase = _l2SkipValI = bcompr;
        _l3SkipL2SkipPos = _l4SkipL2SkipPos = bcompr;
        bcompr += header._l2_skip_size;
    } else {
        _l2SkipValIBase = _l2SkipValI = NULL;
        _l3SkipL2SkipPos = _l4SkipL2SkipPos = NULL;
    }
    if (header._l3_skip_size != 0) {
        _l3SkipValIBase = _l3SkipValI = bcompr;
        _l4SkipL3SkipPos = bcompr;
        bcompr += header._l3_skip_size;
    } else {
        _l3SkipValIBase = _l3SkipValI = NULL;
        _l4SkipL3SkipPos = NULL;
    }
    if (header._l4_skip_size != 0) {
        _l4SkipValI = bcompr;
        bcompr += header._l4_skip_size;
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
~FakeFilterOccZCSkipArrayIterator()
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
        if (docId <= _l3SkipDocId) {
            return;
        }
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
        if (docId <= _l2SkipDocId) {
            return;
        }
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
        if (docId <= _l1SkipDocId) {
            return;
        }
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
    if (getUnpacked()) {
        clearUnpacked();
    }
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
            _posting_params._doc_id_limit,
            matchData);
}


template <bool bigEndian>
class FakeEGCompr64PosOcc : public FakeZcFilterOcc
{
public:
    FakeEGCompr64PosOcc(const FakeWord &fw);
    ~FakeEGCompr64PosOcc() override;
    size_t bitSize() const override;
    bool hasWordPositions() const override;
    SearchIterator *createIterator(const TermFieldMatchDataArray &matchData) const override;
};


template <bool bigEndian>
FakeEGCompr64PosOcc<bigEndian>::FakeEGCompr64PosOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, bigEndian, Zc4PostingParams(disable_skip, disable_chunking, fw._docIdLimit, true, true, false),
                      bigEndian ? ".zcposoccbe" : ".zcposoccle")
{
    setup(fw);
}


template <bool bigEndian>
FakeEGCompr64PosOcc<bigEndian>::~FakeEGCompr64PosOcc()
{
}

template <bool bigEndian>
size_t
FakeEGCompr64PosOcc<bigEndian>::bitSize() const
{
    return _compressedBits;
}


template <bool bigEndian>
bool
FakeEGCompr64PosOcc<bigEndian>::hasWordPositions() const
{
    return true;
}


template <bool bigEndian>
SearchIterator *
FakeEGCompr64PosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return new ZcRareWordPosOccIterator<bigEndian>(Position(_compressed.first, 0),
                                                   _compressedBits, _posting_params._doc_id_limit, false, &_fieldsParams, matchData);
}


template <bool bigEndian>
class FakeEG2Compr64PosOcc : public FakeZcFilterOcc
{
public:
    FakeEG2Compr64PosOcc(const FakeWord &fw);
    ~FakeEG2Compr64PosOcc() override;
    size_t bitSize() const override;
    bool hasWordPositions() const override;
    SearchIterator *createIterator(const fef::TermFieldMatchDataArray &matchData) const override;
};


template <bool bigEndian>
FakeEG2Compr64PosOcc<bigEndian>::FakeEG2Compr64PosOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, bigEndian, Zc4PostingParams(disable_skip, disable_chunking, fw._docIdLimit, false, true, false),
                      bigEndian ? ".zc4posoccbe" : ".zc4posoccle")
{
    setup(fw);
}


template <bool bigEndian>
FakeEG2Compr64PosOcc<bigEndian>::~FakeEG2Compr64PosOcc()
{
}


template <bool bigEndian>
size_t
FakeEG2Compr64PosOcc<bigEndian>::bitSize() const
{
    return _compressedBits;
}


template <bool bigEndian>
bool
FakeEG2Compr64PosOcc<bigEndian>::hasWordPositions() const
{
    return true;
}


template <bool bigEndian>
SearchIterator *
FakeEG2Compr64PosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return new Zc4RareWordPosOccIterator<bigEndian>(Position(_compressed.first, 0),
                                                    _compressedBits, _posting_params._doc_id_limit, false, &_fieldsParams, matchData);
}


template <bool bigEndian>
class FakeZcSkipPosOcc : public FakeZcFilterOcc
{
    search::index::PostingListCounts _counts;
public:
    FakeZcSkipPosOcc(const FakeWord &fw);
    ~FakeZcSkipPosOcc() override;

    size_t bitSize() const override;
    bool hasWordPositions() const override;
    SearchIterator *createIterator(const TermFieldMatchDataArray &matchData) const override;
};


template <bool bigEndian>
FakeZcSkipPosOcc<bigEndian>::FakeZcSkipPosOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, bigEndian, Zc4PostingParams(force_skip, disable_chunking, fw._docIdLimit, true, true, false),
                      bigEndian ? ".zcskipposoccbe" : ".zcskipposoccle")
{
    setup(fw);
    _counts._bitLength = _compressedBits;
}


template <bool bigEndian>
FakeZcSkipPosOcc<bigEndian>::~FakeZcSkipPosOcc()
{
}


template <bool bigEndian>
size_t
FakeZcSkipPosOcc<bigEndian>::bitSize() const
{
    return _compressedBits -
        _l1SkipSize - _l2SkipSize - _l3SkipSize - _l4SkipSize;
}


template <bool bigEndian>
bool
FakeZcSkipPosOcc<bigEndian>::hasWordPositions() const
{
    return true;
}


template <bool bigEndian>
SearchIterator *
FakeZcSkipPosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return new ZcPosOccIterator<bigEndian>(Position(_compressed.first, 0), _compressedBits, _posting_params._doc_id_limit, false,
                                           static_cast<uint32_t>(-1),
                                           _counts,
                                           &_fieldsParams,
                                           matchData);
}


template <bool bigEndian>
class FakeZc4SkipPosOcc : public FakeZcFilterOcc
{
    search::index::PostingListCounts _counts;
    bool _encode_cheap_features;
protected:
    FakeZc4SkipPosOcc(const FakeWord &fw, const Zc4PostingParams &posting_params, const char *name_suffix);
public:
    FakeZc4SkipPosOcc(const FakeWord &fw);
    ~FakeZc4SkipPosOcc() override;
    size_t bitSize() const override;
    bool hasWordPositions() const override;
    SearchIterator *createIterator(const TermFieldMatchDataArray &matchData) const override;
};


template <bool bigEndian>
FakeZc4SkipPosOcc<bigEndian>::FakeZc4SkipPosOcc(const FakeWord &fw, const Zc4PostingParams &posting_params, const char *name_suffix)
    : FakeZcFilterOcc(fw, bigEndian, posting_params, name_suffix)
{
    setup(fw);
    _counts._bitLength = _compressedBits;
}

template <bool bigEndian>
FakeZc4SkipPosOcc<bigEndian>::FakeZc4SkipPosOcc(const FakeWord &fw)
    : FakeZc4SkipPosOcc<bigEndian>(fw, Zc4PostingParams(force_skip, disable_chunking, fw._docIdLimit, false, true, false),
                                   (bigEndian ? ".zc4skipposoccbe" : ".zc4skipposoccle"))
{
}

template <bool bigEndian>
FakeZc4SkipPosOcc<bigEndian>::~FakeZc4SkipPosOcc() = default;

template <bool bigEndian>
size_t
FakeZc4SkipPosOcc<bigEndian>::bitSize() const
{
    return _compressedBits -
        _l1SkipSize - _l2SkipSize - _l3SkipSize - _l4SkipSize;
}


template <bool bigEndian>
bool
FakeZc4SkipPosOcc<bigEndian>::hasWordPositions() const
{
    return true;
}


template <bool bigEndian>
SearchIterator *
FakeZc4SkipPosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    if (_hitDocs >= _posting_params._min_skip_docs) {
        if (_posting_params._dynamic_k) {
            return new ZcPosOccIterator<bigEndian>(Position(_compressed.first, 0), _compressedBits, _posting_params._doc_id_limit, _posting_params._encode_cheap_features,
                                                   static_cast<uint32_t>(-1),
                                                   _counts,
                                                   &_fieldsParams,
                                                   matchData);
        } else {
            return new Zc4PosOccIterator<bigEndian>(Position(_compressed.first, 0), _compressedBits, _posting_params._doc_id_limit, _posting_params._encode_cheap_features,
                                                    static_cast<uint32_t>(-1), _counts, &_fieldsParams, matchData);
        }
    } else {
        if (_posting_params._dynamic_k) {
            return new ZcRareWordPosOccIterator<bigEndian>(Position(_compressed.first, 0),
                                                           _compressedBits, _posting_params._doc_id_limit, _posting_params._encode_cheap_features, &_fieldsParams, matchData);
        } else {
            return new Zc4RareWordPosOccIterator<bigEndian>(Position(_compressed.first, 0),
                                                            _compressedBits, _posting_params._doc_id_limit, _posting_params._encode_cheap_features, &_fieldsParams, matchData);
        }
    }
}

template <bool bigEndian>
class FakeZc4SkipPosOccCf : public FakeZc4SkipPosOcc<bigEndian>
{
public:
    FakeZc4SkipPosOccCf(const FakeWord &fw)
        : FakeZc4SkipPosOcc<bigEndian>(fw, Zc4PostingParams(force_skip, disable_chunking, fw._docIdLimit, false, true, true),
                                       (bigEndian ? ".zc4skipposoccbe.cf" : ".zc4skipposoccle.cf"))
    {
    }
};

template <bool bigEndian>
class FakeZc4NoSkipPosOccCf : public FakeZc4SkipPosOcc<bigEndian>
{
public:
    FakeZc4NoSkipPosOccCf(const FakeWord &fw)
        : FakeZc4SkipPosOcc<bigEndian>(fw, Zc4PostingParams(disable_skip, disable_chunking, fw._docIdLimit, false, true, true),
                                       (bigEndian ? ".zc4noskipposoccbe.cf" : "zc4noskipposoccle.cf"))
    {
    }
};

template <bool bigEndian>
class FakeZc5NoSkipPosOccCf : public FakeZc4SkipPosOcc<bigEndian>
{
public:
    FakeZc5NoSkipPosOccCf(const FakeWord &fw)
        : FakeZc4SkipPosOcc<bigEndian>(fw, Zc4PostingParams(disable_skip, disable_chunking, fw._docIdLimit, true, true, true),
                                       (bigEndian ? ".zc5noskipposoccbe.cf" : "zc5noskipposoccle.cf"))
    {
    }
};

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
initSkipPos0be(std::make_pair("Zc4SkipPosOccBE",
                              makeFPFactory<FPFactoryT<FakeZc4SkipPosOcc<true> > >));


static FPFactoryInit
initSkipPos0le(std::make_pair("Zc4SkipPosOccLE",
                              makeFPFactory<FPFactoryT<FakeZc4SkipPosOcc<false> > >));


static FPFactoryInit
initSkipPos0becf(std::make_pair("Zc4SkipPosOccBE.cf",
                                makeFPFactory<FPFactoryT<FakeZc4SkipPosOccCf<true> > >));


static FPFactoryInit
initSkipPos0lecf(std::make_pair("Zc4SkipPosOccLE.cf",
                                makeFPFactory<FPFactoryT<FakeZc4SkipPosOccCf<false> > >));

static FPFactoryInit
initNoSkipPos0becf(std::make_pair("Zc4NoSkipPosOccBE.cf",
                                  makeFPFactory<FPFactoryT<FakeZc4NoSkipPosOccCf<true> > >));


static FPFactoryInit
initNoSkipPos0lecf(std::make_pair("Zc4NoSkipPosOccLE.cf",
                                  makeFPFactory<FPFactoryT<FakeZc4NoSkipPosOccCf<false> > >));


static FPFactoryInit
initNoSkipPosbecf(std::make_pair("Zc5NoSkipPosOccBE.cf",
                                 makeFPFactory<FPFactoryT<FakeZc5NoSkipPosOccCf<true> > >));


static FPFactoryInit
initNoSkipPoslecf(std::make_pair("Zc5NoSkipPosOccLE.cf",
                                 makeFPFactory<FPFactoryT<FakeZc5NoSkipPosOccCf<false> > >));


} // namespace fakedata

} // namespace search

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fakezcfilterocc.h"
#include "fpfactory.h"
#include <vespa/searchlib/bitcompression/posocc_fields_params.h>
#include <vespa/searchlib/diskindex/zc_decoder_validator.h>
#include <vespa/searchlib/diskindex/zcposocciterators.h>
#include <vespa/searchlib/diskindex/zc4_posting_header.h>
#include <vespa/searchlib/diskindex/zc4_posting_params.h>
#include <vespa/searchlib/diskindex/zc4_posting_reader.h>
#include <vespa/searchlib/diskindex/zc4_posting_writer.h>
#include <vespa/searchlib/index/postinglistparams.h>

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

namespace search::fakedata {

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
    auto view = buf.view();
    uint32_t bytesOffset = reinterpret_cast<unsigned long>(view.data()) & 7;
    e.writeBits(reinterpret_cast<const uint64_t *>(view.data() - bytesOffset),
                bytesOffset * 8, view.size() * 8);
}

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
      _compressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _compressedAlloc(),
      _featuresSize(0),
      _fieldsParams(fw.getFieldsParams()),
      _bigEndian(true),
      _posting_params(force_skip, disable_chunking, fw._docIdLimit, true, false, false),
      _counts()
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
      _compressed(std::make_pair(static_cast<uint64_t *>(nullptr), 0)),
      _featuresSize(0),
      _fieldsParams(fw.getFieldsParams()),
      _bigEndian(bigEndian),
      _posting_params(posting_params),
      _counts()
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

    auto d = fw._postings.begin();
    auto de = fw._postings.end();
    auto p = fw._wordPosFeatures.begin();
    auto pe = fw._wordPosFeatures.end();
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
    params.set("interleaved_features", _posting_params._encode_interleaved_features);
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

    _compressed = writeContext.grabComprBuffer(_compressedAlloc);
    _counts = counts;
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
    assert(header._num_docs == _hitDocs || header._features_size_flush);
    if (header._num_docs >= _posting_params._min_skip_docs || header._features_size_flush) {
        assert(header._last_doc_id > 0);
        assert(header._last_doc_id <= _lastDocId);
        assert(_lastDocId == header._last_doc_id || _counts._segments.size() > 1);
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
    PostingListCounts counts = _counts;
    assert(counts._bitLength == _compressedBits);
    assert(counts._numDocs == _hitDocs);
    reader.set_word_and_counts(fw.getName(), counts);
    auto word_pos_iterator(fw._wordPosFeatures.begin());
    auto word_pos_iterator_end(fw._wordPosFeatures.end());
    DocIdAndPosOccFeatures check_features;
    DocIdAndFeatures features;
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
        if (_posting_params._encode_interleaved_features) {
            assert(features.field_length() == doc._collapsedDocWordFeatures._field_len);
            assert(features.num_occs() == doc._collapsedDocWordFeatures._num_occs);
        }
    }
    if (_posting_params._encode_features) {
        assert(word_pos_iterator == word_pos_iterator_end);
    }
    reader.read_doc_id_and_features(features);
    assert(static_cast<int32_t>(features.doc_id()) == -1);
}

FakeZcFilterOcc::~FakeZcFilterOcc() = default;


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

bool
FakeZcFilterOcc::has_interleaved_features() const
{
    return _posting_params._encode_interleaved_features;
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
    ZcDecoder _zc_decoder;
    unsigned int _residue;
    uint32_t _lastDocId;

    using DecodeContext = search::bitcompression::FeatureDecodeContextBE;
    using EncodeContext = search::bitcompression::FeatureEncodeContextBE;
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
      _zc_decoder(),
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
    _zc_decoder.set_cur(bcompr);
    bcompr += header._doc_ids_size;
    bcompr += header._l1_skip_size;
    bcompr += header._l2_skip_size;
    bcompr += header._l3_skip_size;
    bcompr += header._l4_skip_size,
    d.setByteCompr(bcompr);
    uint32_t oDocId;
    oDocId = 1 + _zc_decoder.decode32();
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
    ZcDecoder zc_decoder(_zc_decoder);
    uint32_t oDocId = getDocId();

    if (getUnpacked()) {
        clearUnpacked();
    }
    while (oDocId < docId) {
        if (--_residue == 0) {
            goto atbreak;
        }
        oDocId += (1 + zc_decoder.decode32());
#if DEBUG_ZCFILTEROCC_PRINTF
        printf("Decode docId=%d\n",
               docId);
#endif
    }
    _zc_decoder = zc_decoder;
    setDocId(oDocId);
    return;
 atbreak:
    _zc_decoder = zc_decoder;
    setAtEnd();                     // Mark end of data
    return;
}


void
FakeFilterOccZCArrayIterator::doUnpack(uint32_t docId)
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


std::unique_ptr<SearchIterator>
FakeZcFilterOcc::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return std::make_unique<FakeFilterOccZCArrayIterator>(_compressed.first, 0, _posting_params._doc_id_limit, matchData);
}

class FakeZcSkipFilterOcc : public FakeZcFilterOcc
{
    search::index::PostingListCounts _counts;
public:
    FakeZcSkipFilterOcc(const FakeWord &fw);

    ~FakeZcSkipFilterOcc() override;
    std::unique_ptr<SearchIterator> createIterator(const TermFieldMatchDataArray &matchData) const override;
};

static FPFactoryInit
initSkip(std::make_pair("ZcSkipFilterOcc",
                        makeFPFactory<FPFactoryT<FakeZcSkipFilterOcc>>));

FakeZcSkipFilterOcc::FakeZcSkipFilterOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, true, Zc4PostingParams(force_skip, disable_chunking, fw._docIdLimit, true, false, false), ".zc5skipfilterocc")
{
    setup(fw);
    _counts._bitLength = _compressedBits;
    _counts._numDocs = _hitDocs;
}


FakeZcSkipFilterOcc::~FakeZcSkipFilterOcc() = default;

std::unique_ptr<SearchIterator>
FakeZcSkipFilterOcc::createIterator(const TermFieldMatchDataArray &matchData) const
{
    return create_zc_posocc_iterator(true, _counts, Position(_compressed.first, 0), _compressedBits, _posting_params, _fieldsParams, matchData);
}


template <bool bigEndian>
class FakeEGCompr64PosOcc : public FakeZcFilterOcc
{
    search::index::PostingListCounts _counts;
public:
    FakeEGCompr64PosOcc(const FakeWord &fw);
    ~FakeEGCompr64PosOcc() override;
    size_t bitSize() const override;
    bool hasWordPositions() const override;
    std::unique_ptr<SearchIterator> createIterator(const TermFieldMatchDataArray &matchData) const override;
};


template <bool bigEndian>
FakeEGCompr64PosOcc<bigEndian>::FakeEGCompr64PosOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, bigEndian, Zc4PostingParams(disable_skip, disable_chunking, fw._docIdLimit, true, true, false),
                      bigEndian ? ".zcposoccbe" : ".zcposoccle")
{
    setup(fw);
    _counts._bitLength = _compressedBits;
    _counts._numDocs = _hitDocs;
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
std::unique_ptr<SearchIterator>
FakeEGCompr64PosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return create_zc_posocc_iterator(bigEndian, _counts, Position(_compressed.first, 0), _compressedBits, _posting_params, _fieldsParams, matchData);
}


template <bool bigEndian>
class FakeEG2Compr64PosOcc : public FakeZcFilterOcc
{
    search::index::PostingListCounts _counts;
public:
    FakeEG2Compr64PosOcc(const FakeWord &fw);
    ~FakeEG2Compr64PosOcc() override;
    size_t bitSize() const override;
    bool hasWordPositions() const override;
    std::unique_ptr<SearchIterator> createIterator(const fef::TermFieldMatchDataArray &matchData) const override;
};


template <bool bigEndian>
FakeEG2Compr64PosOcc<bigEndian>::FakeEG2Compr64PosOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, bigEndian, Zc4PostingParams(disable_skip, disable_chunking, fw._docIdLimit, false, true, false),
                      bigEndian ? ".zc4posoccbe" : ".zc4posoccle")
{
    setup(fw);
    _counts._bitLength = _compressedBits;
    _counts._numDocs = _hitDocs;
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
std::unique_ptr<SearchIterator>
FakeEG2Compr64PosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return create_zc_posocc_iterator(bigEndian, _counts, Position(_compressed.first, 0), _compressedBits, _posting_params, _fieldsParams, matchData);
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
    std::unique_ptr<SearchIterator> createIterator(const TermFieldMatchDataArray &matchData) const override;
};


template <bool bigEndian>
FakeZcSkipPosOcc<bigEndian>::FakeZcSkipPosOcc(const FakeWord &fw)
    : FakeZcFilterOcc(fw, bigEndian, Zc4PostingParams(force_skip, disable_chunking, fw._docIdLimit, true, true, false),
                      bigEndian ? ".zcskipposoccbe" : ".zcskipposoccle")
{
    setup(fw);
    _counts._bitLength = _compressedBits;
    _counts._numDocs = _hitDocs;
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
std::unique_ptr<SearchIterator>
FakeZcSkipPosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    return create_zc_posocc_iterator(bigEndian, _counts, Position(_compressed.first, 0), _compressedBits, _posting_params, _fieldsParams, matchData);
}


template <bool bigEndian>
class FakeZc4SkipPosOcc : public FakeZcFilterOcc
{
    search::index::PostingListCounts _counts;
protected:
    bool _unpack_normal_features;
    bool _unpack_interleaved_features;
    FakeZc4SkipPosOcc(const FakeWord &fw, const Zc4PostingParams &posting_params, const char *name_suffix);
public:
    FakeZc4SkipPosOcc(const FakeWord &fw);
    ~FakeZc4SkipPosOcc() override;
    size_t bitSize() const override;
    bool hasWordPositions() const override;
    std::unique_ptr<SearchIterator> createIterator(const TermFieldMatchDataArray &matchData) const override;
    bool enable_unpack_normal_features() const override { return _unpack_normal_features; }
    bool enable_unpack_interleaved_features() const override { return _unpack_interleaved_features; }
};


template <bool bigEndian>
FakeZc4SkipPosOcc<bigEndian>::FakeZc4SkipPosOcc(const FakeWord &fw, const Zc4PostingParams &posting_params, const char *name_suffix)
    : FakeZcFilterOcc(fw, bigEndian, posting_params, name_suffix),
      _counts(),
      _unpack_normal_features(true),
      _unpack_interleaved_features(true)
{
    setup(fw);
    _counts._bitLength = _compressedBits;
    _counts._numDocs = _hitDocs;
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
std::unique_ptr<SearchIterator>
FakeZc4SkipPosOcc<bigEndian>::
createIterator(const TermFieldMatchDataArray &matchData) const
{
    if (matchData.valid()) {
        assert(_unpack_normal_features == matchData[0]->needs_normal_features());
        assert(_unpack_interleaved_features == matchData[0]->needs_interleaved_features());
    } else {
        assert(!_unpack_normal_features);
        assert(!_unpack_interleaved_features);
    }
    return create_zc_posocc_iterator(bigEndian, _counts, Position(_compressed.first, 0), _compressedBits, _posting_params, _fieldsParams, matchData);
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
    ~FakeZc4SkipPosOccCf() override;
};

template <bool bigEndian>
FakeZc4SkipPosOccCf<bigEndian>::~FakeZc4SkipPosOccCf() = default;

class FakeZc4SkipPosOccCfNoNormalUnpack : public FakeZc4SkipPosOcc<true>
{
public:
    FakeZc4SkipPosOccCfNoNormalUnpack(const FakeWord &fw)
        : FakeZc4SkipPosOcc<true>(fw, Zc4PostingParams(force_skip, disable_chunking, fw._docIdLimit, false, true, true),
                                  ".zc4skipposoccbe.cf.nnu")
    {
        _unpack_normal_features = false;
    }
    ~FakeZc4SkipPosOccCfNoNormalUnpack() override;
};

FakeZc4SkipPosOccCfNoNormalUnpack::~FakeZc4SkipPosOccCfNoNormalUnpack() = default;

class FakeZc4SkipPosOccCfNoCheapUnpack : public FakeZc4SkipPosOcc<true>
{
public:
    FakeZc4SkipPosOccCfNoCheapUnpack(const FakeWord &fw)
        : FakeZc4SkipPosOcc<true>(fw, Zc4PostingParams(force_skip, disable_chunking, fw._docIdLimit, false, true, true),
                                  ".zc4skipposoccbe.cf.ncu")
    {
        _unpack_interleaved_features = false;
    }
    ~FakeZc4SkipPosOccCfNoCheapUnpack() override;
};

FakeZc4SkipPosOccCfNoCheapUnpack::~FakeZc4SkipPosOccCfNoCheapUnpack() = default;

template <bool bigEndian>
class FakeZc4NoSkipPosOccCf : public FakeZc4SkipPosOcc<bigEndian>
{
public:
    FakeZc4NoSkipPosOccCf(const FakeWord &fw)
        : FakeZc4SkipPosOcc<bigEndian>(fw, Zc4PostingParams(disable_skip, disable_chunking, fw._docIdLimit, false, true, true),
                                       (bigEndian ? ".zc4noskipposoccbe.cf" : "zc4noskipposoccle.cf"))
    {
    }
    ~FakeZc4NoSkipPosOccCf() override;
};

template <bool bigEndian>
FakeZc4NoSkipPosOccCf<bigEndian>::~FakeZc4NoSkipPosOccCf() = default;

class FakeZc4NoSkipPosOccCfNoNormalUnpack : public FakeZc4SkipPosOcc<true>
{
public:
    FakeZc4NoSkipPosOccCfNoNormalUnpack(const FakeWord &fw)
        : FakeZc4SkipPosOcc<true>(fw, Zc4PostingParams(disable_skip, disable_chunking, fw._docIdLimit, false, true, true),
                                  ".zc4noskipposoccbe.cf.nnu")
    {
        _unpack_normal_features = false;
    }
    ~FakeZc4NoSkipPosOccCfNoNormalUnpack() override;
};

FakeZc4NoSkipPosOccCfNoNormalUnpack::~FakeZc4NoSkipPosOccCfNoNormalUnpack() = default;

class FakeZc4NoSkipPosOccCfNoCheapUnpack : public FakeZc4SkipPosOcc<true>
{
public:
    FakeZc4NoSkipPosOccCfNoCheapUnpack(const FakeWord &fw)
        : FakeZc4SkipPosOcc<true>(fw, Zc4PostingParams(disable_skip, disable_chunking, fw._docIdLimit, false, true, true),
                                  ".zc4noskipposoccbe.cf.ncu")
    {
        _unpack_interleaved_features = false;
    }
    ~FakeZc4NoSkipPosOccCfNoCheapUnpack() override;
};

FakeZc4NoSkipPosOccCfNoCheapUnpack::~FakeZc4NoSkipPosOccCfNoCheapUnpack() = default;

template <bool bigEndian>
class FakeZc5NoSkipPosOccCf : public FakeZc4SkipPosOcc<bigEndian>
{
public:
    FakeZc5NoSkipPosOccCf(const FakeWord &fw)
        : FakeZc4SkipPosOcc<bigEndian>(fw, Zc4PostingParams(disable_skip, disable_chunking, fw._docIdLimit, true, true, true),
                                       (bigEndian ? ".zc5noskipposoccbe.cf" : ".zc5noskipposoccle.cf"))
    {
    }
    ~FakeZc5NoSkipPosOccCf() override;
};

template <bool bigEndian>
FakeZc5NoSkipPosOccCf<bigEndian>::~FakeZc5NoSkipPosOccCf() = default;

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
initSkipPos0becfnnu(std::make_pair("Zc4SkipPosOccBE.cf.nnu",
                                makeFPFactory<FPFactoryT<FakeZc4SkipPosOccCfNoNormalUnpack > >));


static FPFactoryInit
initSkipPos0becfncu(std::make_pair("Zc4SkipPosOccBE.cf.ncu",
                                makeFPFactory<FPFactoryT<FakeZc4SkipPosOccCfNoCheapUnpack > >));


static FPFactoryInit
initNoSkipPos0becf(std::make_pair("Zc4NoSkipPosOccBE.cf",
                                  makeFPFactory<FPFactoryT<FakeZc4NoSkipPosOccCf<true> > >));


static FPFactoryInit
initNoSkipPos0becfnnu(std::make_pair("Zc4NoSkipPosOccBE.cf.nnu",
                                  makeFPFactory<FPFactoryT<FakeZc4NoSkipPosOccCfNoNormalUnpack > >));


static FPFactoryInit
initNoSkipPos0becfncu(std::make_pair("Zc4NoSkipPosOccBE.cf.ncu",
                                  makeFPFactory<FPFactoryT<FakeZc4NoSkipPosOccCfNoCheapUnpack > >));


static FPFactoryInit
initNoSkipPos0lecf(std::make_pair("Zc4NoSkipPosOccLE.cf",
                                  makeFPFactory<FPFactoryT<FakeZc4NoSkipPosOccCf<false> > >));


static FPFactoryInit
initNoSkipPosbecf(std::make_pair("Zc5NoSkipPosOccBE.cf",
                                 makeFPFactory<FPFactoryT<FakeZc5NoSkipPosOccCf<true> > >));


static FPFactoryInit
initNoSkipPoslecf(std::make_pair("Zc5NoSkipPosOccLE.cf",
                                 makeFPFactory<FPFactoryT<FakeZc5NoSkipPosOccCf<false> > >));

}

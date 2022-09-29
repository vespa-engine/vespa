// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/util/randomgenerator.h>
#include <vespa/vespalib/util/compress.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/attribute/i_document_weight_attribute.h>
#include <vespa/searchlib/queryeval/document_weight_search_iterator.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>

LOG_SETUP("bitvector_test");

using search::AttributeFactory;
using search::AttributeVector;
using search::BitVector;
using search::BitVectorIterator;
using search::FloatingPointAttribute;
using search::IntegerAttribute;
using search::ParseItem;
using search::StringAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::SearchContext;
using search::attribute::SearchContextParams;
using search::fef::TermFieldMatchData;
using search::queryeval::SearchIterator;

using SearchContextPtr = std::unique_ptr<SearchContext>;
typedef std::unique_ptr<search::queryeval::SearchIterator> SearchBasePtr;

struct BitVectorTest
{
    typedef AttributeVector::SP AttributePtr;

    BitVectorTest() { }

    ~BitVectorTest() { }

    template <typename VectorType>
    VectorType & as(AttributePtr &v);
    IntegerAttribute & asInt(AttributePtr &v);
    StringAttribute & asString(AttributePtr &v);
    FloatingPointAttribute & asFloat(AttributePtr &v);

    AttributePtr
    make(Config cfg,
         const vespalib::string &pref,
         bool fastSearch,
         bool enableOnlyBitVector,
         bool filter);

    void
    addDocs(const AttributePtr &v, size_t sz);

    template <typename VectorType>
    void populate(VectorType &v, uint32_t low, uint32_t high, bool set);

    template <typename VectorType>
    void populateAll(VectorType &v, uint32_t low, uint32_t high, bool set);

    void
    buildTermQuery(std::vector<char> & buffer,
                   const vespalib::string & index,
                   const vespalib::string & term, bool prefix);

    template <typename V>
    vespalib::string
    getSearchStr();

    template <typename V, typename T>
    SearchContextPtr
    getSearch(const V & vec, const T & term, bool prefix, bool useBitVector);

    template <typename V>
    SearchContextPtr
    getSearch(const V & vec, bool useBitVector);

    void
    checkSearch(AttributePtr v,
                SearchBasePtr sb,
                TermFieldMatchData &md,
                uint32_t expFirstDocId,
                uint32_t expFastDocId,
                uint32_t expDocFreq,
                bool weights,
                bool checkStride);

    void
    checkSearch(AttributePtr v,
                SearchContextPtr sc,
                uint32_t expFirstDocId,
                uint32_t expLastDocId,
                uint32_t expDocFreq,
                bool weights,
                bool checkStride);

    template <typename VectorType, typename BufferType>
    void
    test(BasicType bt, CollectionType ct, const vespalib::string &pref,
         bool fastSearch,
         bool enableOnlyBitVector,
         bool filter);

    template <typename VectorType, typename BufferType>
    void
    test(BasicType bt, CollectionType ct, const vespalib::string &pref);
};


template <typename VectorType>
VectorType &
BitVectorTest::as(AttributePtr &v)
{
    auto *res = dynamic_cast<VectorType *>(v.get());
    assert(res != nullptr);
    return *res;
}


IntegerAttribute &
BitVectorTest::asInt(AttributePtr &v)
{
    return as<IntegerAttribute>(v);
}


StringAttribute &
BitVectorTest::asString(AttributePtr &v)
{
    return as<StringAttribute>(v);
}


FloatingPointAttribute &
BitVectorTest::asFloat(AttributePtr &v)
{
    return as<FloatingPointAttribute>(v);
}


void
BitVectorTest::buildTermQuery(std::vector<char> &buffer,
                                   const vespalib::string &index,
                                   const vespalib::string &term,
                                   bool prefix)
{
    uint32_t indexLen = index.size();
    uint32_t termLen = term.size();
    uint32_t queryPacketSize = 1 + 2 * 4 + indexLen + termLen;
    uint32_t p = 0;
    buffer.resize(queryPacketSize);
    buffer[p++] = prefix ? ParseItem::ITEM_PREFIXTERM : ParseItem::ITEM_TERM;
    p += vespalib::compress::Integer::compressPositive(indexLen, &buffer[p]);
    memcpy(&buffer[p], index.c_str(), indexLen);
    p += indexLen;
    p += vespalib::compress::Integer::compressPositive(termLen, &buffer[p]);
    memcpy(&buffer[p], term.c_str(), termLen);
    p += termLen;
    buffer.resize(p);
}


template <>
vespalib::string
BitVectorTest::getSearchStr<IntegerAttribute>()
{
    return "[-42;-42]";
}

template <>
vespalib::string
BitVectorTest::getSearchStr<FloatingPointAttribute>()
{
    return "[-42.0;-42.0]";
}

template <>
vespalib::string
BitVectorTest::getSearchStr<StringAttribute>()
{
    return "foo";
}


template <typename V, typename T>
SearchContextPtr
BitVectorTest::getSearch(const V &vec, const T &term, bool prefix,
                         bool useBitVector)
{
    std::vector<char> query;
    vespalib::asciistream ss;
    ss << term;
    buildTermQuery(query, vec.getName(), ss.str(), prefix);

    return (static_cast<const AttributeVector &>(vec)).
        getSearch(vespalib::stringref(&query[0], query.size()),
                  SearchContextParams().useBitVector(useBitVector));
}


template <>
SearchContextPtr
BitVectorTest::getSearch<IntegerAttribute>(const IntegerAttribute &v,
                                           bool useBitVector)
{
    return getSearch<IntegerAttribute>(v, "[-42;-42]", false, useBitVector);
}

template <>
SearchContextPtr
BitVectorTest::
getSearch<FloatingPointAttribute>(const FloatingPointAttribute &v,
                                  bool useBitVector)
{
    return getSearch<FloatingPointAttribute>(v, "[-42.0;-42.0]", false,
                                             useBitVector);
}

template <>
SearchContextPtr
BitVectorTest::getSearch<StringAttribute>(const StringAttribute &v,
                                          bool useBitVector)
{
    return getSearch<StringAttribute, const vespalib::string &>
        (v, "foo", false, useBitVector);
}


BitVectorTest::AttributePtr
BitVectorTest::make(Config cfg,
                    const vespalib::string &pref,
                    bool fastSearch,
                    bool enableOnlyBitVector,
                    bool filter)
{
    cfg.setFastSearch(fastSearch);
    cfg.setEnableOnlyBitVector(enableOnlyBitVector);
    cfg.setIsFilter(filter);
    AttributePtr v = AttributeFactory::createAttribute(pref, cfg);
    return v;
}


void
BitVectorTest::addDocs(const AttributePtr &v, size_t sz)
{
    while (v->getNumDocs() < sz) {
        AttributeVector::DocId docId = 0;
        EXPECT_TRUE(v->addDoc(docId));
        v->clearDoc(docId);
    }
    EXPECT_TRUE(v->getNumDocs() == sz);
    v->commit(true);
}


template <>
void
BitVectorTest::populate(IntegerAttribute &v,
                        uint32_t low, uint32_t high,
                        bool set)
{
    for(size_t i(low), m(high); i < m; i+= 5) {
        if (!set) {
            v.clearDoc(i);
        } else if (v.hasMultiValue()) {
            v.append(i, -42, 27);
            v.append(i, -43, 14);
            v.append(i, -42, -3);
        } else {
            EXPECT_TRUE(v.update(i, -42));
        }
    }
    v.commit();
}


template <>
void
BitVectorTest::populate(FloatingPointAttribute &v,
                        uint32_t low, uint32_t high,
                        bool set)
{
    for(size_t i(low), m(high); i < m; i+= 5) {
        if (!set) {
            v.clearDoc(i);
        } else if (v.hasMultiValue()) {
            v.append(i, -42.0, 27);
            v.append(i, -43.0, 14);
            v.append(i, -42.0, -3);
        } else {
            EXPECT_TRUE(v.update(i, -42.0));
        }
    }
    v.commit();
}


template <>
void
BitVectorTest::populate(StringAttribute &v,
                        uint32_t low, uint32_t high,
                        bool set)
{
    for(size_t i(low), m(high); i < m; i+= 5) {
        if (!set) {
            v.clearDoc(i);
        } else if (v.hasMultiValue()) {
            v.append(i, "foo", 27);
            v.append(i, "bar", 14);
            v.append(i, "foO", -3);
        } else {
            EXPECT_TRUE(v.update(i, "foo"));
        }
    }
    v.commit();
}

template <>
void
BitVectorTest::populateAll(IntegerAttribute &v,
                        uint32_t low, uint32_t high,
                        bool set)
{
    for(size_t i(low), m(high); i < m; ++i) {
        if (!set) {
            v.clearDoc(i);
        } else if (v.hasMultiValue()) {
            v.clearDoc(i);
            v.append(i, -42, 27);
            v.append(i, -43, 14);
            v.append(i, -42, -3);
        } else {
            EXPECT_TRUE(v.update(i, -42));
        }
    }
    v.commit();
}


template <>
void
BitVectorTest::populateAll(FloatingPointAttribute &v,
                           uint32_t low, uint32_t high,
                           bool set)
{
    for(size_t i(low), m(high); i < m; ++i) {
        if (!set) {
            v.clearDoc(i);
        } else if (v.hasMultiValue()) {
            v.clearDoc(i);
            v.append(i, -42.0, 27);
            v.append(i, -43.0, 14);
            v.append(i, -42.0, -3);
        } else {
            EXPECT_TRUE(v.update(i, -42.0));
        }
    }
    v.commit();
}


template <>
void
BitVectorTest::populateAll(StringAttribute &v,
                           uint32_t low, uint32_t high,
                           bool set)
{
    for(size_t i(low), m(high); i < m; ++i) {
        if (!set) {
            v.clearDoc(i);
        } else if (v.hasMultiValue()) {
            v.clearDoc(i);
            v.append(i, "foo", 27);
            v.append(i, "bar", 14);
            v.append(i, "foO", -3);
        } else {
            EXPECT_TRUE(v.update(i, "foo"));
        }
    }
    v.commit();
}


void
BitVectorTest::checkSearch(AttributePtr v,
                           SearchBasePtr sb,
                           TermFieldMatchData &md,
                           uint32_t expFirstDocId,
                           uint32_t expLastDocId,
                           uint32_t expDocFreq,
                           bool weights,
                           bool checkStride)
{
    (void) checkStride;
    sb->initRange(1, v->getCommittedDocIdLimit());
    sb->seek(1u);
    uint32_t docId = sb->getDocId();
    uint32_t lastDocId = 0;
    uint32_t docFreq = 0;
    EXPECT_EQUAL(expFirstDocId, docId);
    while (docId != search::endDocId) {
        lastDocId = docId;
        ++docFreq,
        assert(!checkStride || (docId % 5) == 2u);
        sb->unpack(docId);
        EXPECT_EQUAL(md.getDocId(), docId);
        if (v->getCollectionType() == CollectionType::SINGLE ||
            !weights) {
            EXPECT_EQUAL(1, md.getWeight());
        } else if (v->getCollectionType() == CollectionType::ARRAY) {
            EXPECT_EQUAL(2, md.getWeight());
        } else {
            if (v->getBasicType() == BasicType::STRING) {
                EXPECT_EQUAL(24, md.getWeight());
            } else {
                EXPECT_EQUAL(-3, md.getWeight());
            }
        }
        sb->seek(docId + 1);
        docId = sb->getDocId();
    }
    EXPECT_EQUAL(expLastDocId, lastDocId);
    EXPECT_EQUAL(expDocFreq, docFreq);
}


void
BitVectorTest::checkSearch(AttributePtr v,
                           SearchContextPtr sc,
                           uint32_t expFirstDocId,
                           uint32_t expLastDocId,
                           uint32_t expDocFreq,
                           bool weights,
                           bool checkStride)
{
    TermFieldMatchData md;
    sc->fetchPostings(search::queryeval::ExecuteInfo::TRUE);
    SearchBasePtr sb = sc->createIterator(&md, true);
    checkSearch(std::move(v), std::move(sb), md,
                expFirstDocId, expLastDocId, expDocFreq, weights,
                checkStride);
}


template <typename VectorType, typename BufferType>
void
BitVectorTest::test(BasicType bt,
                    CollectionType ct,
                    const vespalib::string &pref,
                    bool fastSearch,
                    bool enableOnlyBitVector,
                    bool filter)
{
    Config cfg(bt, ct);
    AttributePtr v = make(cfg, pref, fastSearch, enableOnlyBitVector, filter);
    addDocs(v, 1024);
    auto &tv = as<VectorType>(v);
    populate(tv, 2, 1023, true);

    SearchContextPtr sc = getSearch<VectorType>(tv, true);
    checkSearch(v, std::move(sc), 2, 1022, 205, !fastSearch && !filter, true);
    sc = getSearch<VectorType>(tv, false);
    checkSearch(v, std::move(sc), 2, 1022, 205, !enableOnlyBitVector && !filter, true);
    const search::IDocumentWeightAttribute *dwa = v->asDocumentWeightAttribute();
    if (dwa != nullptr) {
        search::IDocumentWeightAttribute::LookupResult lres = 
            dwa->lookup(getSearchStr<VectorType>(), dwa->get_dictionary_snapshot());
        typedef search::queryeval::DocumentWeightSearchIterator DWSI;
        typedef search::queryeval::SearchIterator SI;
        TermFieldMatchData md;
        SI::UP dwsi(new DWSI(md, *dwa, lres));
        if (!enableOnlyBitVector) {
            checkSearch(v, std::move(dwsi), md, 2, 1022, 205, !filter, true);
        } else {
            dwsi->initRange(1, v->getCommittedDocIdLimit());
            EXPECT_TRUE(dwsi->isAtEnd());
        }
    }
    populate(tv, 2, 973, false);
    sc = getSearch<VectorType>(tv, true);
    checkSearch(v, std::move(sc), 977, 1022, 10, !enableOnlyBitVector &&!filter, true);
    populate(tv, 2, 973, true);
    sc = getSearch<VectorType>(tv, true);
    checkSearch(v, std::move(sc), 2, 1022, 205, !fastSearch && !filter, true);
    addDocs(v, 15000);
    sc = getSearch<VectorType>(tv, true);
    checkSearch(v, std::move(sc), 2, 1022, 205, !enableOnlyBitVector && !filter, true);
    populateAll(tv, 10, 15000, true);
    sc = getSearch<VectorType>(tv, true);
    checkSearch(v, std::move(sc), 2, 14999, 14992, !fastSearch && !filter, false);
}


template <typename VectorType, typename BufferType>
void
BitVectorTest::test(BasicType bt, CollectionType ct, const vespalib::string &pref)
{
    LOG(info, "test run, pref is %s", pref.c_str());
    test<VectorType, BufferType>(bt, ct, pref, false, false, false);
    test<VectorType, BufferType>(bt, ct, pref, false, false, true);
    test<VectorType, BufferType>(bt, ct, pref, true, false, false);
    test<VectorType, BufferType>(bt, ct, pref, true, false, true);
    test<VectorType, BufferType>(bt, ct, pref, true, true, false);
    test<VectorType, BufferType>(bt, ct, pref, true, true, true);
}


TEST_F("Test bitvectors with single value int32", BitVectorTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::largeint_t>(BasicType::INT32,
                                      CollectionType::SINGLE,
                                      "int32_sv");
}

TEST_F("Test bitvectors with array value int32", BitVectorTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::largeint_t>(BasicType::INT32,
                                      CollectionType::ARRAY,
                                      "int32_a");
}

TEST_F("Test bitvectors with weighted set value int32", BitVectorTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::WeightedInt>(BasicType::INT32,
                                       CollectionType::WSET,
                                       "int32_sv");
}

TEST_F("Test bitvectors with single value double", BitVectorTest)
{
    f.template test<FloatingPointAttribute,
        double>(BasicType::DOUBLE,
                CollectionType::SINGLE,
                "double_sv");
}

TEST_F("Test bitvectors with array value double", BitVectorTest)
{
    f.template test<FloatingPointAttribute,
        double>(BasicType::DOUBLE,
                CollectionType::ARRAY,
                "double_a");
}

TEST_F("Test bitvectors with weighted set value double", BitVectorTest)
{
    f.template test<FloatingPointAttribute,
        FloatingPointAttribute::WeightedFloat>(BasicType::DOUBLE,
                                               CollectionType::WSET,
                                               "double_ws");
}

TEST_F("Test bitvectors with single value string", BitVectorTest)
{
    f.template test<StringAttribute,
        vespalib::string>(BasicType::STRING,
                          CollectionType::SINGLE,
                          "string_sv");
}

TEST_F("Test bitvectors with array value string", BitVectorTest)
{
    f.template test<StringAttribute,
        vespalib::string>(BasicType::STRING,
                          CollectionType::ARRAY,
                          "string_a");
}

TEST_F("Test bitvectors with weighted set value string", BitVectorTest)
{
    f.template test<StringAttribute,
        StringAttribute::WeightedString>(BasicType::STRING,
                                         CollectionType::WSET,
                                         "string_ws");
}


class Verifier : public search::test::SearchIteratorVerifier {
public:
    explicit Verifier(bool inverted);
    ~Verifier() override;

    SearchIterator::UP create(bool strict) const override {
        return BitVectorIterator::create(_bv.get(), getDocIdLimit(), _tfmd, strict, _inverted);
    }

private:
    bool _inverted;
    mutable TermFieldMatchData _tfmd;
    BitVector::UP _bv;
};

Verifier::Verifier(bool inverted)
    : _inverted(inverted),
      _bv(BitVector::create(getDocIdLimit()))
{
    if (inverted) {
        _bv->setInterval(0, getDocIdLimit());
    }
    for (uint32_t docId: getExpectedDocIds()) {
        if (inverted) {
            _bv->clearBit(docId);
        } else {
            _bv->setBit(docId);
        }
    }
}
Verifier::~Verifier() = default;

TEST("Test that bitvector iterators adheres to SearchIterator requirements") {
    {
        Verifier searchIteratorVerifier(false);
        searchIteratorVerifier.verify();
    }
    {
        Verifier searchIteratorVerifier(true);
        searchIteratorVerifier.verify();
    }
}


TEST_MAIN() { TEST_RUN_ALL(); }

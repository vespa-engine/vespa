// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/queryeval/docid_with_weight_search_iterator.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/util/randomgenerator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/compress.h>

using search::AttributeFactory;
using search::AttributeVector;
using search::BitVector;
using search::BitVectorIterator;
using search::CommitParam;
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
using SearchBasePtr = std::unique_ptr<search::queryeval::SearchIterator>;

namespace search::attribute {

void PrintTo(const BasicType& bt, std::ostream* os) {
    *os << bt.asString();
}

void PrintTo(const CollectionType& ct, std::ostream* os) {
    *os << ct.asString();
}

}

std::string
param_as_string(const testing::TestParamInfo<std::tuple<BasicType, CollectionType, bool, bool>>& info)
{
    std::ostringstream os;
    auto& param = info.param;
    os << std::get<0>(param).asString() << "_";
    os << std::get<1>(param).asString();
    os << (std::get<2>(param) ? "_fs" : "");
    os << (std::get<3>(param) ? "_filter" : "");
    return os.str();
}

class BitVectorTest : public ::testing::TestWithParam<std::tuple<BasicType, CollectionType, bool, bool>>
{
public:
    using AttributePtr = AttributeVector::SP;

    BitVectorTest();
    ~BitVectorTest() override;

    template <typename VectorType>
    VectorType & as(AttributePtr &v);
    IntegerAttribute & asInt(AttributePtr &v);
    StringAttribute & asString(AttributePtr &v);
    FloatingPointAttribute & asFloat(AttributePtr &v);

    AttributePtr make(Config cfg, const std::string &pref, bool fastSearch, bool filter);

    void addDocs(const AttributePtr &v, size_t sz);

    template <typename VectorType>
    void populate(VectorType &v, uint32_t low, uint32_t high, bool set);

    template <typename VectorType>
    void populateAll(VectorType &v, uint32_t low, uint32_t high, bool set);

    void buildTermQuery(std::vector<char> & buffer, const std::string & index, const std::string & term, bool prefix);

    template <typename V>
    std::string getSearchStr();

    template <typename V, typename T>
    SearchContextPtr getSearch(const V & vec, const T & term, bool prefix, bool useBitVector);

    template <typename V>
    SearchContextPtr getSearch(const V & vec, bool useBitVector);

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

    template <typename VectorType>
    void
    test(BasicType bt, CollectionType ct, const std::string &pref, bool fastSearch, bool filter);
};

BitVectorTest::BitVectorTest() = default;
BitVectorTest::~BitVectorTest() = default;


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
                                   const std::string &index,
                                   const std::string &term,
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
std::string
BitVectorTest::getSearchStr<IntegerAttribute>()
{
    return "[-42;-42]";
}

template <>
std::string
BitVectorTest::getSearchStr<FloatingPointAttribute>()
{
    return "[-42.0;-42.0]";
}

template <>
std::string
BitVectorTest::getSearchStr<StringAttribute>()
{
    return "foo";
}


template <typename V, typename T>
SearchContextPtr
BitVectorTest::getSearch(const V &vec, const T &term, bool prefix, bool useBitVector)
{
    std::vector<char> query;
    vespalib::asciistream ss;
    ss << term;
    buildTermQuery(query, vec.getName(), ss.str(), prefix);

    return (static_cast<const AttributeVector &>(vec)).
        getSearch(std::string_view(&query[0], query.size()),
                  SearchContextParams().useBitVector(useBitVector));
}


template <>
SearchContextPtr
BitVectorTest::getSearch<IntegerAttribute>(const IntegerAttribute &v, bool useBitVector)
{
    return getSearch<IntegerAttribute>(v, "[-42;-42]", false, useBitVector);
}

template <>
SearchContextPtr
BitVectorTest::
getSearch<FloatingPointAttribute>(const FloatingPointAttribute &v, bool useBitVector)
{
    return getSearch<FloatingPointAttribute>(v, "[-42.0;-42.0]", false, useBitVector);
}

template <>
SearchContextPtr
BitVectorTest::getSearch<StringAttribute>(const StringAttribute &v, bool useBitVector)
{
    return getSearch<StringAttribute, const std::string &>(v, "foo", false, useBitVector);
}


BitVectorTest::AttributePtr
BitVectorTest::make(Config cfg, const std::string &pref, bool fastSearch, bool filter)
{
    cfg.setFastSearch(fastSearch);
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
    v->commit(CommitParam::UpdateStats::FORCE);
}


template <>
void
BitVectorTest::populate(IntegerAttribute &v, uint32_t low, uint32_t high, bool set)
{
    for (size_t i(low), m(high); i < m; i+= 5) {
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
BitVectorTest::populate(FloatingPointAttribute &v, uint32_t low, uint32_t high, bool set)
{
    for (size_t i(low), m(high); i < m; i+= 5) {
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
BitVectorTest::populate(StringAttribute &v, uint32_t low, uint32_t high, bool set)
{
    for (size_t i(low), m(high); i < m; i+= 5) {
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
BitVectorTest::populateAll(IntegerAttribute &v, uint32_t low, uint32_t high, bool set)
{
    for (size_t i(low), m(high); i < m; ++i) {
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
BitVectorTest::populateAll(FloatingPointAttribute &v, uint32_t low, uint32_t high, bool set)
{
    for (size_t i(low), m(high); i < m; ++i) {
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
BitVectorTest::populateAll(StringAttribute &v, uint32_t low, uint32_t high, bool set)
{
    for (size_t i(low), m(high); i < m; ++i) {
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
    EXPECT_EQ(expFirstDocId, docId);
    while (docId != search::endDocId) {
        lastDocId = docId;
        ++docFreq,
        assert(!checkStride || (docId % 5) == 2u);
        sb->unpack(docId);
        EXPECT_TRUE(md.has_ranking_data(docId));
        if (v->getCollectionType() == CollectionType::SINGLE || !weights) {
            EXPECT_EQ(1, md.getWeight());
        } else if (v->getCollectionType() == CollectionType::ARRAY) {
            EXPECT_EQ(2, md.getWeight());
        } else {
            if (v->getBasicType() == BasicType::STRING) {
                EXPECT_EQ(24, md.getWeight());
            } else {
                EXPECT_EQ(-3, md.getWeight());
            }
        }
        sb->seek(docId + 1);
        docId = sb->getDocId();
    }
    EXPECT_EQ(expLastDocId, lastDocId);
    EXPECT_EQ(expDocFreq, docFreq);
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
    sc->fetchPostings(search::queryeval::ExecuteInfo::FULL, true);
    SearchBasePtr sb = sc->createIterator(&md, true);
    checkSearch(std::move(v), std::move(sb), md,
                expFirstDocId, expLastDocId, expDocFreq, weights,
                checkStride);
}


template <typename VectorType>
void
BitVectorTest::test(BasicType bt, CollectionType ct, const std::string &pref, bool fastSearch, bool filter)
{
    Config cfg(bt, ct);
    AttributePtr v = make(cfg, pref, fastSearch, filter);
    addDocs(v, 1024);
    auto &tv = as<VectorType>(v);
    populate(tv, 2, 1023, true);

    SearchContextPtr sc = getSearch<VectorType>(tv, true);
    checkSearch(v, std::move(sc), 2, 1022, 205, !fastSearch && !filter, true);
    sc = getSearch<VectorType>(tv, filter);
    checkSearch(v, std::move(sc), 2, 1022, 205, !filter, true);
    const auto* dww = v->as_docid_with_weight_posting_store();
    if ((dww != nullptr) && (bt == BasicType::STRING)) {
        // This way of doing lookup is only supported by string attributes.
        auto lres = dww->lookup(getSearchStr<VectorType>(), dww->get_dictionary_snapshot());
        using DWSI = search::queryeval::DocidWithWeightSearchIterator;
        TermFieldMatchData md;
        auto dwsi = std::make_unique<DWSI>(md, *dww, lres);
        if (!filter) {
            SCOPED_TRACE("dww without filter");
            checkSearch(v, std::move(dwsi), md, 2, 1022, 205, !filter, true);
        } else {
            dwsi->initRange(1, v->getCommittedDocIdLimit());
            EXPECT_TRUE(dwsi->isAtEnd());
        }
    }
    populate(tv, 2, 973, false);
    sc = getSearch<VectorType>(tv, filter);
    checkSearch(v, std::move(sc), 977, 1022, 10, !filter, true);
    populate(tv, 2, 973, true);
    sc = getSearch<VectorType>(tv, true);
    checkSearch(v, std::move(sc), 2, 1022, 205, !fastSearch && !filter, true);
    addDocs(v, 15000);
    sc = getSearch<VectorType>(tv, filter);
    checkSearch(v, std::move(sc), 2, 1022, 205, !filter, true);
    populateAll(tv, 10, 15000, true);
    sc = getSearch<VectorType>(tv, true);
    checkSearch(v, std::move(sc), 2, 14999, 14992, !fastSearch && !filter, false);
}

TEST_P(BitVectorTest, test_bitvectors)
{
    const auto& param = GetParam();
    auto bt = std::get<0>(param);
    auto ct = std::get<1>(param);
    auto fast_search = std::get<2>(param);
    auto filter = std::get<3>(param);
    vespalib::asciistream pref;
    pref << bt.asString() << "_" << ct.asString();
    switch (bt.type()) {
    case BasicType::INT32:
        test<IntegerAttribute>(bt, ct, pref.str(), fast_search, filter);
        break;
    case BasicType::DOUBLE:
        test<FloatingPointAttribute>(bt, ct, pref.str(), fast_search, filter);
        break;
    case BasicType::STRING:
        test<StringAttribute>(bt, ct, pref.str(), fast_search, filter);
        break;
    default:
        FAIL() << "Cannot handle basic type " << bt.asString();
    }
}

auto test_values = testing::Combine(testing::Values(BasicType::INT32, BasicType::DOUBLE, BasicType::STRING), testing::Values(CollectionType::SINGLE, CollectionType::ARRAY, CollectionType::WSET),testing::Bool(), testing::Bool());

INSTANTIATE_TEST_SUITE_P(Attributes, BitVectorTest, test_values, param_as_string);

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

TEST(BitVectorVerifierTest, test_that_bitvector_iterators_adheres_to_SearchIterator_requirements)
{
    {
        Verifier searchIteratorVerifier(false);
        searchIteratorVerifier.verify();
    }
    {
        Verifier searchIteratorVerifier(true);
        searchIteratorVerifier.verify();
    }
}

GTEST_MAIN_RUN_ALL_TESTS()

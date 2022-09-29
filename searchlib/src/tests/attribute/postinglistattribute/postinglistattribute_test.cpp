// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/document/update/arithmeticvalueupdate.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/postinglistattribute.h>
#include <vespa/searchlib/attribute/singlenumericpostattribute.h>
#include <vespa/searchlib/attribute/multinumericpostattribute.h>
#include <vespa/searchlib/attribute/singlestringpostattribute.h>
#include <vespa/searchlib/attribute/multistringpostattribute.h>
#include <vespa/searchlib/common/growablebitvector.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/compress.h>
#include <vespa/fastos/file.h>
#include <vespa/searchlib/attribute/enumstore.hpp>
#include <filesystem>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("postinglistattribute_test");

namespace {

vespalib::string tmp_dir("tmp");

}

using std::shared_ptr;

bool
FastOS_UNIX_File::Sync()
{
    return true;
}

namespace search {

using attribute::CollectionType;
using attribute::BasicType;
using attribute::Config;
using queryeval::PostingInfo;
using queryeval::MinMaxPostingInfo;
using search::attribute::SearchContext;
using search::fef::TermFieldMatchData;
using search::queryeval::SearchIterator;

using SearchContextPtr = std::unique_ptr<SearchContext>;
typedef std::unique_ptr<search::queryeval::SearchIterator> SearchBasePtr;

void
toStr(std::stringstream &ss, SearchIterator &it, TermFieldMatchData *md)
{
    it.initFullRange();
    it.seek(1u);
    bool first = true;
    while ( !it.isAtEnd()) {
        if (first) {
            first = false;
        } else {
            ss << ",";
        }
        ss << it.getDocId();
        if (md != nullptr) {
            it.unpack(it.getDocId());
            ss << "[w=" << md->begin()->getElementWeight() << "]";
        }
        it.seek(it.getDocId() + 1);
    }
}


bool
assertIterator(const std::string &exp, SearchIterator &it,
               TermFieldMatchData *md = nullptr)
{
    std::stringstream ss;
    toStr(ss, it, md);
    bool retval = true;
    EXPECT_EQ(exp, ss.str()) << (retval = false, "");
    return retval;
}

using AttributePtr = AttributeVector::SP;

class PostingListAttributeTest : public ::testing::Test
{
protected:
    typedef IntegerAttribute::largeint_t     largeint_t;
    typedef std::set<AttributeVector::DocId> DocSet;

    typedef SingleValueNumericPostingAttribute<
        EnumAttribute<IntegerAttributeTemplate<int32_t> > >
    Int32PostingListAttribute;
    typedef MultiValueNumericPostingAttribute<
        EnumAttribute<IntegerAttributeTemplate<int32_t> >,
        IEnumStore::AtomicIndex>
    Int32ArrayPostingListAttribute;
    typedef MultiValueNumericPostingAttribute<
        EnumAttribute<IntegerAttributeTemplate<int32_t> >,
        multivalue::WeightedValue<IEnumStore::AtomicIndex> >
    Int32WsetPostingListAttribute;

    typedef SingleValueNumericPostingAttribute<
        EnumAttribute<FloatingPointAttributeTemplate<float> > >
    FloatPostingListAttribute;
    typedef MultiValueNumericPostingAttribute<
        EnumAttribute<FloatingPointAttributeTemplate<float> >,
        IEnumStore::AtomicIndex>
    FloatArrayPostingListAttribute;
    typedef MultiValueNumericPostingAttribute<
        EnumAttribute<FloatingPointAttributeTemplate<float> >,
        multivalue::WeightedValue<IEnumStore::AtomicIndex> >
    FloatWsetPostingListAttribute;

    typedef SingleValueStringPostingAttribute StringPostingListAttribute;
    typedef ArrayStringPostingAttribute StringArrayPostingListAttribute;
    typedef WeightedSetStringPostingAttribute StringWsetPostingListAttribute;

    template <typename VectorType>
    void populate(VectorType &v);

    template <typename VectorType>
    VectorType & as(AttributePtr &v);

    IntegerAttribute & asInt(AttributePtr &v);
    StringAttribute & asString(AttributePtr &v);
    void buildTermQuery(std::vector<char> & buffer, const vespalib::string & index, const vespalib::string & term, bool prefix);

    template <typename V, typename T>
    SearchContextPtr getSearch(const V & vec, const T & term, bool prefix, const attribute::SearchContextParams & params=attribute::SearchContextParams());

    template <typename V>
    SearchContextPtr getSearch(const V & vec);

    template <typename V>
    SearchContextPtr getSearch2(const V & vec);

    bool assertSearch(const std::string &exp, StringAttribute &sa);
    bool assertSearch(const std::string &exp, StringAttribute &v, const std::string &key);
    bool assertSearch(const std::string &exp, IntegerAttribute &v, int32_t key);
    void addDocs(const AttributePtr & ptr, uint32_t numDocs);

    template <typename VectorType, typename BufferType, typename Range>
    void checkPostingList(const VectorType & vec, const std::vector<BufferType> & values, const Range & range);

    template <typename BufferType>
    void checkSearch(bool useBitVector, bool need_unpack, bool has_btree, bool has_bitvector, const AttributeVector & vec, const BufferType & term, uint32_t numHits, uint32_t docBegin, uint32_t docEnd);

    template <typename VectorType, typename BufferType>
    void testPostingList(const AttributePtr& ptr1, uint32_t numDocs, const std::vector<BufferType>& values);
    void testPostingList();
    void testPostingList(bool enable_only_bitvector);
    void testPostingList(bool enable_only_bitvector, uint32_t numDocs, uint32_t numUniqueValues);

    template <typename AttributeType, typename ValueType>
    void checkPostingList(AttributeType & vec, ValueType value, DocSet expected);
    template <typename AttributeType, typename ValueType>
    void checkNonExistantPostingList(AttributeType & vec, ValueType value);
    template <typename AttributeType, typename ValueType>
    void testArithmeticValueUpdate(const AttributePtr & ptr);
    void testArithmeticValueUpdate();

    template <typename VectorType, typename ValueType>
    void testReload(const AttributePtr & ptr1, const AttributePtr & ptr2, const ValueType & value);
    void testReload();

    template <typename VectorType>
    void testMinMax(AttributePtr &ptr1, uint32_t trimmed);

    template <typename VectorType>
    void testMinMax(AttributePtr &ptr1, AttributePtr &ptr2);

    void testMinMax();
    void testStringFold();
    void testDupValuesInIntArray();
    void testDupValuesInStringArray();
};

template <>
void
PostingListAttributeTest::populate<IntegerAttribute>(IntegerAttribute &v)
{
    for(size_t i(0), m(v.getNumDocs()); i < m; i++) {
        v.clearDoc(i);
        if (i == 0)
            continue;
        if (i == 9)
            continue;
        if (i == 7) {
            if (v.hasMultiValue()) {
                v.append(i, -42, 27);
                v.append(i, -43, 14);
                v.append(i, -42, -3);
            } else {
                EXPECT_TRUE( v.update(i, -43) );
            }
            v.commit();
            continue;
        }
        if (i == 20) {
            if (v.hasMultiValue()) {
                v.append(i, -42, 27);
                v.append(i, -43, 14);
                v.append(i, -42, -3);
            } else {
                EXPECT_TRUE( v.update(i, -43) );
            }
            v.commit();
            continue;
        }
        if (i == 25) {
            if (v.hasMultiValue()) {
                v.append(i, -42, 27);
                v.append(i, -43, 12);
                v.append(i, -42, -3);
            } else {
                EXPECT_TRUE( v.update(i, -43) );
            }
            v.commit();
            continue;
        }
        if (v.hasMultiValue()) {
            v.append(i, -42, 3);
        } else {
            v.update(i, -42);
        }
        v.commit();
    }
    v.commit();
}

template <>
void
PostingListAttributeTest::populate<StringAttribute>(StringAttribute &v)
{
    for(size_t i(0), m(v.getNumDocs()); i < m; i++) {
        v.clearDoc(i);
        if (i == 0)
            continue;
        if (i == 9)
            continue;
        if (i == 7) {
            if (v.hasMultiValue()) {
                v.append(i, "foo", 27);
                v.append(i, "bar", 14);
                v.append(i, "foo", -3);
            } else {
                EXPECT_TRUE( v.update(i, "bar") );
            }
            v.commit();
            continue;
        }
        if (i == 20) {
            if (v.hasMultiValue()) {
                v.append(i, "foo", 27);
                v.append(i, "bar", 14);
                v.append(i, "foo", -3);
            } else {
                EXPECT_TRUE( v.update(i, "bar") );
            }
            v.commit();
            continue;
        }
        if (i == 25) {
            if (v.hasMultiValue()) {
                v.append(i, "foo", 27);
                v.append(i, "bar", 12);
                v.append(i, "foo", -3);
            } else {
                EXPECT_TRUE( v.update(i, "bar") );
            }
            v.commit();
            continue;
        }
        if (v.hasMultiValue()) {
            v.append(i, "foo", 3);
        } else {
            v.update(i, "foo");
        }
        v.commit();
    }
}


template <typename VectorType>
VectorType &
PostingListAttributeTest::as(AttributePtr &v)
{
    VectorType *res = dynamic_cast<VectorType *>(v.get());
    assert(res != NULL);
    return *res;
}


IntegerAttribute &
PostingListAttributeTest::asInt(AttributePtr &v)
{
    return as<IntegerAttribute>(v);
}


StringAttribute &
PostingListAttributeTest::asString(AttributePtr &v)
{
    return as<StringAttribute>(v);
}


void
PostingListAttributeTest::buildTermQuery(std::vector<char> &buffer,
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


template <typename V, typename T>
SearchContextPtr
PostingListAttributeTest::getSearch(const V &vec, const T &term, bool prefix, const attribute::SearchContextParams & params)
{
    std::vector<char> query;
    vespalib::asciistream ss;
    ss << term;
    buildTermQuery(query, vec.getName(), ss.str(), prefix);

    return (static_cast<const AttributeVector &>(vec)).getSearch(vespalib::stringref(&query[0], query.size()), params);
}


template <>
SearchContextPtr
PostingListAttributeTest::getSearch<IntegerAttribute>(const IntegerAttribute &v)
{
    return getSearch<IntegerAttribute>(v, "[-42;-42]", false);
}


template <>
SearchContextPtr
PostingListAttributeTest::getSearch<StringAttribute>(const StringAttribute &v)
{
    return getSearch<StringAttribute, const vespalib::string &>(v, "foo", false);
}


template <>
SearchContextPtr
PostingListAttributeTest::getSearch2<IntegerAttribute>(const IntegerAttribute &v)
{
    return getSearch<IntegerAttribute>(v, "[-43;-43]", false);
}


template <>
SearchContextPtr
PostingListAttributeTest::getSearch2<StringAttribute>(const StringAttribute &v)
{
    return getSearch<StringAttribute, const vespalib::string &>(v, "bar", false);
}


bool
PostingListAttributeTest::assertSearch(const std::string &exp, StringAttribute &sa)
{
    TermFieldMatchData md;
    SearchContextPtr sc = getSearch<StringAttribute>(sa);
    sc->fetchPostings(queryeval::ExecuteInfo::TRUE);
    SearchBasePtr sb = sc->createIterator(&md, true);
    bool retval = true;
    EXPECT_TRUE(assertIterator(exp, *sb)) << (retval = false, "");
    return retval;
}


bool
PostingListAttributeTest::assertSearch(const std::string &exp, StringAttribute &sa, const std::string &key)
{
    TermFieldMatchData md;
    SearchContextPtr sc = getSearch<StringAttribute, std::string>(sa, key, false);
    sc->fetchPostings(queryeval::ExecuteInfo::TRUE);
    SearchBasePtr sb = sc->createIterator(&md, true);
    bool retval = true;
    EXPECT_TRUE(assertIterator(exp, *sb, &md)) << (retval = false, "");
    return retval;
}

bool
PostingListAttributeTest::assertSearch(const std::string &exp, IntegerAttribute &ia, int32_t key)
{
    TermFieldMatchData md;
    SearchContextPtr sc = getSearch<IntegerAttribute, int32_t>(ia, key, false);
    sc->fetchPostings(queryeval::ExecuteInfo::TRUE);
    SearchBasePtr sb = sc->createIterator(&md, true);
    bool retval = true;
    EXPECT_TRUE(assertIterator(exp, *sb, &md)) << (retval = false, "");
    return retval;
}


void
PostingListAttributeTest::addDocs(const AttributePtr & ptr, uint32_t numDocs)
{
    for (uint32_t i = 0; i < numDocs; ++i) {
        uint32_t doc;
        ASSERT_TRUE(ptr->addDoc(doc));
        ASSERT_TRUE(doc == i);
        ASSERT_TRUE(ptr->getNumDocs() == i + 1);
    }
    ASSERT_TRUE(ptr->getNumDocs() == numDocs);
}

class RangeAlpha {
private:
    uint32_t _part;
public:
    RangeAlpha(uint32_t part) : _part(part) { }
    uint32_t getBegin(uint32_t i) const { return i * _part; }
    uint32_t getEnd(uint32_t i) const { return (i + 1) * _part; }
};

class RangeBeta {
private:
    uint32_t _part;
    uint32_t _numValues;
public:
    RangeBeta(uint32_t part, uint32_t numValues) : _part(part), _numValues(numValues) { }
    uint32_t getBegin(uint32_t i) const { return (_numValues - 1 - i) * _part; }
    uint32_t getEnd(uint32_t i) const { return (_numValues - i) * _part; }
};

template <typename VectorType, typename BufferType, typename RangeGenerator>
void
PostingListAttributeTest::checkPostingList(const VectorType & vec, const std::vector<BufferType> & values,
                                           const RangeGenerator & range)
{
    const typename VectorType::EnumStore & enumStore = vec.getEnumStore();
    auto& dict = enumStore.get_dictionary();
    const typename VectorType::PostingList & postingList = vec.getPostingList();

    for (size_t i = 0; i < values.size(); ++i) {
        const uint32_t docBegin = range.getBegin(i);
        const uint32_t docEnd = range.getEnd(i);

        auto find_result = dict.find_posting_list(enumStore.make_comparator(values[i]), dict.get_frozen_root());
        ASSERT_TRUE(find_result.first.valid());
        bool has_bitvector = VectorType::PostingList::isBitVector(postingList.getTypeId(find_result.second));

        typename VectorType::PostingList::Iterator postings;
        postings = postingList.begin(find_result.second);
        uint32_t numHits(0);
        bool has_btree = postings.valid();
        if (postings.valid()) {
            uint32_t doc = docBegin;
            for (; postings.valid(); ++postings) {
                EXPECT_EQ(doc++, postings.getKey());
                numHits++;
            }
            EXPECT_EQ(doc, docEnd);
        } else {
            EXPECT_TRUE(has_bitvector && vec.getEnableOnlyBitVector());
            numHits = postingList.getBitVectorEntry(find_result.second)->_bv->reader().countTrueBits();
        }
        if (has_bitvector) {
            uint32_t doc = docBegin;
            uint32_t bv_num_hits = 0;
            auto& bv = postingList.getBitVectorEntry(find_result.second)->_bv->reader();
            for (auto lid = bv.getFirstTrueBit(); lid < bv.size(); lid = bv.getNextTrueBit(lid + 1)) {
                EXPECT_EQ(doc++, lid);
                ++bv_num_hits;
            }
            EXPECT_EQ(doc, docEnd);
            EXPECT_EQ(numHits, bv_num_hits);
        }
        checkSearch(false, true, has_btree, has_bitvector, vec, values[i], numHits, docBegin, docEnd);
        checkSearch(true, true, has_btree, has_bitvector, vec, values[i], numHits, docBegin, docEnd);
        checkSearch(false, false, has_btree, has_bitvector, vec, values[i], numHits, docBegin, docEnd);
    }
}


template <typename BufferType>
void
PostingListAttributeTest::checkSearch(bool useBitVector, bool need_unpack, bool has_btree, bool has_bitvector, const AttributeVector & vec, const BufferType & term, uint32_t numHits, uint32_t docBegin, uint32_t docEnd)
{
    SearchContextPtr sc = getSearch(vec, term, false, attribute::SearchContextParams().useBitVector(useBitVector));
    EXPECT_FALSE( ! sc );
    sc->fetchPostings(queryeval::ExecuteInfo::TRUE);
    size_t approx = sc->approximateHits();
    EXPECT_EQ(numHits, approx);
    if (docBegin == 0) {
        // Approximation does not know about the special 0
        // But the iterator does....
        numHits--;
        docBegin++;
    }
    TermFieldMatchData tfmd;
    if (!need_unpack) {
        tfmd.tagAsNotNeeded();
    }
    auto it = sc->createIterator(&tfmd, true);
    EXPECT_EQ((useBitVector || !has_btree || !need_unpack) && has_bitvector, it->isBitVector());
    it->initFullRange();
    EXPECT_TRUE(it->seekFirst(docBegin));
    EXPECT_EQ(docBegin, it->getDocId());
    size_t hits(0);
    uint32_t lastDocId = it->getDocId();
    while (! it->isAtEnd()) {
        lastDocId = it->getDocId();
        it->seek(lastDocId+1);
        hits++;
    }
    EXPECT_EQ(numHits, hits);
    EXPECT_GE(approx, hits);
    EXPECT_EQ(docEnd, lastDocId+1);
}


AttributePtr
create_attribute(const vespalib::stringref name, const Config& cfg)
{
    return AttributeFactory::createAttribute(tmp_dir + "/" + name, cfg);
}

AttributePtr
create_as(const AttributeVector& attr, const std::string& name_suffix)
{
    return create_attribute(attr.getName() + name_suffix, attr.getConfig());
}

template <typename VectorType, typename BufferType>
void
PostingListAttributeTest::testPostingList(const AttributePtr& ptr1, uint32_t numDocs,
                                          const std::vector<BufferType>& values)
{
    LOG(info, "testPostingList: vector '%s'", ptr1->getName().c_str());

    auto& vec1 = dynamic_cast<VectorType &>(*ptr1);
    addDocs(ptr1, numDocs);

    uint32_t part = numDocs / values.size();

    // insert values
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t idx = doc / part;
        EXPECT_TRUE(vec1.update(doc, values[idx]));
    }
    vec1.commit();

    // check posting list for correct content
    checkPostingList(vec1, values, RangeAlpha(part));

    // load and save vector
    auto ptr2 = create_as(*ptr1, "_2");
    ptr1->save(ptr2->getBaseFileName());
    ptr2->load();
    checkPostingList(static_cast<VectorType&>(*ptr2.get()), values, RangeAlpha(part));

    // insert values in another order
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t idx = values.size() - 1 - (doc / part);
        EXPECT_TRUE(vec1.update(doc, values[idx]));
    }
    vec1.commit();

    // check posting list again for correct content
    checkPostingList(vec1, values, RangeBeta(part, values.size()));

    // load and save vector
    auto ptr3 = create_as(*ptr1, "_3");
    ptr1->save(ptr3->getBaseFileName());
    ptr3->load();
    checkPostingList(static_cast<VectorType&>(*ptr3.get()), values, RangeBeta(part, values.size()));
}

void
PostingListAttributeTest::testPostingList()
{
    testPostingList(false);
    testPostingList(true);
}

void
PostingListAttributeTest::testPostingList(bool enable_only_bitvector)
{
    testPostingList(enable_only_bitvector, 1000, 50);
    testPostingList(enable_only_bitvector, 2000, 10); // This should force bitvector
}

void
PostingListAttributeTest::testPostingList(bool enable_only_bitvector, uint32_t numDocs, uint32_t numUniqueValues)
{

    { // IntegerAttribute
        std::vector<largeint_t> values;
        for (uint32_t i = 0; i < numUniqueValues; ++i) {
            values.push_back(i);
        }
        {
            Config cfg(Config(BasicType::INT32, CollectionType::SINGLE));
            cfg.setFastSearch(true);
            cfg.setEnableOnlyBitVector(enable_only_bitvector);
            AttributePtr ptr1 = create_attribute("sint32", cfg);
            testPostingList<Int32PostingListAttribute>(ptr1, numDocs, values);
        }
        {
            Config cfg(Config(BasicType::INT32, CollectionType::ARRAY));
            cfg.setFastSearch(true);
            cfg.setEnableOnlyBitVector(enable_only_bitvector);
            AttributePtr ptr1 = create_attribute("aint32", cfg);
            testPostingList<Int32ArrayPostingListAttribute>(ptr1, numDocs, values);
        }
        {
            Config cfg(Config(BasicType::INT32, CollectionType::WSET));
            cfg.setFastSearch(true);
            cfg.setEnableOnlyBitVector(enable_only_bitvector);
            AttributePtr ptr1 = create_attribute("wsint32", cfg);
            testPostingList<Int32WsetPostingListAttribute>(ptr1, numDocs, values);
        }
    }

    { // FloatingPointAttribute
        std::vector<double> values;
        for (uint32_t i = 0; i < numUniqueValues; ++i) {
            values.push_back(i);
        }
        {
            Config cfg(Config(BasicType::FLOAT, CollectionType::SINGLE));
            cfg.setFastSearch(true);
            cfg.setEnableOnlyBitVector(enable_only_bitvector);
            AttributePtr ptr1 = create_attribute("sfloat", cfg);
            testPostingList<FloatPostingListAttribute>(ptr1, numDocs, values);
        }
        {
            Config cfg(Config(BasicType::FLOAT, CollectionType::ARRAY));
            cfg.setFastSearch(true);
            cfg.setEnableOnlyBitVector(enable_only_bitvector);
            AttributePtr ptr1 = create_attribute("afloat", cfg);
            testPostingList<FloatArrayPostingListAttribute>(ptr1, numDocs, values);
        }
        {
            Config cfg(Config(BasicType::FLOAT, CollectionType::WSET));
            cfg.setFastSearch(true);
            cfg.setEnableOnlyBitVector(enable_only_bitvector);
            AttributePtr ptr1 = create_attribute("wsfloat", cfg);
            testPostingList<FloatWsetPostingListAttribute>(ptr1, numDocs, values);
        }
    }

    { // StringAttribute
        std::vector<vespalib::string> values;
        std::vector<const char *> charValues;
        values.reserve(numUniqueValues);
        charValues.reserve(numUniqueValues);
        for (uint32_t i = 0; i < numUniqueValues; ++i) {
            vespalib::asciistream ss;
            ss << "string" << i;
            values.push_back(ss.str());
            charValues.push_back(values.back().c_str());
        }
        {
            Config cfg(Config(BasicType::STRING, CollectionType::SINGLE));
            cfg.setFastSearch(true);
            cfg.setEnableOnlyBitVector(enable_only_bitvector);
            AttributePtr ptr1 = create_attribute("sstr", cfg);
            testPostingList<StringPostingListAttribute>(ptr1, numDocs, charValues);
        }
        {
            Config cfg(Config(BasicType::STRING, CollectionType::ARRAY));
            cfg.setFastSearch(true);
            cfg.setEnableOnlyBitVector(enable_only_bitvector);
            AttributePtr ptr1 = create_attribute("astr", cfg);
            testPostingList<StringArrayPostingListAttribute>(ptr1, numDocs, charValues);
        }
        {
            Config cfg(Config(BasicType::STRING, CollectionType::WSET));
            cfg.setFastSearch(true);
            cfg.setEnableOnlyBitVector(enable_only_bitvector);
            AttributePtr ptr1 = create_attribute("wsstr", cfg);
            testPostingList<StringWsetPostingListAttribute>(ptr1, numDocs, charValues);
        }
    }
}

template <typename AttributeType, typename ValueType>
void
PostingListAttributeTest::checkPostingList(AttributeType & vec, ValueType value, DocSet expected)
{
    const typename AttributeType::EnumStore & enumStore = vec.getEnumStore();
    auto& dict = enumStore.get_dictionary();
    const typename AttributeType::PostingList & postingList = vec.getPostingList();
    auto find_result = dict.find_posting_list(vec.getEnumStore().make_comparator(value), dict.get_frozen_root());
    ASSERT_TRUE(find_result.first.valid());

    typename AttributeType::PostingList::Iterator postings;
    postings = postingList.begin(find_result.second);

    DocSet::iterator docBegin = expected.begin();
    DocSet::iterator docEnd = expected.end();
    for (; postings.valid(); ++postings) {
        EXPECT_EQ(*docBegin++, postings.getKey());
    }
    EXPECT_TRUE(docBegin == docEnd);
}

template <typename AttributeType, typename ValueType>
void
PostingListAttributeTest::checkNonExistantPostingList(AttributeType & vec, ValueType value)
{
    auto& dict = vec.getEnumStore().get_dictionary();
    auto find_result = dict.find_posting_list(vec.getEnumStore().make_comparator(value), dict.get_frozen_root());
    EXPECT_TRUE(!find_result.first.valid());
}

template <typename AttributeType, typename ValueType>
void
PostingListAttributeTest::testArithmeticValueUpdate(const AttributePtr & ptr)
{
    LOG(info, "testArithmeticValueUpdate: vector '%s'", ptr->getName().c_str());

    typedef document::ArithmeticValueUpdate Arith;
    AttributeType & vec = static_cast<AttributeType &>(*ptr.get());

    addDocs(ptr, 4);

    uint32_t allDocs[] = {0, 1, 2, 3};
    checkNonExistantPostingList<AttributeType, ValueType>(vec, 0);

    for (uint32_t doc = 0; doc < 4; ++doc) {
        ASSERT_TRUE(vec.update(doc, 100));
    }
    ptr->commit();

    checkNonExistantPostingList<AttributeType, ValueType>(vec, 0);
    checkPostingList<AttributeType, ValueType>(vec, 100, DocSet(allDocs, allDocs + 4));

    EXPECT_TRUE(vec.apply(0, Arith(Arith::Add, 10)));
    EXPECT_TRUE(vec.apply(1, Arith(Arith::Sub, 10)));
    EXPECT_TRUE(vec.apply(2, Arith(Arith::Mul, 10)));
    EXPECT_TRUE(vec.apply(3, Arith(Arith::Div, 10)));
    ptr->commit();

    {
        uint32_t docs[] = {0};
        checkPostingList<AttributeType, ValueType>(vec, 110, DocSet(docs, docs + 1));
    }
    {
        uint32_t docs[] = {1};
        checkPostingList<AttributeType, ValueType>(vec, 90, DocSet(docs, docs + 1));
    }
    {
        uint32_t docs[] = {2};
        checkPostingList<AttributeType, ValueType>(vec, 1000, DocSet(docs, docs + 1));
    }
    {
        uint32_t docs[] = {3};
        checkPostingList<AttributeType, ValueType>(vec, 10, DocSet(docs, docs + 1));
    }


    // several inside a single commit
    for (uint32_t doc = 0; doc < 4; ++doc) {
        ASSERT_TRUE(vec.update(doc, 2000));
    }
    EXPECT_TRUE(vec.apply(0, Arith(Arith::Add, 10)));
    EXPECT_TRUE(vec.apply(0, Arith(Arith::Add, 10)));
    EXPECT_TRUE(vec.apply(1, Arith(Arith::Sub, 10)));
    EXPECT_TRUE(vec.apply(1, Arith(Arith::Sub, 10)));
    EXPECT_TRUE(vec.apply(2, Arith(Arith::Mul, 10)));
    EXPECT_TRUE(vec.apply(2, Arith(Arith::Mul, 10)));
    EXPECT_TRUE(vec.apply(3, Arith(Arith::Div, 10)));
    EXPECT_TRUE(vec.apply(3, Arith(Arith::Div, 10)));
    ptr->commit();

    {
        uint32_t docs[] = {0};
        checkPostingList<AttributeType, ValueType>(vec, 2020, DocSet(docs, docs + 1));
    }
    {
        uint32_t docs[] = {1};
        checkPostingList<AttributeType, ValueType>(vec, 1980, DocSet(docs, docs + 1));
    }
    {
        uint32_t docs[] = {2};
        checkPostingList<AttributeType, ValueType>(vec, 200000, DocSet(docs, docs + 1));
    }
    {
        uint32_t docs[] = {3};
        checkPostingList<AttributeType, ValueType>(vec, 20, DocSet(docs, docs + 1));
    }
    checkNonExistantPostingList<AttributeType, ValueType>(vec, 100);
    checkNonExistantPostingList<AttributeType, ValueType>(vec, 110);
    checkNonExistantPostingList<AttributeType, ValueType>(vec, 90);
    checkNonExistantPostingList<AttributeType, ValueType>(vec, 1000);
    checkNonExistantPostingList<AttributeType, ValueType>(vec, 10);
    checkNonExistantPostingList<AttributeType, ValueType>(vec, 2000);
    checkNonExistantPostingList<AttributeType, ValueType>(vec, 2010);
    checkNonExistantPostingList<AttributeType, ValueType>(vec, 1990);
    checkNonExistantPostingList<AttributeType, ValueType>(vec, 20000);
    checkNonExistantPostingList<AttributeType, ValueType>(vec, 200);
}

void
PostingListAttributeTest::testArithmeticValueUpdate()
{
    { // IntegerAttribute
        Config cfg(Config(BasicType::INT32, CollectionType::SINGLE));
        cfg.setFastSearch(true);
        AttributePtr ptr = create_attribute("sint32", cfg);
        testArithmeticValueUpdate<Int32PostingListAttribute, largeint_t>(ptr);
    }

    { // FloatingPointAttribute
        Config cfg(Config(BasicType::FLOAT, CollectionType::SINGLE));
        cfg.setFastSearch(true);
        AttributePtr ptr = create_attribute("sfloat", cfg);
        testArithmeticValueUpdate<FloatPostingListAttribute, double>(ptr);
    }
}


template <typename VectorType, typename ValueType>
void
PostingListAttributeTest::testReload(const AttributePtr & ptr1, const AttributePtr & ptr2, const ValueType & value)
{
    LOG(info, "testReload: vector '%s'", ptr1->getName().c_str());

    VectorType & vec1 = static_cast<VectorType &>(*ptr1.get());

    addDocs(ptr1, 5);
    for (uint32_t doc = 0; doc < 5; ++doc) {
        EXPECT_TRUE(vec1.update(doc, value));
    }
    ptr1->commit();

    ASSERT_TRUE(ptr1->save(ptr2->getBaseFileName()));
    ASSERT_TRUE(ptr2->load());

    EXPECT_TRUE(ptr2->getNumDocs() == 5);
    ValueType buffer[1];
    for (uint32_t doc = 0; doc < 5; ++doc) {
        EXPECT_TRUE(ptr2->get(doc, buffer, 1) == 1);
        EXPECT_EQ(buffer[0], value);
    }
}

void
PostingListAttributeTest::testReload()
{
    { // IntegerAttribute
        Config cfg(Config(BasicType::INT32, CollectionType::SINGLE));
        cfg.setFastSearch(true);
        {
            AttributePtr ptr1 = create_attribute("sint32_1", cfg);
            AttributePtr ptr2 = create_attribute("sint32_2", cfg);
            testReload<Int32PostingListAttribute, largeint_t>(ptr1, ptr2, 100);
        }
        {
            AttributePtr ptr1 = create_attribute("sint32_1", cfg);
            AttributePtr ptr2 = create_attribute("sint32_2", cfg);
            testReload<Int32PostingListAttribute, largeint_t>(ptr1, ptr2, 0);
        }
    }

    { // FloatingPointAttribute
        Config cfg(Config(BasicType::FLOAT, CollectionType::SINGLE));
        cfg.setFastSearch(true);
        {
            AttributePtr ptr1 = create_attribute("sfloat_1", cfg);
            AttributePtr ptr2 = create_attribute("sfloat_2", cfg);
            testReload<FloatPostingListAttribute, double>(ptr1, ptr2, 100);
        }
        {
            AttributePtr ptr1 = create_attribute("sfloat_1", cfg);
            AttributePtr ptr2 = create_attribute("sfloat_2", cfg);
            testReload<FloatPostingListAttribute, double>(ptr1, ptr2, 0);
        }
    }

    { // StringAttribute
        Config cfg(Config(BasicType::STRING, CollectionType::SINGLE));
        cfg.setFastSearch(true);
        {
            AttributePtr ptr1 = create_attribute("sstr_1", cfg);
            AttributePtr ptr2 = create_attribute("sstr_2", cfg);
            testReload<StringPostingListAttribute, vespalib::string>(ptr1, ptr2, "unique");
        }
        {
            AttributePtr ptr1 = create_attribute("sstr_1", cfg);
            AttributePtr ptr2 = create_attribute("sstr_2", cfg);
            testReload<StringPostingListAttribute, vespalib::string>(ptr1, ptr2, "");
        }
    }
}

template <typename VectorType>
void
PostingListAttributeTest::testMinMax(AttributePtr &ptr1, uint32_t trimmed)
{
    TermFieldMatchData md;
    SearchContextPtr sc = getSearch<VectorType>(as<VectorType>(ptr1));
    sc->fetchPostings(queryeval::ExecuteInfo::TRUE);
    SearchBasePtr sb = sc->createIterator(&md, true);
    sb->initFullRange();

    const PostingInfo *pi = sb->getPostingInfo();
    ASSERT_TRUE(pi != nullptr);
    const MinMaxPostingInfo *mmpi = dynamic_cast<const MinMaxPostingInfo *>(pi);
    ASSERT_TRUE(mmpi != nullptr);

    if (ptr1->hasMultiValue()) {
        if (trimmed == 2u) {
            EXPECT_EQ(3, mmpi->getMinWeight());
        } else {
            EXPECT_EQ(-3, mmpi->getMinWeight());
        }
        EXPECT_EQ(3, mmpi->getMaxWeight());
    } else {
        EXPECT_EQ(1, mmpi->getMinWeight());
        EXPECT_EQ(1, mmpi->getMaxWeight());
    }

    sb->seek(1u);
    EXPECT_EQ(1u, sb->getDocId());

    sc = getSearch2<VectorType>(as<VectorType>(ptr1));
    sc->fetchPostings(queryeval::ExecuteInfo::TRUE);
    sb = sc->createIterator(&md, true);
    sb->initFullRange();

    pi = sb->getPostingInfo();
    if (trimmed == 2) {
        ASSERT_TRUE(pi == nullptr);
    } else {
        ASSERT_TRUE(pi != nullptr);
        mmpi = dynamic_cast<const MinMaxPostingInfo *>(pi);
        ASSERT_TRUE(mmpi != nullptr);

        if (ptr1->hasMultiValue()) {
            if (trimmed == 0) {
                EXPECT_EQ(12, mmpi->getMinWeight());
            } else {
                EXPECT_EQ(14, mmpi->getMinWeight());
            }
            EXPECT_EQ(14, mmpi->getMaxWeight());
        } else {
            EXPECT_EQ(1, mmpi->getMinWeight());
            EXPECT_EQ(1, mmpi->getMaxWeight());
        }
    }

    sb->seek(1u);
    if (trimmed == 2u) {
        EXPECT_TRUE(sb->isAtEnd());
    } else {
        EXPECT_EQ(7u, sb->getDocId());
    }
}

template <typename VectorType>
void
PostingListAttributeTest::testMinMax(AttributePtr &ptr1, AttributePtr &ptr2)
{
    uint32_t numDocs = 100;
    addDocs(ptr1, numDocs);
    populate(as<VectorType>(ptr1));
    
    {
        SCOPED_TRACE("after populate");
        testMinMax<VectorType>(ptr1, 0u);
    }
    ASSERT_TRUE(ptr1->save(ptr2->getBaseFileName()));
    ASSERT_TRUE(ptr2->load());
    {
        SCOPED_TRACE("after save and load");
        testMinMax<VectorType>(ptr2, 0u);
    }

    ptr2->clearDoc(20);
    ptr2->clearDoc(25);
    ptr2->commit();
    {
        SCOPED_TRACE("after 1 trim round");
        testMinMax<VectorType>(ptr2, 1u);
    }

    ptr2->clearDoc(7);
    ptr2->commit();
    SCOPED_TRACE("after 2 trim rounds");
    testMinMax<VectorType>(ptr2, 2u);
    
}

void
PostingListAttributeTest::testMinMax()
{
    {
        Config cfg(Config(BasicType::INT32, CollectionType::SINGLE));
        cfg.setFastSearch(true);
        AttributePtr ptr1 = create_attribute("sint32_1", cfg);
        AttributePtr ptr2 = create_attribute("sint32_2", cfg);
        testMinMax<IntegerAttribute>(ptr1, ptr2);
    }
    {
        Config cfg(Config(BasicType::INT32, CollectionType::WSET));
        cfg.setFastSearch(true);
        AttributePtr ptr1 = create_attribute("wsint32_1", cfg);
        AttributePtr ptr2 = create_attribute("wsint32_2", cfg);
        testMinMax<IntegerAttribute>(ptr1, ptr2);
    }
    {
        Config cfg(Config(BasicType::STRING, CollectionType::SINGLE));
        cfg.setFastSearch(true);
        AttributePtr ptr1 = create_attribute("sstr_1", cfg);
        AttributePtr ptr2 = create_attribute("sstr_2", cfg);
        testMinMax<StringAttribute>(ptr1, ptr2);
    }
    {
        Config cfg(Config(BasicType::STRING, CollectionType::WSET));
        cfg.setFastSearch(true);
        AttributePtr ptr1 = create_attribute("wsstr_1", cfg);
        AttributePtr ptr2 = create_attribute("wsstr_2", cfg);
        testMinMax<StringAttribute>(ptr1, ptr2);
    }
}


void
PostingListAttributeTest::testStringFold()
{
    Config cfg(Config(BasicType::STRING, CollectionType::SINGLE));
    cfg.setFastSearch(true);
    AttributePtr ptr1 = create_attribute("sstr_1", cfg);

    addDocs(ptr1, 6);

    StringAttribute &sa(asString(ptr1));
    
    sa.update(1, "a");
    sa.commit();
    sa.update(3, "FOo");
    sa.commit();
    sa.update(4, "foo");
    sa.commit();
    sa.update(5, "z");
    sa.commit();

    EXPECT_TRUE(assertSearch("3,4", sa));

    sa.update(2, "FOO");
    sa.commit();

    EXPECT_TRUE(assertSearch("2,3,4", sa));

    sa.update(4, "");
    sa.commit();

    EXPECT_TRUE(assertSearch("2,3", sa));

    sa.update(2, "");
    sa.commit();

    EXPECT_TRUE(assertSearch("3", sa));

    sa.update(3, "");
    sa.commit();

    EXPECT_TRUE(assertSearch("", sa));
}

void
PostingListAttributeTest::testDupValuesInIntArray()
{
    Config cfg(Config(BasicType::INT32, CollectionType::ARRAY));
    cfg.setFastSearch(true);
    AttributePtr ptr1 = create_attribute("aint32_3", cfg);
    addDocs(ptr1, 6);

    IntegerAttribute &ia(asInt(ptr1));

    ia.append(1, 1, 1);
    ia.append(1, 1, 1);
    ia.append(2, 1, 1);
    ia.commit();
    EXPECT_TRUE(assertSearch("1[w=2],2[w=1]", ia, 1));

    ia.clearDoc(1);
    ia.append(1, 1, 1);
    ia.clearDoc(2);
    ia.append(2, 1, 1);
    ia.append(2, 1, 1);
    ia.commit();
    EXPECT_TRUE(assertSearch("1[w=1],2[w=2]", ia, 1));
}

void
PostingListAttributeTest::testDupValuesInStringArray()
{
    Config cfg(Config(BasicType::STRING, CollectionType::ARRAY));
    cfg.setFastSearch(true);
    AttributePtr ptr1 = create_attribute("astr_3", cfg);
    addDocs(ptr1, 6);

    StringAttribute &sa(asString(ptr1));

    sa.append(1, "foo", 1);
    sa.append(1, "foo", 1);
    sa.append(2, "foo", 1);
    sa.append(3, "bar", 1);
    sa.append(3, "BAR", 1);
    sa.append(4, "bar", 1);
    sa.commit();
    EXPECT_TRUE(assertSearch("1[w=2],2[w=1]", sa, "foo"));
    EXPECT_TRUE(assertSearch("3[w=2],4[w=1]", sa, "bar"));

    sa.clearDoc(1);
    sa.append(1, "foo", 1);
    sa.clearDoc(2);
    sa.append(2, "foo", 1);
    sa.append(2, "foo", 1);
    sa.clearDoc(3);
    sa.append(3, "bar", 1);
    sa.clearDoc(4);
    sa.append(4, "bar", 1);
    sa.append(4, "BAR", 1);
    sa.commit();
    EXPECT_TRUE(assertSearch("1[w=1],2[w=2]", sa, "foo"));
    EXPECT_TRUE(assertSearch("3[w=1],4[w=2]", sa, "bar"));
}


TEST_F(PostingListAttributeTest, test_posting_list)
{
    testPostingList();
}

TEST_F(PostingListAttributeTest, test_arithmetic_value_update)
{
    testArithmeticValueUpdate();
}

TEST_F(PostingListAttributeTest, test_reload)
{
    testReload();
}

TEST_F(PostingListAttributeTest, test_min_max)
{
    testMinMax();
}

TEST_F(PostingListAttributeTest, test_string_fold)
{
    testStringFold();
}

TEST_F(PostingListAttributeTest, test_dup_values_in_int_array)
{
    testDupValuesInIntArray();
}

TEST_F(PostingListAttributeTest, test_dup_values_in_string_array)
{
    testDupValuesInStringArray();
}

}

int
main(int argc, char* argv[])
{
    ::testing::InitGoogleTest(&argc, argv);
    std::filesystem::remove_all(std::filesystem::path(tmp_dir));
    std::filesystem::create_directories(std::filesystem::path(tmp_dir));
    int retval = RUN_ALL_TESTS();
    std::filesystem::remove_all(std::filesystem::path(tmp_dir));
    return retval;
}

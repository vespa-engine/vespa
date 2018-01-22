// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeiterators.h>
#include <vespa/searchlib/attribute/flagattribute.h>
#include <vespa/searchlib/attribute/singlenumericattribute.h>
#include <vespa/searchlib/attribute/singlestringattribute.h>
#include <vespa/searchlib/attribute/multistringattribute.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/queryeval/hitcollector.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/compress.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/query/queryterm.h>
#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/attribute/attributevector.hpp>

#include <vespa/log/log.h>
LOG_SETUP("searchcontext_test");

namespace search {

namespace {

bool
isUnsignedSmallIntAttribute(const AttributeVector &a)
{
    switch (a.getBasicType())
    {
    case attribute::BasicType::UINT1:
    case attribute::BasicType::UINT2:
    case attribute::BasicType::UINT4:
        return true;
    default:
        return false;
    }
}

}

typedef AttributeVector::SP AttributePtr;
typedef std::unique_ptr<AttributeVector::SearchContext> SearchContextPtr;
typedef AttributeVector::SearchContext SearchContext;
using attribute::Config;
using attribute::BasicType;
using attribute::CollectionType;
typedef AttributeVector::largeint_t largeint_t;
typedef queryeval::SearchIterator::UP SearchBasePtr;
typedef std::unique_ptr<ResultSet> ResultSetPtr;

using queryeval::HitCollector;
using queryeval::SearchIterator;
using fef::MatchData;
using fef::TermFieldMatchData;
using fef::TermFieldMatchDataArray;
using fef::TermFieldMatchDataPosition;

class DocSet : public std::set<uint32_t>
{
public:
    DocSet();
    ~DocSet();
    DocSet(const uint32_t *b, const uint32_t *e) : std::set<uint32_t>(b, e) {}
    DocSet & put(const uint32_t &v) {
        insert(v);
        return *this;
    }
};

DocSet::DocSet() : std::set<uint32_t>() {}
DocSet::~DocSet() {}

template <typename V, typename T>
class PostingList
{
private:
    V * _vec;
    T _value;
    DocSet _hits;

public:
    PostingList(V & vec, T value);
    ~PostingList();
    const V & getAttribute() const { return *_vec; }
    V & getAttribute() { return *_vec; }
    const T & getValue() const { return _value; }
    DocSet & getHits() { return _hits; }
    const DocSet & getHits() const { return _hits; }
    uint32_t getHitCount() const { return _hits.size(); }
};

template <typename V, typename T>
PostingList<V, T>::PostingList(V & vec, T value) : _vec(&vec), _value(value), _hits() {}

template <typename V, typename T>
PostingList<V, T>::~PostingList() {}

class DocRange
{
public:
    uint32_t start;
    uint32_t end;
    DocRange(uint32_t start_, uint32_t end_) : start(start_), end(end_) {}
};

class SearchContextTest : public vespalib::TestApp
{
public:
    // helper functions
    static void addReservedDoc(AttributeVector &ptr);
    static void addDocs(AttributeVector & ptr, uint32_t numDocs);
    template <typename V, typename T>
    static SearchContextPtr getSearch(const V & vec, const T & term, QueryTermSimple::SearchTerm termType=QueryTermSimple::WORD);
private:
    typedef std::map<vespalib::string, Config> ConfigMap;
    // Map of all config objects
    ConfigMap _integerCfg;
    ConfigMap _floatCfg;
    ConfigMap _stringCfg;


    template <typename T>
    void fillVector(std::vector<T> & values, size_t numValues);
    template <typename V, typename T>
    void fillAttribute(V & vec, const std::vector<T> & values);
    template <typename V, typename T>
    void resetAttribute(V & vec, const T & value);
    template <typename V, typename T>
    void fillPostingList(PostingList<V, T> & pl, const DocRange & range);
    template <typename V, typename T>
    void fillPostingList(PostingList<V, T> & pl);
    static void buildTermQuery(std::vector<char> & buffer, const vespalib::string & index, const vespalib::string & term,
                               QueryTermSimple::SearchTerm termType=QueryTermSimple::WORD);

    ResultSetPtr performSearch(SearchIterator & sb, uint32_t numDocs);
    template <typename V, typename T>
    ResultSetPtr performSearch(const V & vec, const T & term, QueryTermSimple::SearchTerm termType=QueryTermSimple::WORD);
    template <typename V>
    void performSearch(const V & vec, const vespalib::string & term,
                       const DocSet & expected, QueryTermSimple::SearchTerm termType);
    void checkResultSet(const ResultSet & rs, const DocSet & exp, bool bitVector);

    template<typename T, typename A>
    void testSearchIterator(T key, const vespalib::string &keyAsString, const ConfigMap &cfgs);
    void testSearchIteratorConformance();
    // test search functionality
    template <typename V, typename T>
    void testFind(const PostingList<V, T> & first);

    template <typename V, typename T>
    void testSearch(V & attribute, uint32_t numDocs, const std::vector<T> & values);
    template<typename T, typename A>
    void testSearch(const ConfigMap & cfgs);
    template <typename V, typename T>
    void testMultiValueSearchHelper(V & vec, const std::vector<T> & values);
    template <typename V, typename T>
    void testMultiValueSearch(V & first, V & second, const std::vector<T> & values);
    void testSearch();

    class IteratorTester {
    public:
        virtual bool matches(const SearchIterator & base) const = 0;
        virtual ~IteratorTester() { }
    };
    class AttributeIteratorTester : public IteratorTester
    {
    public:
        virtual bool matches(const SearchIterator & base) const override {
            return dynamic_cast<const AttributeIterator *>(&base) != NULL;
        }
    };
    class FlagAttributeIteratorTester : public IteratorTester
    {
    public:
        virtual bool matches(const SearchIterator & base) const override {
            return (dynamic_cast<const FlagAttributeIterator *>(&base) != NULL) ||
                   (dynamic_cast<const BitVectorIterator *>(&base) != NULL) ||
                   (dynamic_cast<const queryeval::EmptySearch *>(&base) != NULL);
        }
    };
    class AttributePostingListIteratorTester : public IteratorTester
    {
    public:
        virtual bool matches(const SearchIterator & base) const override {
            return dynamic_cast<const AttributePostingListIterator *>(&base) != NULL ||
                dynamic_cast<const queryeval::EmptySearch *>(&base) != NULL;
                
        }
    };


    // test search iterator functionality
    void testStrictSearchIterator(SearchContext & threeHits,
                                  SearchContext & noHits,
                                  const IteratorTester & typeTester);
    void testNonStrictSearchIterator(SearchContext & threeHits,
                                     SearchContext & noHits,
                                     const IteratorTester & typeTester);
    void fillForSearchIteratorTest(IntegerAttribute * ia);
    void fillForSemiNibbleSearchIteratorTest(IntegerAttribute * ia);
    void testSearchIterator();


    // test search iterator unpacking
    void fillForSearchIteratorUnpackingTest(IntegerAttribute * ia, bool extra);
    void testSearchIteratorUnpacking(const AttributePtr & ptr,
                                     SearchContext & sc,
                                     bool extra,
                                     bool strict);
    void testSearchIteratorUnpacking();


    // test range search
    template <typename VectorType>
    void performRangeSearch(const VectorType & vec, const vespalib::string & term,
                            const DocSet & expected);
    template <typename VectorType, typename ValueType>
    void testRangeSearch(const AttributePtr & ptr, uint32_t numDocs, std::vector<ValueType> values);
    void testRangeSearch();
    void testRangeSearchLimited();


    // test case insensitive search
    void performCaseInsensitiveSearch(const StringAttribute & vec, const vespalib::string & term,
                                      const DocSet & expected);
    void testCaseInsensitiveSearch(const AttributePtr & ptr);
    void testCaseInsensitiveSearch();
    void testRegexSearch(const AttributePtr & ptr);
    void testRegexSearch();


    // test prefix search
    void performPrefixSearch(const StringAttribute & vec, const vespalib::string & term,
                             const DocSet & expected, QueryTermSimple::SearchTerm termType);
    void testPrefixSearch(const AttributePtr & ptr);
    void testPrefixSearch();

    // test that search is working after clear doc
    template <typename VectorType, typename ValueType>
    void requireThatSearchIsWorkingAfterClearDoc(const vespalib::string & name, const Config & cfg,
                                                 ValueType startValue, const vespalib::string & term);
    void requireThatSearchIsWorkingAfterClearDoc();

    // test that search is working after load and clear doc
    template <typename VectorType, typename ValueType>
    void requireThatSearchIsWorkingAfterLoadAndClearDoc(const vespalib::string & name, const Config & cfg,
                                                        ValueType startValue, ValueType defaultValue,
                                                        const vespalib::string & term);
    void requireThatSearchIsWorkingAfterLoadAndClearDoc();

    template <typename VectorType, typename ValueType>
    void requireThatSearchIsWorkingAfterUpdates(const vespalib::string & name,
                                                const Config & cfg,
                                                ValueType value1,
                                                ValueType value2);
    void requireThatSearchIsWorkingAfterUpdates();

    void requireThatFlagAttributeIsWorkingWhenNewDocsAreAdded();

    template <typename VectorType, typename ValueType>
    void requireThatInvalidSearchTermGivesZeroHits(const vespalib::string & name,
                                                   const Config & cfg,
                                                   ValueType value);
    void requireThatInvalidSearchTermGivesZeroHits();

    void requireThatFlagAttributeHandlesTheByteRange();

    void requireThatOutOfBoundsSearchTermGivesZeroHits(const vespalib::string &name,
                                                       const Config &cfg,
                                                       int64_t maxValue);
    void requireThatOutOfBoundsSearchTermGivesZeroHits();

    // init maps with config objects
    void initIntegerConfig();
    void initFloatConfig();
    void initStringConfig();

public:
    SearchContextTest();
    ~SearchContextTest();
    int Main() override;
};


void
SearchContextTest::addReservedDoc(AttributeVector &ptr)
{
    ptr.addReservedDoc();
}


void
SearchContextTest::addDocs(AttributeVector & ptr, uint32_t numDocs)
{
    uint32_t docId;
    addReservedDoc(ptr);
    for (uint32_t i = 1; i <= numDocs; ++i) {
        ptr.addDoc(docId);
        EXPECT_EQUAL(docId, i);
    }
    ASSERT_TRUE(ptr.getNumDocs() == numDocs + 1);
}

template <typename T>
void
SearchContextTest::fillVector(std::vector<T> & values, size_t numValues)
{
    values.clear();
    values.reserve(numValues);
    for (size_t i = 1; i <= numValues; ++i) {
        values.push_back(static_cast<T>(i));
    }
}

template <>
void
SearchContextTest::fillVector(std::vector<vespalib::string> & values, size_t numValues)
{
    values.clear();
    values.reserve(numValues);
    for (size_t i = 0; i < numValues; ++i) {
        vespalib::asciistream ss;
        ss << "string" << (i < 10 ? "0" : "") << i;
        values.push_back(ss.str());
    }
}

template <typename V, typename T>
void
SearchContextTest::fillAttribute(V & vec, const std::vector<T> & values)
{
    for (uint32_t doc = 1; doc < vec.getNumDocs(); ++doc) {
        ASSERT_TRUE(doc < vec.getNumDocs());
        vec.clearDoc(doc);
        uint32_t valueCount = doc % (values.size() + 1);
        for (uint32_t i = 0; i < valueCount; ++i) {
            // std::cout << "append(" << doc << ", " << values[i] << ")" << std::endl;
            EXPECT_TRUE(vec.append(doc, values[i], 1));
        }
    }
    vec.commit(true);
}

template <typename V, typename T>
void
SearchContextTest::resetAttribute(V & vec, const T & value)
{
    for (uint32_t doc = 1; doc < vec.getNumDocs(); ++doc) {
        ASSERT_TRUE(doc < vec.getNumDocs());
        EXPECT_TRUE(vec.update(doc, value));
    }
    vec.commit(true);
}

template <typename V, typename T>
void
SearchContextTest::fillPostingList(PostingList<V, T> & pl, const DocRange & range)
{
    pl.getHits().clear();
    for (uint32_t doc = range.start; doc < range.end; ++doc) {
        ASSERT_TRUE(doc < pl.getAttribute().getNumDocs());
        EXPECT_TRUE(pl.getAttribute().update(doc, pl.getValue()));
        pl.getHits().insert(doc);
    }
    pl.getAttribute().commit(true);
}

template <typename V, typename T>
void
SearchContextTest::fillPostingList(PostingList<V, T> & pl)
{
    AttributeVector & vec = dynamic_cast<AttributeVector &>(pl.getAttribute());
    pl.getHits().clear();
    uint32_t sz = vec.getMaxValueCount();
    T * buf = new T[sz];
    for (uint32_t doc = 1; doc < vec.getNumDocs(); ++doc) {
        uint32_t valueCount = vec.get(doc, buf, sz);
        EXPECT_TRUE(valueCount <= sz);
        for (uint32_t i = 0; i < valueCount; ++i) {
            if (buf[i] == pl.getValue()) {
                //std::cout << "hit for doc(" << doc << "): buf[" << i << "] (=" << buf[i] << ") == " << pl.getValue() << std::endl;
                pl.getHits().insert(doc);
                break;
            }
        }
    }
    delete [] buf;
}

void
SearchContextTest::buildTermQuery(std::vector<char> & buffer, const vespalib::string & index, const vespalib::string & term, QueryTermSimple::SearchTerm termType)
{
    uint32_t indexLen = index.size();
    uint32_t termLen = term.size();
    uint32_t queryPacketSize = 1 + 2 * 4 + indexLen + termLen;
    uint32_t p = 0;
    buffer.resize(queryPacketSize);
    switch (termType) {
      case QueryTermSimple::PREFIXTERM: buffer[p++] = ParseItem::ITEM_PREFIXTERM; break;
      case QueryTermSimple::REGEXP: buffer[p++] = ParseItem::ITEM_REGEXP; break;
      default:
         buffer[p++] = ParseItem::ITEM_TERM;
         break;
    }
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
SearchContextTest::getSearch(const V & vec, const T & term, QueryTermSimple::SearchTerm termType)
{
    std::vector<char> query;
    vespalib::asciistream ss;
    ss << term;
    buildTermQuery(query, vec.getName(), ss.str(), termType);

    return (dynamic_cast<const AttributeVector &>(vec)).
        getSearch(vespalib::stringref(&query[0], query.size()),
                  attribute::SearchContextParams());
}

ResultSetPtr
SearchContextTest::performSearch(SearchIterator & sb, uint32_t numDocs)
{
    HitCollector hc(numDocs, numDocs, 0);
    sb.initRange(1, numDocs);
    // assume strict toplevel search object located at start
    for (sb.seek(1u); ! sb.isAtEnd(); sb.seek(sb.getDocId() + 1)) {
        hc.addHit(sb.getDocId(), 0.0);
    }
    return hc.getResultSet();
}

template <typename V, typename T>
ResultSetPtr
SearchContextTest::performSearch(const V & vec, const T & term, QueryTermSimple::SearchTerm termType)
{
    TermFieldMatchData dummy;
    SearchContextPtr sc = getSearch(vec, term, termType);
    sc->fetchPostings(true);
    SearchBasePtr sb = sc->createIterator(&dummy, true);
    ResultSetPtr rs = performSearch(*sb, vec.getNumDocs());
    return rs;
}

template <typename V>
void
SearchContextTest::performSearch(const V & vec, const vespalib::string & term,
                                 const DocSet & expected, QueryTermSimple::SearchTerm termType)
{
#if 0
    std::cout << "performSearch[" << term << "]: {";
    std::copy(expected.begin(), expected.end(), std::ostream_iterator<uint32_t>(std::cout, ", "));
    std::cout << "}, prefix(" << (prefix ? "true" : "false") << ")" << std::endl;
#endif
    { // strict search iterator
        ResultSetPtr rs = performSearch(vec, term, termType);
        checkResultSet(*rs, expected, false);
    }
}

void
SearchContextTest::checkResultSet(const ResultSet & rs, const DocSet & expected, bool bitVector)
{
    EXPECT_EQUAL(rs.getNumHits(), expected.size());
    if (bitVector) {
        const BitVector * vec = rs.getBitOverflow();
        if (expected.size() != 0) {
            ASSERT_TRUE(vec != NULL);
            for (const auto & expect : expected) {
                EXPECT_TRUE(vec->testBit(expect));
            }
        }
    } else {
        const RankedHit * array = rs.getArray();
        if (expected.size() != 0) {
            ASSERT_TRUE(array != NULL);
            uint32_t i = 0;
            for (DocSet::const_iterator iter = expected.begin();
                 iter != expected.end(); ++iter, ++i)
            {
                EXPECT_TRUE(array[i]._docId == *iter);
            }
        }
    }
}


//-----------------------------------------------------------------------------
// Test search functionality
//-----------------------------------------------------------------------------
template <typename V, typename T>
void
SearchContextTest::testFind(const PostingList<V, T> & pl)
{
    { // strict search iterator
        SearchContextPtr sc = getSearch(pl.getAttribute(), pl.getValue());
        sc->fetchPostings(true);
        TermFieldMatchData dummy;
        SearchBasePtr sb = sc->createIterator(&dummy, true);
        ResultSetPtr rs = performSearch(*sb, pl.getAttribute().getNumDocs());
        checkResultSet(*rs, pl.getHits(), false);
    }
}

template <typename V, typename T>
void
SearchContextTest::testSearch(V & attribute, uint32_t numDocs, const std::vector<T> & values)
{
    LOG(info, "testSearch: vector '%s' with %u documents and %lu unique values",
        attribute.getName().c_str(), numDocs, static_cast<unsigned long>(values.size()));

    // fill attribute vectors
    addDocs(attribute, numDocs);

    std::vector<PostingList<V, T> > lists;

    // fill posting lists
    ASSERT_TRUE((attribute.getNumDocs() - 1) % values.size() == 0);
    uint32_t hitCount = attribute.getNumDocs() / values.size();
    for (uint32_t i = 0; i < values.size(); ++i) {
        // for each value a range with hitCount documents will hit on that value
        lists.push_back(PostingList<V, T>(attribute, values[i]));
        fillPostingList(lists.back(), DocRange(i * hitCount + 1, (i + 1) * hitCount + 1));
    }

    // test find()
    for (const auto & list : lists) {
        testFind(list);
    }
}

template <typename V, typename T>
void
SearchContextTest::testMultiValueSearchHelper(V & vec, const std::vector<T> & values)
{
    std::vector<PostingList<V, T> > lists;

    // fill posting lists based on attribute content
    for (const T & value : values) {
        lists.push_back(PostingList<V, T>(vec, value));
        fillPostingList(lists.back());
    }

    // test find()
    for (const auto & list : lists) {
        //std::cout << "testFind(lists[" << i << "]): value = " << lists[i].getValue()
        //                                            << ", hit count = " << lists[i].getHitCount() << std::endl;
        testFind(list);
    }
}

template <typename V, typename T>
void
SearchContextTest::testMultiValueSearch(V & first, V & second, const std::vector<T> & values)
{
    addDocs(first, second.getNumDocs());
    LOG(info, "testMultiValueSearch: vector '%s' with %u documents and %lu unique values",
        first.getName().c_str(), first.getNumDocs(), static_cast<unsigned long>(values.size()));

    fillAttribute(first, values);

    testMultiValueSearchHelper(first, values);

    ASSERT_TRUE(first.saveAs(second.getBaseFileName()));
    ASSERT_TRUE(second.load());

    testMultiValueSearchHelper(second, values);

    size_t sz = values.size();
    ASSERT_TRUE(sz > 2);
    std::vector<T> subset;
    // values[sz - 2] is not used  -> 0 hits
    // values[sz - 1] is used once -> 1 hit
    for (size_t i = 0; i < sz - 2; ++i) {
        subset.push_back(values[i]);
    }

    fillAttribute(first, subset);

    ASSERT_TRUE(1u < first.getNumDocs());
    EXPECT_TRUE(first.append(1u, values[sz - 1], 1));
    first.commit(true);

    testMultiValueSearchHelper(first, values);

    ASSERT_TRUE(first.saveAs(second.getBaseFileName()));
    ASSERT_TRUE(second.load());

    testMultiValueSearchHelper(second, values);
}

template<typename T, typename A>
void SearchContextTest::testSearch(const ConfigMap & cfgs) {
    uint32_t numDocs = 100;
    uint32_t numUniques = 20;
    std::vector<T> values;
    fillVector(values, numUniques);
    for (const auto & cfg : cfgs) {
        AttributePtr second = AttributeFactory::createAttribute(cfg.first + "-2", cfg.second);
        testSearch(*(dynamic_cast<A *>(second.get())), numDocs, values);
        if (second->hasMultiValue()) {
            AttributePtr first = AttributeFactory::createAttribute(cfg.first + "-1", cfg.second);
            testMultiValueSearch(*(dynamic_cast<A *>(first.get())),
                                 *(dynamic_cast<A *>(second.get())), values);
        }
    }
}


template<typename T, typename A>
class Verifier : public search::test::SearchIteratorVerifier {
public:
    Verifier(T key, const vespalib::string & keyAsString, const vespalib::string & name, const Config & cfg);
    ~Verifier();
    SearchIterator::UP create(bool strict) const override {
        return _sc->createIterator(&_dummy, strict);
    }
private:
    mutable TermFieldMatchData _dummy;
    AttributePtr _attribute;
    SearchContextPtr _sc;
};

template<typename T, typename A>
Verifier<T, A>::Verifier(T key, const vespalib::string & keyAsString, const vespalib::string & name, const Config & cfg)
    :_attribute(AttributeFactory::createAttribute(name + "-initrange", cfg)),
     _sc()
{
    SearchContextTest::addDocs(*_attribute, getDocIdLimit());
    for (uint32_t doc : getExpectedDocIds()) {
        EXPECT_TRUE(nullptr != dynamic_cast<A *>(_attribute.get()));
        EXPECT_TRUE(dynamic_cast<A *>(_attribute.get())->update(doc, key));
    }
    _attribute->commit(true);
    _sc = SearchContextTest::getSearch(*_attribute, keyAsString);
    ASSERT_TRUE(_sc->valid());
    _sc->fetchPostings(true);
}

template<typename T, typename A>
Verifier<T, A>::~Verifier() {}

template<typename T, typename A>
void SearchContextTest::testSearchIterator(T key, const vespalib::string &keyAsString, const ConfigMap &cfgs) {

    for (const auto & cfg : cfgs) {
        Verifier<T, A> verifier(key, keyAsString, cfg.first, cfg.second);
        verifier.verify();
    }
}

void SearchContextTest::testSearchIteratorConformance() {
    testSearchIterator<AttributeVector::largeint_t, IntegerAttribute>(42, "42", _integerCfg);
    testSearchIterator<double, FloatingPointAttribute>(42.42, "42.42", _floatCfg);
    testSearchIterator<vespalib::string, StringAttribute>("any-key", "any-key", _stringCfg);
}

void
SearchContextTest::testSearch()
{
    const uint32_t numDocs = 100;
    const uint32_t numUniques = 20;

    { // IntegerAttribute
        for (const auto & cfg : _integerCfg) {
            AttributePtr attribute = AttributeFactory::createAttribute(cfg.first + "-3", cfg.second);
            SearchContextPtr sc = getSearch(*attribute, "100");
            ASSERT_TRUE(sc->valid());
            sc = getSearch(*attribute, "1A0");
            EXPECT_FALSE( sc->valid() );
        }


        { // CollectionType::ARRAY Flags.
            std::vector<AttributeVector::largeint_t> values;
            fillVector(values, numUniques);
            Config cfg(BasicType::INT8, CollectionType::ARRAY);
            cfg.setFastSearch(true);
            AttributePtr second = AttributeFactory::createAttribute("flags-2", cfg);
            testSearch(*(dynamic_cast<IntegerAttribute *>(second.get())), numDocs, values);
            AttributePtr first = AttributeFactory::createAttribute("flags-1", cfg);
            testMultiValueSearch(*(dynamic_cast<IntegerAttribute *>(first.get())),
                                 *(dynamic_cast<IntegerAttribute *>(second.get())), values);
        }
    }

    { // FloatingPointAttribute
        for (const auto & cfg : _floatCfg) {
            AttributePtr attribute = AttributeFactory::createAttribute(cfg.first + "-3", cfg.second);
            SearchContextPtr sc = getSearch(*attribute, "100");
            ASSERT_TRUE(sc->valid());
            sc = getSearch(*attribute, "7.3");
            ASSERT_TRUE( sc->valid() );
            sc = getSearch(*attribute, "1A0");
            EXPECT_FALSE( sc->valid() );
        }
    }

    testSearch<AttributeVector::largeint_t, IntegerAttribute>(_integerCfg);
    testSearch<double, FloatingPointAttribute>(_floatCfg);
    testSearch<vespalib::string, StringAttribute>(_stringCfg);
}

//-----------------------------------------------------------------------------
// Test search iterator functionality
//-----------------------------------------------------------------------------
void
SearchContextTest::testStrictSearchIterator(SearchContext & threeHits,
                                            SearchContext & noHits,
                                            const IteratorTester & typeTester)
{
    TermFieldMatchData dummy;
    { // search for value with 3 hits
        threeHits.fetchPostings(true);
        SearchBasePtr sb = threeHits.createIterator(&dummy, true);
        sb->initRange(1, threeHits.attribute().getCommittedDocIdLimit());
        EXPECT_TRUE(typeTester.matches(*sb));
        EXPECT_TRUE(sb->getDocId() == sb->beginId() ||
                    sb->getDocId() == 1u);
        EXPECT_TRUE(sb->seek(1));
        EXPECT_EQUAL(sb->getDocId(), 1u);
        EXPECT_TRUE(!sb->seek(2));
        EXPECT_EQUAL(sb->getDocId(), 3u);
        EXPECT_TRUE(sb->seek(3));
        EXPECT_EQUAL(sb->getDocId(), 3u);
        EXPECT_TRUE(!sb->seek(4));
        EXPECT_EQUAL(sb->getDocId(), 5u);
        EXPECT_TRUE(sb->seek(5));
        EXPECT_EQUAL(sb->getDocId(), 5u);
        EXPECT_TRUE(!sb->seek(6));
        EXPECT_TRUE(sb->isAtEnd());
    }

    { // search for value with no hits
        noHits.fetchPostings(true);
        SearchBasePtr sb = noHits.createIterator(&dummy, true);
        sb->initRange(1, noHits.attribute().getCommittedDocIdLimit());
        ASSERT_TRUE(typeTester.matches(*sb));
        EXPECT_TRUE(sb->getDocId() == sb->beginId() ||
                   sb->isAtEnd());
        EXPECT_TRUE(!sb->seek(1));
        EXPECT_TRUE(sb->isAtEnd());
    }
}

void
SearchContextTest::testNonStrictSearchIterator(SearchContext & threeHits,
                                               SearchContext & noHits,
                                               const IteratorTester & typeTester)
{
    TermFieldMatchData dummy;
    { // search for value with three hits
        threeHits.fetchPostings(false);
        SearchBasePtr sb = threeHits.createIterator(&dummy, false);
        sb->initRange(1, threeHits.attribute().getCommittedDocIdLimit());
        EXPECT_TRUE(typeTester.matches(*sb));
        EXPECT_TRUE(sb->seek(1));
        EXPECT_EQUAL(sb->getDocId(), 1u);
        EXPECT_TRUE(!sb->seek(2));
        EXPECT_EQUAL(sb->getDocId(), 1u);
        EXPECT_TRUE(sb->seek(3));
        EXPECT_EQUAL(sb->getDocId(), 3u);
        EXPECT_TRUE(!sb->seek(4));
        EXPECT_EQUAL(sb->getDocId(), 3u);
        EXPECT_TRUE(sb->seek(5));
        EXPECT_EQUAL(sb->getDocId(), 5u);
        EXPECT_TRUE(!sb->seek(6));
        EXPECT_TRUE(sb->getDocId() == 5u || sb->isAtEnd());
    }
    { // search for value with no hits
        noHits.fetchPostings(false);
        SearchBasePtr sb = noHits.createIterator(&dummy, false);
        sb->initRange(1, threeHits.attribute().getCommittedDocIdLimit());

        EXPECT_TRUE(typeTester.matches(*sb));
        EXPECT_TRUE(sb->getDocId() == sb->beginId() ||
                    sb->isAtEnd());
        EXPECT_TRUE(!sb->seek(1));
        EXPECT_NOT_EQUAL(sb->getDocId(), 1u);
        EXPECT_TRUE(!sb->seek(6));
        EXPECT_NOT_EQUAL(sb->getDocId(), 6u);
    }
}

void
SearchContextTest::fillForSearchIteratorTest(IntegerAttribute * ia)
{
    addReservedDoc(*ia);
    ia->addDocs(5);
    ia->update(1, 10);
    ia->update(2, 20);
    ia->update(3, 10);
    ia->update(4, 20);
    ia->update(5, 10);
    ia->commit(true);
}

void
SearchContextTest::fillForSemiNibbleSearchIteratorTest(IntegerAttribute * ia)
{
    addReservedDoc(*ia);
    ia->addDocs(5);
    ia->update(1, 1);
    ia->update(2, 2);
    ia->update(3, 1);
    ia->update(4, 2);
    ia->update(5, 1);
    ia->commit(true);
}

void
SearchContextTest::testSearchIterator()
{
    {
        Config cfg(BasicType::INT32, CollectionType::SINGLE);
        AttributePtr ptr = AttributeFactory::createAttribute("s-int32", cfg);
        fillForSearchIteratorTest(dynamic_cast<IntegerAttribute *>(ptr.get()));

        SearchContextPtr threeHits = getSearch(*ptr.get(), 10);
        SearchContextPtr noHits = getSearch(*ptr.get(), 30);
        AttributeIteratorTester tester;
        testStrictSearchIterator(*threeHits, *noHits, tester);
        threeHits = getSearch(*ptr.get(), 10);
        noHits = getSearch(*ptr.get(), 30);
        testNonStrictSearchIterator(*threeHits, *noHits, tester);
    }
    {
        Config cfg(BasicType::UINT2, CollectionType::SINGLE);
        AttributePtr ptr = AttributeFactory::createAttribute("s-uint2", cfg);
        fillForSemiNibbleSearchIteratorTest(dynamic_cast<IntegerAttribute *>
                (ptr.get()));

        SearchContextPtr threeHits = getSearch(*ptr.get(), 1);
        SearchContextPtr noHits = getSearch(*ptr.get(), 3);
        AttributeIteratorTester tester;
        testStrictSearchIterator(*threeHits, *noHits, tester);
        threeHits = getSearch(*ptr.get(), 1);
        noHits = getSearch(*ptr.get(), 3);
        testNonStrictSearchIterator(*threeHits, *noHits, tester);
    }
    {
        Config cfg(BasicType::INT32, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        AttributePtr ptr = AttributeFactory::createAttribute("sfs-int32", cfg);
        fillForSearchIteratorTest(dynamic_cast<IntegerAttribute *>(ptr.get()));

        SearchContextPtr threeHits = getSearch(*ptr.get(), 10);
        SearchContextPtr noHits = getSearch(*ptr.get(), 30);
        AttributePostingListIteratorTester tester;
        testStrictSearchIterator(*threeHits, *noHits, tester);
    }
    {
        Config cfg(BasicType::STRING, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        AttributePtr ptr = AttributeFactory::createAttribute("sfs-string", cfg);
        StringAttribute * sa = dynamic_cast<StringAttribute *>(ptr.get());
        addReservedDoc(*ptr);
        ptr->addDocs(5);
        sa->update(1, "three");
        sa->update(2, "two");
        sa->update(3, "three");
        sa->update(4, "two");
        sa->update(5, "three");
        ptr->commit(true);

        SearchContextPtr threeHits = getSearch(*ptr.get(), "three");
        SearchContextPtr noHits = getSearch(*ptr.get(), "none");
        AttributePostingListIteratorTester tester;
        testStrictSearchIterator(*threeHits, *noHits, tester);
    }
    {
        Config cfg(BasicType::INT8, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        AttributePtr ptr = AttributeFactory::createAttribute("flags", cfg);
        fillForSearchIteratorTest(dynamic_cast<IntegerAttribute *>(ptr.get()));

        SearchContextPtr threeHits = getSearch(*ptr.get(), 10);
        SearchContextPtr noHits = getSearch(*ptr.get(), 30);
        FlagAttributeIteratorTester tester;
        testStrictSearchIterator(*threeHits, *noHits, tester);
        threeHits = getSearch(*ptr.get(), 10);
        noHits = getSearch(*ptr.get(), 30);
        testNonStrictSearchIterator(*threeHits, *noHits, tester);
    }
}



//-----------------------------------------------------------------------------
// Test search iterator unpacking
//-----------------------------------------------------------------------------
void
SearchContextTest::fillForSearchIteratorUnpackingTest(IntegerAttribute * ia,
                                                      bool extra)
{
    addReservedDoc(*ia);
    ia->addDocs(3);
    if (ia->getCollectionType() == CollectionType::SINGLE) {
        ia->update(1, 10);
        ia->update(2, 10);
        ia->update(3, 10);
    } else if (ia->getCollectionType() == CollectionType::ARRAY) {
        ia->append(1, 10, 1);
        ia->append(2, 10, 1);
        ia->append(2, 10, 1);
        ia->append(3, 10, 1);
        ia->append(3, 10, 1);
        ia->append(3, 10, 1);
    } else { // WEIGHTED SET
        ia->append(1, 10, -50);
        ia->append(2, 10, 0);
        ia->append(3, 10, 50);
    }
    ia->commit(true);
    if (!extra)
        return;
    ia->addDocs(20);
    for (uint32_t d = 4; d < 24; ++d) {
        if (ia->getCollectionType() == CollectionType::SINGLE)
            ia->update(d, 10);
        else
            ia->append(d, 10, 1);
    }
    ia->commit(true);
}

void
SearchContextTest::testSearchIteratorUnpacking(const AttributePtr & attr,
                                               SearchContext & sc,
                                               bool extra,
                                               bool strict)
{
    LOG(info,
        "testSearchIteratorUnpacking: vector '%s'", attr->getName().c_str());

    TermFieldMatchData md;
    md.reset(100);

    TermFieldMatchDataPosition pos;
    pos.setElementWeight(100);
    md.appendPosition(pos);

    sc.fetchPostings(strict);
    SearchBasePtr sb = sc.createIterator(&md, strict);
    sb->initFullRange();

    std::vector<int32_t> weights(3);
    if (attr->getCollectionType() == CollectionType::SINGLE ||
        (attr->getCollectionType() == CollectionType::ARRAY && attr->getBasicType() == BasicType::INT8))
    {
        weights[0] = 1;
        weights[1] = 1;
        weights[2] = 1;
    } else if (attr->getCollectionType() == CollectionType::ARRAY) {
        weights[0] = 1;
        weights[1] = 2;
        weights[2] = 3;
    } else {
        weights[0] = -50;
        weights[1] = 0;
        weights[2] = 50;
    }

    // unpack and check weights
    sb->unpack(1);
    EXPECT_EQUAL(sb->getDocId(), 1u);
    EXPECT_EQUAL(md.getDocId(), 1u);
    EXPECT_EQUAL(md.getWeight(), weights[0]);

    sb->unpack(2);
    EXPECT_EQUAL(sb->getDocId(), 2u);
    EXPECT_EQUAL(md.getDocId(), 2u);
    EXPECT_EQUAL(md.getWeight(), weights[1]);

    sb->unpack(3);
    EXPECT_EQUAL(sb->getDocId(), 3u);
    EXPECT_EQUAL(md.getDocId(), 3u);
    EXPECT_EQUAL(md.getWeight(), weights[2]);
    if (extra) {
        sb->unpack(4);
        EXPECT_EQUAL(sb->getDocId(), 4u);
        EXPECT_EQUAL(md.getDocId(), 4u);
        EXPECT_EQUAL(md.getWeight(), 1);
    }
}

void
SearchContextTest::testSearchIteratorUnpacking()
{
    std::vector<std::pair<vespalib::string, Config> > config;

    {
        Config cfg(BasicType::INT32, CollectionType::SINGLE);
        config.emplace_back("s-int32", cfg);
    }
    {
        Config cfg(BasicType::UINT4, CollectionType::SINGLE);
        config.emplace_back("s-uint4", cfg);
    }
    {
        Config cfg(BasicType::INT32, CollectionType::ARRAY);
        config.emplace_back("a-int32", cfg);
    }
    {
        Config cfg(BasicType::INT32, CollectionType::WSET);
        config.emplace_back("w-int32", cfg);
    }
    {
        Config cfg(BasicType::INT32, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        config.emplace_back("sfs-int32", cfg);
    }
    {
        Config cfg(BasicType::INT32, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        config.emplace_back("afs-int32", cfg);
    }
    {
        Config cfg(BasicType::INT32, CollectionType::WSET);
        cfg.setFastSearch(true);
        config.emplace_back("wfs-int32", cfg);
    }
    {
        Config cfg(BasicType::INT8, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        config.emplace_back("flags", cfg);
    }

    for (const auto & cfg : config) {
        AttributePtr ptr = AttributeFactory::createAttribute(cfg.first, cfg.second);
        fillForSearchIteratorUnpackingTest(dynamic_cast<IntegerAttribute *>(ptr.get()), false);
        SearchContextPtr sc = getSearch(*ptr.get(), 10);
        testSearchIteratorUnpacking(ptr, *sc, false, true);
        sc = getSearch(*ptr.get(), 10);
        testSearchIteratorUnpacking(ptr, *sc, false, false);
        if (cfg.second.fastSearch()) {
            AttributePtr ptr2 = AttributeFactory::createAttribute(cfg.first + "-extra", cfg.second);
            fillForSearchIteratorUnpackingTest(dynamic_cast<IntegerAttribute *>(ptr2.get()), true);
            SearchContextPtr sc2 = getSearch(*ptr2.get(), 10);
            testSearchIteratorUnpacking(ptr2, *sc2, true, true);
            sc2 = getSearch(*ptr2.get(), 10);
            testSearchIteratorUnpacking(ptr2, *sc2, true, false);
        }
    }
}



//-----------------------------------------------------------------------------
// Test range search
//-----------------------------------------------------------------------------

template <typename VectorType>
void
SearchContextTest::performRangeSearch(const VectorType & vec, const vespalib::string & term,
                                      const DocSet & expected)
{
    performSearch(vec, term, expected, QueryTermSimple::WORD);
}

template <typename VectorType, typename ValueType>
void
SearchContextTest::testRangeSearch(const AttributePtr & ptr, uint32_t numDocs, std::vector<ValueType> values)
{
    LOG(info, "testRangeSearch: vector '%s'", ptr->getName().c_str());

    VectorType & vec = dynamic_cast<VectorType &>(*ptr.get());

    addDocs(vec, numDocs);

    std::map<ValueType, DocSet> postingList;

    uint32_t docCnt = 0;
    for (uint32_t i = 0; i < values.size() && docCnt < numDocs; i+=2) {
        //std::cout << "postingList[" << values[i] << "]: {";
        for (uint32_t j = 0; j < (i + 1) && docCnt < numDocs; ++j, ++docCnt) {
            EXPECT_TRUE(vec.update(docCnt + 1u, values[i]));
            postingList[values[i]].insert(docCnt + 1u);
            //std::cout << docCnt << ", ";
        }
        //std::cout << "}" << std::endl;
    }
    ptr->commit(true);
    uint32_t smallHits = 0;
    ValueType zeroValue = 0;
    bool smallUInt = isUnsignedSmallIntAttribute(vec);
    if (smallUInt) {
        for (uint32_t i = docCnt ; i < numDocs; ++i) {
            postingList[zeroValue].insert(i + 1u);
            ++smallHits;
        }
    }

    // test less than ("<a")
    for (uint32_t i = 0; i < values.size(); ++i) {
        vespalib::asciistream ss;
        ss << "<" << values[i];
        DocSet expected;
        if (smallUInt) {
            expected.insert(postingList[zeroValue].begin(),
                            postingList[zeroValue].end());
        }
        for (uint32_t j = 0; j < i; ++j) {
            expected.insert(postingList[values[j]].begin(), postingList[values[j]].end());
        }
        performRangeSearch(vec, ss.str(), expected);
    }

    // test greater than (">a")
    for (uint32_t i = 0; i < values.size(); ++i) {
        vespalib::asciistream ss;
        ss << ">" << values[i];
        DocSet expected;
        for (uint32_t j = i + 1; j < values.size(); ++j) {
            expected.insert(postingList[values[j]].begin(), postingList[values[j]].end());
        }
        performRangeSearch(vec, ss.str(), expected);
    }

    // test range ("[a;b]")
    for (uint32_t i = 0; i < values.size(); ++i) {
        for (uint32_t j = 0; j < values.size(); ++j) { // illegal range when j < i
            vespalib::asciistream ss;
            ss << "[" << values[i] << ";" << values[j] << "]";
            DocSet expected;
            for (uint32_t k = i; k < j + 1; ++k) {
                expected.insert(postingList[values[k]].begin(), postingList[values[k]].end());
            }
            performRangeSearch(vec, ss.str(), expected);
        }
    }

    { // test large range
        vespalib::asciistream ss;
        ss << "[" << (values.front() - 1) << ";" << (values.back() + 1) << "]";
        DocSet expected;
        for (uint32_t doc = 0; doc < numDocs; ++doc) {
            expected.insert(doc + 1);
        }
        performRangeSearch(vec, ss.str(), expected);
    }
}

void
SearchContextTest::testRangeSearchLimited()
{
    largeint_t VALUES [] = {0,1,1,2,3,4,5,6,7,8,9,9,10 };
    std::vector<largeint_t> values(VALUES, VALUES+sizeof(VALUES)/sizeof(VALUES[0]));
    Config cfg(BasicType::INT32, CollectionType::SINGLE);
    cfg.setFastSearch(true);
    AttributePtr ptr = AttributeFactory::createAttribute("limited-int32", cfg);
    IntegerAttribute & vec = dynamic_cast<IntegerAttribute &>(*ptr);
    addDocs(vec, values.size());
    for (size_t i(1); i < values.size(); i++) {
        EXPECT_TRUE(vec.update(i, values[i]));
    }
    ptr->commit(true);

    DocSet expected;
    for (size_t i(1); i < 12; i++) {
        expected.put(i);
    }
    performRangeSearch(vec, "[1;9]", expected);
    performRangeSearch(vec, "[1;9;100]", expected);
    performRangeSearch(vec, "[1;9;-100]", expected);
    expected.clear();
    expected.put(3);
    performRangeSearch(vec, "<1;3>", expected);
    expected.put(4);
    performRangeSearch(vec, "<1;3]", expected);
    expected.clear();
    expected.put(1).put(2).put(3);
    performRangeSearch(vec, "[1;3>", expected);
    expected.put(4);
    performRangeSearch(vec, "[1;3]", expected);
    expected.clear();
    expected.put(1).put(2);
    performRangeSearch(vec, "[1;9;1]", expected);
    performRangeSearch(vec, "[1;9;2]", expected);
    expected.put(3);
    performRangeSearch(vec, "[1;9;3]", expected);
    expected.clear();
    expected.put(10).put(11);
    performRangeSearch(vec, "[1;9;-1]", expected);
    performRangeSearch(vec, "[1;9;-2]", expected);
    expected.put(9);
    performRangeSearch(vec, "[1;9;-3]", expected);
    performRangeSearch(vec, "[1;9;-3]", expected);

    expected.clear();
    for (size_t i(1); i < 13; i++) {
        expected.put(i);
    }
    performRangeSearch(vec, "[;;100]", expected);
    performRangeSearch(vec, "[;;-100]", expected);

    expected.clear();
    expected.put(1).put(2);
    performRangeSearch(vec, "[;;1]", expected);
    expected.clear();
    expected.put(12);
    performRangeSearch(vec, "[;;-1]", expected);
}

void
SearchContextTest::testRangeSearch()
{
    const uint32_t numDocs = 100;
    const uint32_t numValues = 20;
    const uint32_t numNibbleValues = 9;

    { // IntegerAttribute
        std::vector<largeint_t> values;
        std::vector<largeint_t> nibbleValues;
        largeint_t start = 1;

        for (uint32_t i = 0; i < numValues; ++i) {
            values.push_back(start + i);
        }
        for (uint32_t i = 0; i < numNibbleValues; ++i) {
            nibbleValues.push_back(start + i);
        }

        for (const auto & cfg : _integerCfg) {
            AttributePtr ptr = AttributeFactory::createAttribute(cfg.first, cfg.second);
            testRangeSearch<IntegerAttribute, largeint_t>(ptr, numDocs, values);
        }
        { // CollectionType::ARRAY Flags.
            Config cfg(BasicType::INT8, CollectionType::ARRAY);
            cfg.setFastSearch(true);
            AttributePtr ptr = AttributeFactory::createAttribute("flags", cfg);
            testRangeSearch<IntegerAttribute, largeint_t>(ptr, numDocs, values);
        }
        {
            Config cfg(BasicType::UINT4, CollectionType::SINGLE);
            AttributePtr ptr = AttributeFactory::createAttribute("s-uint4", cfg);
            testRangeSearch<IntegerAttribute, largeint_t>(ptr, numDocs, nibbleValues);
        }
    }

    { // FloatingPointAttribute
        std::vector<double> values;
        double start = 1;

        for (uint32_t i = 0; i < numValues; ++i) {
            values.push_back(start + i);
        }

        for (const auto & cfg : _floatCfg) {
            AttributePtr ptr = AttributeFactory::createAttribute(cfg.first, cfg.second);
            testRangeSearch<FloatingPointAttribute, double>(ptr, numDocs, values);
        }
    }
}


//-----------------------------------------------------------------------------
// Test case insensitive search
//-----------------------------------------------------------------------------

void
SearchContextTest::performCaseInsensitiveSearch(const StringAttribute & vec, const vespalib::string & term,
                                                const DocSet & expected)
{
    performSearch(vec, term, expected, QueryTermSimple::WORD);
}

void
SearchContextTest::testCaseInsensitiveSearch(const AttributePtr & ptr)
{
    LOG(info, "testCaseInsensitiveSearch: vector '%s'", ptr->getName().c_str());

    StringAttribute & vec = dynamic_cast<StringAttribute &>(*ptr.get());

    uint32_t numDocs = 5 * 5;
    addDocs(*ptr.get(), numDocs);

    const char * terms[][5] = {
    {"lower", "upper", "firstupper", "mixedcase", "intermixedcase"}, // lower
    {"LOWER", "UPPER", "FIRSTUPPER", "MIXEDCASE", "INTERMIXEDCASE"}, // upper
    {"Lower", "Upper", "Firstupper", "Mixedcase", "Intermixedcase"}, // firstUpper
    {"Lower", "Upper", "FirstUpper", "MixedCase", "InterMixedCase"}, // mixedCase
    {"lower", "upper", "firstUpper", "mixedCase", "interMixedCase"}, // interMixedCase
    };

    uint32_t doc = 1;
    for (uint32_t j = 0; j < 5; ++j) {
        for (uint32_t i = 0; i < 5; ++i) {
            ASSERT_TRUE(doc < vec.getNumDocs());
            EXPECT_TRUE(vec.update(doc++, terms[i][j]));
        }
    }

    ptr->commit(true);

    const char * buffer[1];
    doc = 1;
    for (uint32_t j = 0; j < 5; ++j) {
        for (uint32_t i = 0; i < 5; ++i) {
            EXPECT_EQUAL(ptr->get(doc++, buffer, 1), uint32_t(1));
            EXPECT_EQUAL(vespalib::string(buffer[0]), vespalib::string(terms[i][j]));
        }
    }

    DocSet empty;
    for (uint32_t j = 0; j < 5; ++j) {
        DocSet expected;
        for (doc = j * 5 + 1; doc < (j + 1) * 5 + 1; ++doc) {
            expected.insert(doc);
        }
        // for non-posting attributes only lower case search terms should give hits
        performCaseInsensitiveSearch(vec, terms[0][j], expected);

        if (ptr->getConfig().fastSearch()) {
            for (uint32_t i = 1; i < 5; ++i) {
                performCaseInsensitiveSearch(vec, terms[i][j], expected);
            }
        } else {
            for (uint32_t i = 1; i < 4; ++i) {
                performCaseInsensitiveSearch(vec, terms[i][j], empty);
            }
        }
    }
    performCaseInsensitiveSearch(vec, "none", empty);
    performCaseInsensitiveSearch(vec, "NONE", empty);
    performCaseInsensitiveSearch(vec, "None", empty);
}

void
SearchContextTest::testRegexSearch(const AttributePtr & ptr)
{
    LOG(info, "testRegexSearch: vector '%s'", ptr->getName().c_str());

    StringAttribute & vec = dynamic_cast<StringAttribute &>(*ptr.get());

    uint32_t numDocs = 6;
    addDocs(*ptr.get(), numDocs);

    const char * strings [] = {"abc1def", "abc2Def", "abc2def", "abc4def", "abc5def", "abc6def"};
    std::vector<const char *> terms = { "abc", "bc2de" };

    for (uint32_t doc = 1; doc < numDocs + 1; ++doc) {
        ASSERT_TRUE(doc < vec.getNumDocs());
        EXPECT_TRUE(vec.update(doc, strings[doc - 1]));
    }

    ptr->commit(true);

    std::vector<DocSet> expected;
    DocSet empty;
    {
        uint32_t docs[] = {1, 2, 3, 4, 5, 6};
        expected.push_back(DocSet(docs, docs + 6)); // "abc"
    }
    {
        uint32_t docs[] = {2, 3};
        expected.push_back(DocSet(docs, docs + 2)); // "bc2de"
    }

    for (uint32_t i = 0; i < terms.size(); ++i) {
        performSearch(vec, terms[i], expected[i], QueryTermSimple::REGEXP);
        performSearch(vec, terms[i], empty, QueryTermSimple::WORD);
    }
}


void
SearchContextTest::testCaseInsensitiveSearch()
{
    for (const auto & cfg : _stringCfg) {
        testCaseInsensitiveSearch(AttributeFactory::createAttribute(cfg.first, cfg.second));
    }
}

void
SearchContextTest::testRegexSearch()
{
    for (const auto & cfg : _stringCfg) {
        testRegexSearch(AttributeFactory::createAttribute(cfg.first, cfg.second));
    }
}


//-----------------------------------------------------------------------------
// Test prefix search
//-----------------------------------------------------------------------------

void
SearchContextTest::performPrefixSearch(const StringAttribute & vec, const vespalib::string & term,
                                       const DocSet & expected, QueryTermSimple::SearchTerm termType)
{
    performSearch(vec, term, expected, termType);
}

void
SearchContextTest::testPrefixSearch(const AttributePtr & ptr)
{
    LOG(info, "testPrefixSearch: vector '%s'", ptr->getName().c_str());

    StringAttribute & vec = dynamic_cast<StringAttribute &>(*ptr.get());

    uint32_t numDocs = 6;
    addDocs(*ptr.get(), numDocs);

    const char * strings [] = {"prefixsearch", "PREFIXSEARCH", "PrefixSearch", "precommit", "PRECOMMIT", "PreCommit"};
    const char * terms[][3] = {{"pre", "PRE", "Pre"}, {"pref", "PREF", "Pref"},
        {"prec", "PREC", "PreC"}, {"prex", "PREX", "Prex"}};

    for (uint32_t doc = 1; doc < numDocs + 1; ++doc) {
        ASSERT_TRUE(doc < vec.getNumDocs());
        EXPECT_TRUE(vec.update(doc, strings[doc - 1]));
    }

    ptr->commit(true);

    std::vector<DocSet> expected;
    DocSet empty;
    {
        uint32_t docs[] = {1, 2, 3, 4, 5, 6};
        expected.push_back(DocSet(docs, docs + 6)); // "pre"
    }
    {
        uint32_t docs[] = {1, 2, 3};
        expected.push_back(DocSet(docs, docs + 3)); // "pref"
    }
    {
        uint32_t docs[] = {4, 5, 6};
        expected.push_back(DocSet(docs, docs + 3)); // "prec"
    }
    expected.push_back(DocSet()); // "prex"

    for (uint32_t i = 0; i < 4; ++i) {
        for (uint32_t j = 0; j < 3; ++j) {
            if (j == 0 || ptr->getConfig().fastSearch()) {
                performPrefixSearch(vec, terms[i][j], expected[i], QueryTermSimple::PREFIXTERM);
                performPrefixSearch(vec, terms[i][j], empty, QueryTermSimple::WORD);
            } else {
                performPrefixSearch(vec, terms[i][j], empty, QueryTermSimple::PREFIXTERM);
                performPrefixSearch(vec, terms[i][j], empty, QueryTermSimple::WORD);
            }
        }
    }
}


void
SearchContextTest::testPrefixSearch()
{
    for (const auto & cfg : _stringCfg) {
        testPrefixSearch(AttributeFactory::createAttribute(cfg.first, cfg.second));
    }
}

template <typename VectorType, typename ValueType>
void
SearchContextTest::requireThatSearchIsWorkingAfterClearDoc(const vespalib::string & name,
                                                           const Config & cfg,
                                                           ValueType startValue,
                                                           const vespalib::string & term)
{
    AttributePtr a = AttributeFactory::createAttribute(name, cfg);
    LOG(info, "requireThatSearchIsWorkingAfterClearDoc: vector '%s', term '%s'",
        a->getName().c_str(), term.c_str());
    addReservedDoc(*a);
    a->addDocs(4);
    VectorType & v = dynamic_cast<VectorType &>(*a);
    resetAttribute(v, startValue);
    {
        ResultSetPtr rs = performSearch(v, term);
        EXPECT_EQUAL(4u, rs->getNumHits());
        ASSERT_TRUE(4u == rs->getNumHits());
        const RankedHit * array = rs->getArray();
        EXPECT_EQUAL(1u, array[0]._docId);
        EXPECT_EQUAL(2u, array[1]._docId);
        EXPECT_EQUAL(3u, array[2]._docId);
        EXPECT_EQUAL(4u, array[3]._docId);
    }
    a->clearDoc(1);
    a->clearDoc(3);
    a->commit(true);
    {
        ResultSetPtr rs = performSearch(v, term);
        EXPECT_EQUAL(2u, rs->getNumHits());
        const RankedHit * array = rs->getArray();
        EXPECT_EQUAL(2u, array[0]._docId);
        EXPECT_EQUAL(4u, array[1]._docId);
    }
}

void
SearchContextTest::requireThatSearchIsWorkingAfterClearDoc()
{
    for (const auto & cfg : _integerCfg) {
        requireThatSearchIsWorkingAfterClearDoc<IntegerAttribute>(cfg.first, cfg.second, 10, "10");
        requireThatSearchIsWorkingAfterClearDoc<IntegerAttribute>(cfg.first, cfg.second, 10, "<11");
    }

    for (const auto & cfg : _floatCfg) {
        requireThatSearchIsWorkingAfterClearDoc<FloatingPointAttribute>(cfg.first, cfg.second, 10.5, "10.5");
        requireThatSearchIsWorkingAfterClearDoc<FloatingPointAttribute>(cfg.first, cfg.second, 10.5, "<10.6");
    }

    for (const auto & cfg : _stringCfg) {
        requireThatSearchIsWorkingAfterClearDoc<StringAttribute>(cfg.first, cfg.second, "start", "start");
    }
}

template <typename VectorType, typename ValueType>
void
SearchContextTest::requireThatSearchIsWorkingAfterLoadAndClearDoc(const vespalib::string & name,
                                                                  const Config & cfg,
                                                                  ValueType startValue,
                                                                  ValueType defaultValue,
                                                                  const vespalib::string & term)
{
    AttributePtr a = AttributeFactory::createAttribute(name, cfg);
    LOG(info, "requireThatSearchIsWorkingAfterLoadAndClearDoc: vector '%s', term '%s'",
        a->getName().c_str(), term.c_str());
    addReservedDoc(*a);
    a->addDocs(15);
    VectorType & va = dynamic_cast<VectorType &>(*a);
    resetAttribute(va, startValue); // triggers vector vector in posting list (count 15)
    AttributePtr b = AttributeFactory::createAttribute(name + "-save", cfg);
    EXPECT_TRUE(a->saveAs(b->getBaseFileName()));
    EXPECT_TRUE(b->load());
    b->clearDoc(6); // goes from vector vector to single vector with count 14
    b->commit(true);
    {
        ResultSetPtr rs = performSearch(dynamic_cast<VectorType &>(*b), term);
        EXPECT_EQUAL(14u, rs->getNumHits());
        const RankedHit * array = rs->getArray();
        for (uint32_t i = 0; i < 14; ++i) {
            if (i < 5) {
                EXPECT_EQUAL(i + 1, array[i]._docId);
            } else
                EXPECT_EQUAL(i + 2, array[i]._docId);
        }
    }
    ValueType buf;
    if (cfg.collectionType().isMultiValue()) {
        EXPECT_EQUAL(0u, b->get(6, &buf, 1));
    } else {
        EXPECT_EQUAL(1u, b->get(6, &buf, 1));
        EXPECT_EQUAL(defaultValue, buf);
    }
}

void
SearchContextTest::requireThatSearchIsWorkingAfterLoadAndClearDoc()
{
    {
        int64_t value = 10;
        int64_t defValue = search::attribute::getUndefined<int32_t>();
        requireThatSearchIsWorkingAfterLoadAndClearDoc<IntegerAttribute>("s-fs-int32", _integerCfg["s-fs-int32"],
                                                                         value, defValue, "10");
        requireThatSearchIsWorkingAfterLoadAndClearDoc<IntegerAttribute>("a-fs-int32", _integerCfg["a-fs-int32"],
                                                                         value, defValue, "10");
    }
    {
        vespalib::string value = "foo";
        vespalib::string defValue = "";
        requireThatSearchIsWorkingAfterLoadAndClearDoc<StringAttribute>("s-fs-str", _stringCfg["s-fs-str"],
                                                                        value, defValue, value);
        requireThatSearchIsWorkingAfterLoadAndClearDoc<StringAttribute>("a-fs-str", _stringCfg["a-fs-str"],
                                                                        value, defValue, value);
    }
}

template <typename VectorType, typename ValueType>
void
SearchContextTest::requireThatSearchIsWorkingAfterUpdates(const vespalib::string & name,
                                                          const Config & cfg,
                                                          ValueType value1,
                                                          ValueType value2)
{
    AttributePtr a = AttributeFactory::createAttribute(name, cfg);
    VectorType & va = dynamic_cast<VectorType &>(*a);
    LOG(info, "requireThatSearchIsWorkingAfterUpdates: vector '%s'", a->getName().c_str());
    addReservedDoc(*a);
    a->addDocs(2);
    va.update(1, value1);
    va.commit(true);
    va.update(2, value1);
    va.update(2, value2);
    va.commit(true);
    {
        ResultSetPtr rs = performSearch(va, value1);
        EXPECT_EQUAL(1u, rs->getNumHits()); // doc 1 should not have this value
    }
    {
        ResultSetPtr rs = performSearch(va, value2);
        EXPECT_EQUAL(1u, rs->getNumHits());
    }
}

void
SearchContextTest::requireThatSearchIsWorkingAfterUpdates()
{
    for (const auto & cfg : _integerCfg) {
        requireThatSearchIsWorkingAfterUpdates<IntegerAttribute>(cfg.first, cfg.second, 10, 20);
    }

    for (const auto & cfg : _stringCfg) {
        requireThatSearchIsWorkingAfterUpdates<StringAttribute>(cfg.first, cfg.second, "foo", "bar");
    }
}

void
SearchContextTest::requireThatFlagAttributeIsWorkingWhenNewDocsAreAdded()
{
    LOG(info, "requireThatFlagAttributeIsWorkingWhenNewDocsAreAdded()");
    Config cfg(BasicType::INT8, CollectionType::ARRAY);
    cfg.setFastSearch(true);
    {
        cfg.setGrowStrategy(GrowStrategy::make(1, 0, 1));
        AttributePtr a = AttributeFactory::createAttribute("flags", cfg);
        FlagAttribute & fa = dynamic_cast<FlagAttribute &>(*a);
        addReservedDoc(fa);
        fa.addDocs(1);
        fa.append(1, 10, 1);
        fa.append(1, 24, 1);
        fa.commit(true);
        fa.addDocs(1);
        fa.append(2, 20, 1);
        fa.append(2, 24, 1);
        fa.commit(true);
        fa.addDocs(1);
        fa.append(3, 30, 1);
        fa.append(3, 26, 1);
        fa.commit(true);
        fa.addDocs(1);
        fa.append(4, 40, 1);
        fa.append(4, 24, 1);
        fa.commit(true);
        {
            ResultSetPtr rs = performSearch(fa, "<24");
            EXPECT_EQUAL(2u, rs->getNumHits());
            EXPECT_EQUAL(1u, rs->getArray()[0]._docId);
            EXPECT_EQUAL(2u, rs->getArray()[1]._docId);
        }
        {
            ResultSetPtr rs = performSearch(fa, "24");
            EXPECT_EQUAL(3u, rs->getNumHits());
            EXPECT_EQUAL(1u, rs->getArray()[0]._docId);
            EXPECT_EQUAL(2u, rs->getArray()[1]._docId);
            EXPECT_EQUAL(4u, rs->getArray()[2]._docId);
        }
    }
    {
        cfg.setGrowStrategy(GrowStrategy::make(4, 0, 4));
        AttributePtr a = AttributeFactory::createAttribute("flags", cfg);
        FlagAttribute & fa = dynamic_cast<FlagAttribute &>(*a);
        std::vector<uint32_t> exp50;
        std::vector<uint32_t> exp60;
        addReservedDoc(fa);
        for (uint32_t i = 0; i < 200; ++i) {
            uint32_t docId;
            EXPECT_TRUE(fa.addDoc(docId));
            if (i % 2 == 0) {
                fa.append(docId, 50, 1);
                exp50.push_back(docId);
            } else {
                fa.append(docId, 60, 1);
                exp60.push_back(docId);
            }
            fa.commit(true);
            {
                ResultSetPtr rs1 = performSearch(fa, "50");
                ResultSetPtr rs2 = performSearch(fa, "<51");
                EXPECT_EQUAL(exp50.size(), rs1->getNumHits());
                EXPECT_EQUAL(exp50.size(), rs2->getNumHits());
                for (size_t j = 0; j < exp50.size(); ++j) {
                    EXPECT_EQUAL(exp50[j], rs1->getArray()[j]._docId);
                    EXPECT_EQUAL(exp50[j], rs2->getArray()[j]._docId);
                }
            }
            {
                ResultSetPtr rs = performSearch(fa, "60");
                EXPECT_EQUAL(exp60.size(), rs->getNumHits());
                for (size_t j = 0; j < exp60.size(); ++j) {
                    EXPECT_EQUAL(exp60[j], rs->getArray()[j]._docId);
                }
            }
        }
    }
}

template <typename VectorType, typename ValueType>
void
SearchContextTest::requireThatInvalidSearchTermGivesZeroHits(const vespalib::string & name,
                                                             const Config & cfg,
                                                             ValueType value)
{
    AttributePtr a = AttributeFactory::createAttribute(name, cfg);
    VectorType & va = dynamic_cast<VectorType &>(*a);
    LOG(info, "requireThatInvalidSearchTermGivesZeroHits: vector '%s'", a->getName().c_str());
    addReservedDoc(*a);
    a->addDocs(1);
    va.update(1, value);
    va.commit(true);
    ResultSetPtr rs = performSearch(va, "foo");
    EXPECT_EQUAL(0u, rs->getNumHits());
}

void
SearchContextTest::requireThatInvalidSearchTermGivesZeroHits()
{
    for (const auto & cfg : _integerCfg) {
        requireThatInvalidSearchTermGivesZeroHits<IntegerAttribute>(cfg.first, cfg.second, 10);
    }
    for (const auto & cfg : _floatCfg) {
        requireThatInvalidSearchTermGivesZeroHits<FloatingPointAttribute>(cfg.first, cfg.second, 10);
    }
}

void
SearchContextTest::requireThatFlagAttributeHandlesTheByteRange()
{
    LOG(info, "requireThatFlagAttributeHandlesTheByteRange()");
    Config cfg(BasicType::INT8, CollectionType::ARRAY);
    cfg.setFastSearch(true);

    AttributePtr a = AttributeFactory::createAttribute("flags", cfg);
    FlagAttribute & fa = dynamic_cast<FlagAttribute &>(*a);
    addReservedDoc(fa);
    fa.addDocs(5);
    fa.append(1, -128, 1);
    fa.append(2, -64, 1);
    fa.append(2, -8, 1);
    fa.append(3, 0, 1);
    fa.append(3, 8, 1);
    fa.append(4, 64, 1);
    fa.append(4, 24, 1);
    fa.append(5, 127, 1);
    fa.commit(true);

    performSearch(fa, "-128", DocSet().put(1), QueryTermSimple::WORD);
    performSearch(fa, "127", DocSet().put(5), QueryTermSimple::WORD);
    performSearch(fa, ">-128", DocSet().put(2).put(3).put(4).put(5), QueryTermSimple::WORD);
    performSearch(fa, "<127", DocSet().put(1).put(2).put(3).put(4), QueryTermSimple::WORD);
    performSearch(fa, "[-128;-8]", DocSet().put(1).put(2), QueryTermSimple::WORD);
    performSearch(fa, "[-8;8]", DocSet().put(2).put(3), QueryTermSimple::WORD);
    performSearch(fa, "[8;127]", DocSet().put(3).put(4).put(5), QueryTermSimple::WORD);
    performSearch(fa, "[-129;-8]", DocSet().put(1).put(2), QueryTermSimple::WORD);
    performSearch(fa, "[8;128]", DocSet().put(3).put(4).put(5), QueryTermSimple::WORD);
}

void
SearchContextTest::requireThatOutOfBoundsSearchTermGivesZeroHits(const vespalib::string &name,
                                                                 const Config &cfg,
                                                                 int64_t maxValue)
{
    AttributePtr a = AttributeFactory::createAttribute(name, cfg);
    IntegerAttribute &ia = dynamic_cast<IntegerAttribute &>(*a);
    addReservedDoc(*a);
    a->addDocs(1);
    ia.update(1, maxValue);
    ia.commit(true);
    vespalib::string term = vespalib::make_string("%" PRIu64 "", (int64_t) maxValue + 1);
    LOG(info, "requireThatOutOfBoundsSearchTermGivesZeroHits: vector '%s', term '%s'", a->getName().c_str(), term.c_str());
    ResultSetPtr rs = performSearch(ia, term);
    EXPECT_EQUAL(0u, rs->getNumHits());
}

void
SearchContextTest::requireThatOutOfBoundsSearchTermGivesZeroHits()
{
    for (const auto & cfg : _integerCfg) {
        int32_t maxValue = std::numeric_limits<int32_t>::max();
        requireThatOutOfBoundsSearchTermGivesZeroHits(cfg.first, cfg.second, maxValue);
    }
    {
        Config cfg(BasicType::INT8, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        int8_t maxValue = std::numeric_limits<int8_t>::max();
        requireThatOutOfBoundsSearchTermGivesZeroHits("flags", cfg, maxValue);
    }
}


void
SearchContextTest::initIntegerConfig()
{
    { // CollectionType::SINGLE
        Config cfg(BasicType::INT32, CollectionType::SINGLE);
        _integerCfg["s-int32"] = cfg;
    }
    { // CollectionType::SINGLE && fastSearch
        Config cfg(BasicType::INT32, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        _integerCfg["s-fs-int32"] = cfg;
    }
    { // CollectionType::ARRAY
        Config cfg(BasicType::INT32, CollectionType::ARRAY);
        _integerCfg["a-int32"] = cfg;
    }
    { // CollectionType::ARRAY && fastSearch
        Config cfg(BasicType::INT32, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        _integerCfg["a-fs-int32"] = cfg;
    }
    { // CollectionType::WSET
        Config cfg(BasicType::INT32, CollectionType::WSET);
        _integerCfg["w-int32"] = cfg;
    }
    { // CollectionType::WSET && fastSearch
        Config cfg(BasicType::INT32, CollectionType::WSET);
        cfg.setFastSearch(true);
        _integerCfg["w-fs-int32"] = cfg;
    }
}

void
SearchContextTest::initFloatConfig()
{
    { // CollectionType::SINGLE
        Config cfg(BasicType::FLOAT, CollectionType::SINGLE);
        _floatCfg["s-float"] = cfg;
    }
    { // CollectionType::SINGLE && fastSearch
        Config cfg(BasicType::FLOAT, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        _floatCfg["s-fs-float"] = cfg;
    }
    { // CollectionType::ARRAY
        Config cfg(BasicType::FLOAT, CollectionType::ARRAY);
        _floatCfg["a-float"] = cfg;
    }
    { // CollectionType::ARRAY && fastSearch
        Config cfg(BasicType::FLOAT, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        _floatCfg["a-fs-float"] = cfg;
    }
    { // CollectionType::WSET
        Config cfg(BasicType::FLOAT, CollectionType::WSET);
        _floatCfg["w-float"] = cfg;
    }
    { // CollectionType::WSET && fastSearch
        Config cfg(BasicType::FLOAT, CollectionType::WSET);
        cfg.setFastSearch(true);
        _floatCfg["w-fs-float"] = cfg;
    }
}

void
SearchContextTest::initStringConfig()
{
    { // CollectionType::SINGLE
        Config cfg(BasicType::STRING, CollectionType::SINGLE);
        _stringCfg["s-str"] = cfg;
    }
    { // CollectionType::ARRAY
        Config cfg(BasicType::STRING, CollectionType::ARRAY);
        _stringCfg["a-str"] = cfg;
    }
    { // CollectionType::WSET
        Config cfg(BasicType::STRING, CollectionType::WSET);
        _stringCfg["w-str"] = cfg;
    }
    { // CollectionType::SINGLE && fastSearch
        Config cfg(BasicType::STRING, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        _stringCfg["s-fs-str"] = cfg;
    }
    { // CollectionType::ARRAY && fastSearch
        Config cfg(BasicType::STRING, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        _stringCfg["a-fs-str"] = cfg;
    }
    { // CollectionType::WSET && fastSearch
        Config cfg(BasicType::STRING, CollectionType::WSET);
        cfg.setFastSearch(true);
        _stringCfg["w-fs-str"] = cfg;
    }
}

SearchContextTest::SearchContextTest() :
    _integerCfg(),
    _floatCfg(),
    _stringCfg()
{
    initIntegerConfig();
    initFloatConfig();
    initStringConfig();
}

SearchContextTest::~SearchContextTest() {}

int
SearchContextTest::Main()
{
    TEST_INIT("searchcontext_test");
    EXPECT_TRUE(true);

    testSearch();
    testSearchIterator();
    testRangeSearch();
    testRangeSearchLimited();
    testCaseInsensitiveSearch();
    testRegexSearch();
    testPrefixSearch();
    testSearchIteratorConformance();
    testSearchIteratorUnpacking();
    TEST_DO(requireThatSearchIsWorkingAfterClearDoc());
    TEST_DO(requireThatSearchIsWorkingAfterLoadAndClearDoc());
    TEST_DO(requireThatSearchIsWorkingAfterUpdates());
    TEST_DO(requireThatFlagAttributeIsWorkingWhenNewDocsAreAdded());
    TEST_DO(requireThatInvalidSearchTermGivesZeroHits());
    TEST_DO(requireThatFlagAttributeHandlesTheByteRange());
    TEST_DO(requireThatOutOfBoundsSearchTermGivesZeroHits());

    TEST_DONE();
}

}

TEST_APPHOOK(search::SearchContextTest);

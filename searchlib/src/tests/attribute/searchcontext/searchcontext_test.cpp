// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeiterators.h>
#include <vespa/searchlib/attribute/flagattribute.h>
#include <vespa/searchlib/attribute/postinglistsearchcontext.h>
#include <vespa/searchlib/attribute/searchcontextelementiterator.h>
#include <vespa/searchlib/attribute/singleboolattribute.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/searchlib/queryeval/hitcollector.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/test/attribute_builder.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/compress.h>
#include <vespa/vespalib/util/simple_thread_bundle.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <initializer_list>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP("searchcontext_test");

namespace search {

namespace {

bool
isUnsignedSmallIntAttribute(const AttributeVector &a)
{
    switch (a.getBasicType())
    {
    case attribute::BasicType::BOOL:
    case attribute::BasicType::UINT2:
    case attribute::BasicType::UINT4:
        return true;
    default:
        return false;
    }
}

}

using AttributePtr = AttributeVector::SP;
using ResultSetPtr = std::unique_ptr<ResultSet>;
using SearchBasePtr = queryeval::SearchIterator::UP;
using search::attribute::SearchContext;
using SearchContextPtr = std::unique_ptr<SearchContext>;
using largeint_t = AttributeVector::largeint_t;

using attribute::BasicType;
using attribute::CollectionType;
using attribute::Config;
using attribute::HitEstimate;
using attribute::SearchContextParams;
using attribute::test::AttributeBuilder;
using fef::MatchData;
using fef::TermFieldMatchData;
using fef::TermFieldMatchDataArray;
using fef::TermFieldMatchDataPosition;
using queryeval::HitCollector;
using queryeval::SearchIterator;
using queryeval::SimpleResult;
using TermType = search::QueryTermSimple::Type;

class DocSet : public std::set<uint32_t>
{
public:
    DocSet() noexcept;
    ~DocSet();
    DocSet(std::initializer_list<uint32_t> l) : std::set<uint32_t>(l) { }
    DocSet(const uint32_t *b, const uint32_t *e) : std::set<uint32_t>(b, e) {}
    DocSet & put(const uint32_t &v) {
        insert(v);
        return *this;
    }
};

DocSet::DocSet() noexcept = default;
DocSet::~DocSet() = default;

bool is_flag_attribute(const Config& cfg) {
    return cfg.fastSearch() &&
        (cfg.basicType() == BasicType::INT8) &&
        (cfg.collectionType() == CollectionType::ARRAY);
}

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
    attribute::HitEstimate expected_hit_estimate() const {
        if (getHitCount() == 0) {
            return HitEstimate(0);
        }
        uint32_t docid_limit = _vec->getStatus().getNumDocs();
        if (is_flag_attribute(_vec->getConfig())) {
            return HitEstimate::unknown(docid_limit);
        } else if (_vec->getConfig().fastSearch()) {
            return HitEstimate(getHitCount());
        } else if (_vec->getConfig().collectionType() == CollectionType::SINGLE) {
            return HitEstimate::unknown(docid_limit);
        } else {
            return HitEstimate::unknown(std::max((uint64_t)docid_limit, _vec->getStatus().getNumValues()));
        }
    }
};

template <typename V, typename T>
PostingList<V, T>::PostingList(V & vec, T value) : _vec(&vec), _value(value), _hits() {}

template <typename V, typename T>
PostingList<V, T>::~PostingList() = default;

class DocRange
{
public:
    uint32_t start;
    uint32_t end;
    DocRange(uint32_t start_, uint32_t end_) : start(start_), end(end_) {}
};

class SearchContextTest : public ::testing::Test
{
public:
    // helper functions
    static void addReservedDoc(AttributeVector &ptr);
    static void addDocs(AttributeVector & ptr, uint32_t numDocs);
    template <typename V, typename T>
    static SearchContextPtr getSearch(const V & vec, const T & term, TermType termType=TermType::WORD);
protected:
    using ConfigMap = std::map<vespalib::string, Config>;
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
                               TermType termType=TermType::WORD);

    ResultSetPtr performSearch(SearchIterator & sb, uint32_t numDocs);
    template <typename V, typename T>
    ResultSetPtr performSearch(const V & vec, const T & term);
    template <typename V, typename T>
    ResultSetPtr performSearch(const queryeval::ExecuteInfo & executeInfo, const V & vec, const T & term, TermType termType);
    template <typename V>
    void performSearch(const V & vec, const vespalib::string & term, const DocSet & expected, TermType termType);
    template <typename V>
    void performSearch(const queryeval::ExecuteInfo & executeInfo, const V & vec, const vespalib::string & term,
                       const DocSet & expected, TermType termType);
    void checkResultSet(const ResultSet & rs, const DocSet & exp, bool bitVector);

    template<typename T, typename A>
    void testSearchIterator(const std::vector<T> & keys, const vespalib::string &keyAsString, const ConfigMap &cfgs);
    // test search functionality
    template <typename V, typename T>
    void testFind(const PostingList<V, T> & first, bool verify_hit_estimate);

    template <typename V, typename T>
    void testSearch(V & attribute, uint32_t numDocs, const std::vector<T> & values);
    template<typename T, typename A>
    void testSearch(const ConfigMap & cfgs);
    template <typename V, typename T>
    void testMultiValueSearchHelper(V & vec, const std::vector<T> & values);
    template <typename V, typename T>
    void testMultiValueSearch(V& attr, uint32_t num_docs, const std::vector<T> & values);

    class IteratorTester {
    public:
        virtual bool matches(const SearchIterator & base) const = 0;
        virtual ~IteratorTester() = default;
    };
    class AttributeIteratorTester : public IteratorTester
    {
    public:
        bool matches(const SearchIterator & base) const override {
            return dynamic_cast<const AttributeIterator *>(&base) != nullptr;
        }
    };
    class FlagAttributeIteratorTester : public IteratorTester
    {
    public:
        bool matches(const SearchIterator & base) const override {
            return (dynamic_cast<const FlagAttributeIterator *>(&base) != nullptr) ||
                   (dynamic_cast<const BitVectorIterator *>(&base) != nullptr) ||
                   (dynamic_cast<const queryeval::EmptySearch *>(&base) != nullptr);
        }
    };
    class AttributePostingListIteratorTester : public IteratorTester
    {
    public:
        bool matches(const SearchIterator & base) const override {
            return dynamic_cast<const AttributePostingListIterator *>(&base) != nullptr ||
                dynamic_cast<const queryeval::EmptySearch *>(&base) != nullptr;
                
        }
    };


    // test search iterator functionality
    void testStrictSearchIterator(SearchContext & threeHits, SearchContext & noHits, const IteratorTester & typeTester);
    void testNonStrictSearchIterator(SearchContext & threeHits, SearchContext & noHits, const IteratorTester & typeTester);
    AttributePtr fillForSearchIteratorTest(const vespalib::string& name, const Config& cfg);
    AttributePtr fillForSemiNibbleSearchIteratorTest(const vespalib::string& name, const Config& cfg);


    // test search iterator unpacking
    void fillForSearchIteratorUnpackingTest(IntegerAttribute * ia, bool extra);
    void testSearchIteratorUnpacking(const AttributePtr & ptr, SearchContext & sc, bool extra, bool strict) {
        sc.fetchPostings(queryeval::ExecuteInfo::FULL, true);
        for (bool withElementId : {false, true}) {
            testSearchIteratorUnpacking(ptr, sc, extra, strict, withElementId);
        }
    }
    void testSearchIteratorUnpacking(const AttributePtr & ptr, SearchContext & sc,
                                     bool extra, bool strict, bool withElementId);


    // test range search
    template <typename VectorType>
    void performRangeSearch(const VectorType & vec, const vespalib::string & term, const DocSet & expected);
    template <typename VectorType, typename ValueType>
    void testRangeSearch(const AttributePtr & ptr, uint32_t numDocs, std::vector<ValueType> values);


    // test case insensitive search
    void performCaseInsensitiveSearch(const StringAttribute & vec, const vespalib::string & term, const DocSet & expected);
    void testCaseInsensitiveSearch(const AttributePtr & ptr);
    void testRegexSearch(const vespalib::string& name, const Config& cfg);


    // test prefix search
    void testPrefixSearch(const vespalib::string& name, const Config& cfg);

    // test fuzzy search
    void testFuzzySearch(const vespalib::string& name, const Config& cfg);

    // test that search is working after clear doc
    template <typename VectorType, typename ValueType>
    void requireThatSearchIsWorkingAfterClearDoc(const vespalib::string & name, const Config & cfg,
                                                 ValueType startValue, const vespalib::string & term);

    // test that search is working after load and clear doc
    template <typename VectorType, typename ValueType>
    void requireThatSearchIsWorkingAfterLoadAndClearDoc(const vespalib::string & name, const Config & cfg,
                                                        ValueType startValue, ValueType defaultValue,
                                                        const vespalib::string & term);

    template <typename VectorType, typename ValueType>
    void requireThatSearchIsWorkingAfterUpdates(const vespalib::string & name, const Config & cfg,
                                                ValueType value1, ValueType value2);


    template <typename VectorType, typename ValueType>
    void requireThatInvalidSearchTermGivesZeroHits(const vespalib::string & name, const Config & cfg, ValueType value);


    void requireThatOutOfBoundsSearchTermGivesZeroHits(const vespalib::string &name, const Config &cfg, int32_t maxValue);

    // init maps with config objects
    void initIntegerConfig();
    void initFloatConfig();
    void initStringConfig();

public:
    SearchContextTest();
    ~SearchContextTest() override;
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
        EXPECT_EQ(docId, i);
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
        values.emplace_back(ss.str());
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
    auto & vec = dynamic_cast<AttributeVector &>(pl.getAttribute());
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
SearchContextTest::buildTermQuery(std::vector<char> & buffer, const vespalib::string & index, const vespalib::string & term, TermType termType)
{
    uint32_t indexLen = index.size();
    uint32_t termLen = term.size();
    uint32_t fuzzyParametersSize = (termType == TermType::FUZZYTERM) ? 8 : 0;
    uint32_t queryPacketSize = 1 + 2 * 4 + indexLen + termLen + fuzzyParametersSize;
    uint32_t p = 0;
    buffer.resize(queryPacketSize);
    switch (termType) {
      case TermType::PREFIXTERM: buffer[p++] = ParseItem::ITEM_PREFIXTERM; break;
      case TermType::REGEXP: buffer[p++] = ParseItem::ITEM_REGEXP; break;
      case TermType::FUZZYTERM: buffer[p++] = ParseItem::ITEM_FUZZY; break;
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

    if (termType == TermType::FUZZYTERM) {
        p += vespalib::compress::Integer::compressPositive(2, &buffer[p]);  // max edit distance
        p += vespalib::compress::Integer::compressPositive(0, &buffer[p]);  // prefix length
    }

    buffer.resize(p);
}

template <typename V, typename T>
SearchContextPtr
SearchContextTest::getSearch(const V & vec, const T & term, TermType termType)
{
    std::vector<char> query;
    vespalib::asciistream ss;
    ss << term;
    buildTermQuery(query, vec.getName(), ss.str(), termType);

    return (dynamic_cast<const AttributeVector &>(vec)).
        getSearch(std::string_view(&query[0], query.size()),
                  attribute::SearchContextParams());
}

ResultSetPtr
SearchContextTest::performSearch(SearchIterator & sb, uint32_t numDocs)
{
    HitCollector hc(numDocs, numDocs);
    sb.initRange(1, numDocs);
    // assume strict toplevel search object located at start
    for (sb.seek(1u); ! sb.isAtEnd(); sb.seek(sb.getDocId() + 1)) {
        hc.addHit(sb.getDocId(), 0.0);
    }
    return hc.getResultSet();
}

template <typename V, typename T>
ResultSetPtr
SearchContextTest::performSearch(const V & vec, const T & term)
{
    return performSearch(queryeval::ExecuteInfo::FULL, vec, term, TermType::WORD);
}

template <typename V, typename T>
ResultSetPtr
SearchContextTest::performSearch(const queryeval::ExecuteInfo & executeInfo, const V & vec, const T & term, TermType termType)
{
    TermFieldMatchData dummy;
    SearchContextPtr sc = getSearch(vec, term, termType);
    sc->fetchPostings(executeInfo, true);
    SearchBasePtr sb = sc->createIterator(&dummy, true);
    ResultSetPtr rs = performSearch(*sb, vec.getNumDocs());
    return rs;
}

template <typename V>
void
SearchContextTest::performSearch(const queryeval::ExecuteInfo & executeInfo, const V & vec, const vespalib::string & term,
                                 const DocSet & expected, TermType termType)
{
#if 0
    std::cout << "performSearch[" << term << "]: {";
    std::copy(expected.begin(), expected.end(), std::ostream_iterator<uint32_t>(std::cout, ", "));
    std::cout << "}, prefix(" << (prefix ? "true" : "false") << ")" << std::endl;
#endif
    { // strict search iterator
        ResultSetPtr rs = performSearch(executeInfo, vec, term, termType);
        checkResultSet(*rs, expected, false);
    }
}
template <typename V>
void
SearchContextTest::performSearch(const V & vec, const vespalib::string & term,
                                 const DocSet & expected, TermType termType)
{
    performSearch(queryeval::ExecuteInfo::FULL, vec, term, expected, termType);
}

void
SearchContextTest::checkResultSet(const ResultSet & rs, const DocSet & expected, bool bitVector)
{
    EXPECT_EQ(rs.getNumHits(), expected.size());
    if (bitVector) {
        const BitVector * vec = rs.getBitOverflow();
        if ( ! expected.empty()) {
            ASSERT_TRUE(vec != nullptr);
            for (const auto & expect : expected) {
                EXPECT_TRUE(vec->testBit(expect));
            }
        }
    } else {
        const RankedHit * array = rs.getArray();
        if ( ! expected.empty()) {
            ASSERT_TRUE(array != nullptr);
            uint32_t i = 0;
            for (auto iter = expected.begin(); iter != expected.end(); ++iter, ++i) {
                EXPECT_EQ(array[i].getDocId(), *iter);
            }
        }
    }
}


//-----------------------------------------------------------------------------
// Test search functionality
//-----------------------------------------------------------------------------
template <typename V, typename T>
void
SearchContextTest::testFind(const PostingList<V, T> & pl, bool verify_hit_estimate)
{
    { // strict search iterator
        SearchContextPtr sc = getSearch(pl.getAttribute(), pl.getValue());
        if (verify_hit_estimate) {
            auto act_est = sc->calc_hit_estimate();
            auto exp_est = pl.expected_hit_estimate();
            EXPECT_EQ(exp_est.est_hits(), act_est.est_hits());
            EXPECT_EQ(exp_est.is_unknown(), act_est.is_unknown());
        }
        sc->fetchPostings(queryeval::ExecuteInfo::FULL, true);
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
        attribute.getName().c_str(), numDocs, values.size());

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
        testFind(list, true);
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
        testFind(list, false);
    }
}

AttributePtr
create_as(const AttributeVector& attr, const std::string& name_suffix)
{
    return AttributeFactory::createAttribute(attr.getName() + name_suffix, attr.getConfig());
}


template <typename V, typename T>
void
SearchContextTest::testMultiValueSearch(V& attr, uint32_t num_docs, const std::vector<T> & values)
{
    addDocs(attr, num_docs);
    LOG(info, "testMultiValueSearch: vector '%s' with %u documents and %lu unique values",
        attr.getName().c_str(), attr.getNumDocs(), values.size());

    fillAttribute(attr, values);

    testMultiValueSearchHelper(attr, values);

    auto attr2 = create_as(attr, "_2");
    ASSERT_TRUE(attr.save(attr2->getBaseFileName()));
    ASSERT_TRUE(attr2->load());

    testMultiValueSearchHelper(static_cast<V&>(*attr2.get()), values);

    size_t sz = values.size();
    ASSERT_TRUE(sz > 2);
    std::vector<T> subset;
    // values[sz - 2] is not used  -> 0 hits
    // values[sz - 1] is used once -> 1 hit
    for (size_t i = 0; i < sz - 2; ++i) {
        subset.push_back(values[i]);
    }

    fillAttribute(attr, subset);

    ASSERT_TRUE(1u < attr.getNumDocs());
    EXPECT_TRUE(attr.append(1u, values[sz - 1], 1));
    attr.commit(true);

    testMultiValueSearchHelper(attr, values);

    auto attr3 = create_as(attr, "_3");
    ASSERT_TRUE(attr.save(attr3->getBaseFileName()));
    ASSERT_TRUE(attr3->load());

    testMultiValueSearchHelper(static_cast<V&>(*attr3.get()), values);
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
            testMultiValueSearch(*(dynamic_cast<A *>(first.get())), second->getNumDocs(), values);
        }
    }
}


template<typename T, typename A>
class Verifier : public search::test::SearchIteratorVerifier {
public:
    Verifier(const std::vector<T> & keys, const vespalib::string & keyAsString,
             const vespalib::string & name, const Config & cfg);
    ~Verifier() override;
    SearchIterator::UP
    create(bool strict) const override {
        _sc->fetchPostings(queryeval::ExecuteInfo::FULL, strict);
        return _sc->createIterator(&_dummy, strict);
    }
private:
    mutable TermFieldMatchData _dummy;
    AttributePtr     _attribute;
    SearchContextPtr _sc;
};

template<typename T, typename A>
Verifier<T, A>::Verifier(const std::vector<T> & keys, const vespalib::string & keyAsString,
                         const vespalib::string & name, const Config & cfg)
    : _dummy(),
      _attribute(AttributeFactory::createAttribute(name + "-initrange", cfg)),
      _sc()
{
    SearchContextTest::addDocs(*_attribute, getDocIdLimit());
    size_t i(0);
    for (uint32_t doc : getExpectedDocIds()) {
        EXPECT_TRUE(nullptr != dynamic_cast<A *>(_attribute.get()));
        EXPECT_TRUE(dynamic_cast<A &>(*_attribute).update(doc, keys[(i++)%keys.size()]));
    }
    _attribute->commit(true);
    _sc = SearchContextTest::getSearch(*_attribute, keyAsString);
    EXPECT_TRUE(_sc->valid());
}

template<typename T, typename A>
Verifier<T, A>::~Verifier() = default;

template<typename T, typename A>
void SearchContextTest::testSearchIterator(const std::vector<T> & keys, const vespalib::string &keyAsString, const ConfigMap &cfgs) {
    for (const auto & cfg : cfgs) {
        {
            Verifier<T, A> verifier(keys, keyAsString, cfg.first, cfg.second);
            verifier.verify();
        }
        {
            Config withFilter(cfg.second);
            withFilter.setIsFilter(true);
            Verifier<T, A> verifier(keys, keyAsString, cfg.first + "-filter", withFilter);
            verifier.verify();
        }
    }
}

TEST_F(SearchContextTest, test_search_iterator_conformance)
{
    testSearchIterator<AttributeVector::largeint_t, IntegerAttribute>({42,45,46}, "[0;100]", _integerCfg);
    testSearchIterator<AttributeVector::largeint_t, IntegerAttribute>({42}, "42", _integerCfg);
    testSearchIterator<double, FloatingPointAttribute>({42.42}, "42.42", _floatCfg);
    testSearchIterator<vespalib::string, StringAttribute>({"any-key"}, "any-key", _stringCfg);
}

TEST_F(SearchContextTest, test_search)
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
            testMultiValueSearch(*(dynamic_cast<IntegerAttribute *>(first.get())), second->getNumDocs(), values);
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
        threeHits.fetchPostings(queryeval::ExecuteInfo::FULL, true);
        SearchBasePtr sb = threeHits.createIterator(&dummy, true);
        sb->initRange(1, threeHits.attribute().getCommittedDocIdLimit());
        EXPECT_TRUE(typeTester.matches(*sb));
        EXPECT_TRUE(sb->getDocId() == sb->beginId() ||
                    sb->getDocId() == 1u);
        EXPECT_TRUE(sb->seek(1));
        EXPECT_EQ(sb->getDocId(), 1u);
        EXPECT_TRUE(!sb->seek(2));
        EXPECT_EQ(sb->getDocId(), 3u);
        EXPECT_TRUE(sb->seek(3));
        EXPECT_EQ(sb->getDocId(), 3u);
        EXPECT_TRUE(!sb->seek(4));
        EXPECT_EQ(sb->getDocId(), 5u);
        EXPECT_TRUE(sb->seek(5));
        EXPECT_EQ(sb->getDocId(), 5u);
        EXPECT_TRUE(!sb->seek(6));
        EXPECT_TRUE(sb->isAtEnd());
    }

    { // search for value with no hits
        noHits.fetchPostings(queryeval::ExecuteInfo::FULL, true);
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
        threeHits.fetchPostings(queryeval::ExecuteInfo::FULL, false);
        SearchBasePtr sb = threeHits.createIterator(&dummy, false);
        sb->initRange(1, threeHits.attribute().getCommittedDocIdLimit());
        EXPECT_TRUE(typeTester.matches(*sb));
        EXPECT_TRUE(sb->seek(1));
        EXPECT_EQ(sb->getDocId(), 1u);
        EXPECT_TRUE(!sb->seek(2));
        EXPECT_EQ(sb->getDocId(), 1u);
        EXPECT_TRUE(sb->seek(3));
        EXPECT_EQ(sb->getDocId(), 3u);
        EXPECT_TRUE(!sb->seek(4));
        EXPECT_EQ(sb->getDocId(), 3u);
        EXPECT_TRUE(sb->seek(5));
        EXPECT_EQ(sb->getDocId(), 5u);
        EXPECT_TRUE(!sb->seek(6));
        EXPECT_TRUE(sb->getDocId() == 5u || sb->isAtEnd());
    }
    { // search for value with no hits
        noHits.fetchPostings(queryeval::ExecuteInfo::FULL, false);
        SearchBasePtr sb = noHits.createIterator(&dummy, false);
        sb->initRange(1, threeHits.attribute().getCommittedDocIdLimit());

        EXPECT_TRUE(typeTester.matches(*sb));
        EXPECT_TRUE(sb->getDocId() == sb->beginId() ||
                    sb->isAtEnd());
        EXPECT_TRUE(!sb->seek(1));
        EXPECT_NE(sb->getDocId(), 1u);
        EXPECT_TRUE(!sb->seek(6));
        EXPECT_NE(sb->getDocId(), 6u);
    }
}

AttributePtr
SearchContextTest::fillForSearchIteratorTest(const vespalib::string& name, const Config& cfg)
{
    return AttributeBuilder(name, cfg).fill({10, 20, 10, 20, 10}).get();
}

AttributePtr
SearchContextTest::fillForSemiNibbleSearchIteratorTest(const vespalib::string& name, const Config& cfg)
{
    return AttributeBuilder(name, cfg).fill({1, 2, 1, 2, 1}).get();
}

TEST_F(SearchContextTest, test_search_iterator)
{
    {
        Config cfg(BasicType::INT32, CollectionType::SINGLE);
        auto ptr = fillForSearchIteratorTest("s-int32", cfg);

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
        auto ptr = fillForSemiNibbleSearchIteratorTest("s-uint2", cfg);

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
        auto ptr = fillForSearchIteratorTest("sfs-int32", cfg);

        SearchContextPtr threeHits = getSearch(*ptr.get(), 10);
        SearchContextPtr noHits = getSearch(*ptr.get(), 30);
        AttributePostingListIteratorTester tester;
        testStrictSearchIterator(*threeHits, *noHits, tester);
    }
    {
        Config cfg(BasicType::STRING, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        auto ptr = AttributeBuilder("sfs-string", cfg).
                fill({"three", "two", "three", "two", "three"}).get();

        SearchContextPtr threeHits = getSearch(*ptr.get(), "three");
        SearchContextPtr noHits = getSearch(*ptr.get(), "none");
        AttributePostingListIteratorTester tester;
        testStrictSearchIterator(*threeHits, *noHits, tester);
    }
    {
        Config cfg(BasicType::INT8, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        auto ptr = fillForSearchIteratorTest("flags", cfg);

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
SearchContextTest::testSearchIteratorUnpacking(const AttributePtr & attr, SearchContext & sc,
                                               bool extra, bool strict, bool withElementId)
{
    LOG(info, "testSearchIteratorUnpacking: vector '%s'", attr->getName().c_str());

    TermFieldMatchData md;
    md.reset(100);

    TermFieldMatchDataPosition pos;
    pos.setElementWeight(100);
    md.appendPosition(pos);

    SearchBasePtr sbp = sc.createIterator(&md, strict);
    SearchIterator & search = *sbp;
    queryeval::ElementIterator::UP elemIt;
    if (withElementId) {
        elemIt = std::make_unique<attribute::SearchContextElementIterator>(std::move(sbp), sc);
    }
    search.initFullRange();

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
    search.unpack(1);
    EXPECT_EQ(search.getDocId(), 1u);
    EXPECT_EQ(md.getDocId(), 1u);
    EXPECT_EQ(md.getWeight(), weights[0]);

    search.unpack(2);
    EXPECT_EQ(search.getDocId(), 2u);
    EXPECT_EQ(md.getDocId(), 2u);
    if (withElementId && attr->hasMultiValue() && !attr->hasWeightedSetType()) {
        std::vector<uint32_t> elems;
        elemIt->getElementIds(2, elems);
        ASSERT_EQ(2u, elems.size());
        EXPECT_EQ(0u,elems[0]);
        EXPECT_EQ(1u,elems[1]);
    } else {
        EXPECT_EQ(md.getWeight(), weights[1]);
    }

    search.unpack(3);
    EXPECT_EQ(search.getDocId(), 3u);
    EXPECT_EQ(md.getDocId(), 3u);
    if (withElementId && attr->hasMultiValue() && !attr->hasWeightedSetType()) {
        std::vector<uint32_t> elems;
        elemIt->getElementIds(3, elems);
        ASSERT_EQ(3u, elems.size());
        EXPECT_EQ(0u,elems[0]);
        EXPECT_EQ(1u,elems[1]);
        EXPECT_EQ(2u,elems[2]);
    } else {
        EXPECT_EQ(md.getWeight(), weights[2]);
    }
    if (extra) {
        search.unpack(4);
        EXPECT_EQ(search.getDocId(), 4u);
        EXPECT_EQ(md.getDocId(), 4u);
        EXPECT_EQ(md.getWeight(), 1);
    }
}

TEST_F(SearchContextTest, test_search_iterator_unpacking)
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
        config.emplace_back(vespalib::string("sfs-int32"), cfg);
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
SearchContextTest::performRangeSearch(const VectorType & vec, const vespalib::string & term, const DocSet & expected)
{
    for (size_t num_threads : {1,3}) {
        vespalib::SimpleThreadBundle thread_bundle(num_threads);
        auto executeInfo = queryeval::ExecuteInfo::create(1.0, vespalib::Doom::never(), thread_bundle);
        performSearch(executeInfo, vec, term, expected, TermType::WORD);
    }
}

template <typename VectorType, typename ValueType>
void
SearchContextTest::testRangeSearch(const AttributePtr & ptr, uint32_t numDocs, std::vector<ValueType> values)
{
    LOG(info, "testRangeSearch: vector '%s'", ptr->getName().c_str());

    auto & vec = dynamic_cast<VectorType &>(*ptr.get());

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
    ValueType zeroValue = 0;
    bool smallUInt = isUnsignedSmallIntAttribute(vec);
    if (smallUInt) {
        for (uint32_t i = docCnt ; i < numDocs; ++i) {
            postingList[zeroValue].insert(i + 1u);
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

DocSet
createDocs(uint32_t from, int32_t count) {
    DocSet docs;
    if (count >= 0) {
        for (int32_t i(0); i < count; i++) {
            docs.put(from + i);
        }
    } else {
        for (int32_t i(0); i > count; i--) {
            docs.put(from + i);
        }
    }
    return docs;
}

TEST_F(SearchContextTest, test_range_search_limited_huge_dictionary)
{
    Config cfg(BasicType::INT32, CollectionType::SINGLE);
    cfg.setFastSearch(true);
    std::vector<int32_t> v;
    v.reserve(2000);
    for (size_t i(0); i < v.capacity(); i++) {
        v.push_back(i);
    }
    auto ptr = AttributeBuilder("limited-int32", cfg).fill(v).get();
    auto& vec = dynamic_cast<IntegerAttribute &>(*ptr);

    performRangeSearch(vec, "[1;9;1200]", createDocs(2, 9));
    performRangeSearch(vec, "[1;1109;1200]", createDocs(2, 1109));
    performRangeSearch(vec, "[1;3009;1200]", createDocs(2, 1200));

    performRangeSearch(vec, "[1;9;-1200]", createDocs(2, 9));
    performRangeSearch(vec, "[1;1109;-1200]", createDocs(2, 1109));
    performRangeSearch(vec, "[1;3009;-1200]", createDocs(2000, -1200));
}

TEST_F(SearchContextTest, test_range_search_limited)
{
    Config cfg(BasicType::INT32, CollectionType::SINGLE);
    cfg.setFastSearch(true);
    auto ptr = AttributeBuilder("limited-int32", cfg).fill({1,1,2,3,4,5,6,7,8,9,9,10}).get();
    auto& vec = dynamic_cast<IntegerAttribute &>(*ptr);

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

TEST_F(SearchContextTest, test_range_search)
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
    performSearch(vec, term, expected, TermType::WORD);
}

void
SearchContextTest::testCaseInsensitiveSearch(const AttributePtr & ptr)
{
    LOG(info, "testCaseInsensitiveSearch: vector '%s'", ptr->getName().c_str());

    auto & vec = dynamic_cast<StringAttribute &>(*ptr.get());

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
            EXPECT_EQ(ptr->get(doc++, buffer, 1), uint32_t(1));
            EXPECT_EQ(vespalib::string(buffer[0]), vespalib::string(terms[i][j]));
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
SearchContextTest::testRegexSearch(const vespalib::string& name, const Config& cfg)
{
    LOG(info, "testRegexSearch: vector '%s'", name.c_str());
    auto attr = AttributeBuilder(name, cfg).
            fill({"abc1def", "abc2Def", "abc2def", "abc4def", "abc5def", "abc6def"}).get();

    std::vector<const char *> terms = { "abc", "bc2de", "^abc1def.*bar" };
    std::vector<DocSet> expected;
    DocSet empty;
    expected.emplace_back(DocSet{1, 2, 3, 4, 5, 6}); // "abc"
    expected.emplace_back(DocSet{2, 3});             // "bc2de"
    expected.emplace_back(empty);                    // "^abc1def.*bar"

    for (uint32_t i = 0; i < terms.size(); ++i) {
        performSearch(*attr, terms[i], expected[i], TermType::REGEXP);
        performSearch(*attr, terms[i], empty, TermType::WORD);
    }
}


TEST_F(SearchContextTest, test_case_insensitive_search)
{
    for (const auto & cfg : _stringCfg) {
        testCaseInsensitiveSearch(AttributeFactory::createAttribute(cfg.first, cfg.second));
    }
}

TEST_F(SearchContextTest, test_regex_search)
{
    for (const auto & cfg : _stringCfg) {
        testRegexSearch(cfg.first, cfg.second);
    }
}


//-----------------------------------------------------------------------------
// Test prefix search
//-----------------------------------------------------------------------------

void
SearchContextTest::testPrefixSearch(const vespalib::string& name, const Config& cfg)
{
    LOG(info, "testPrefixSearch: vector '%s'", name.c_str());
    auto attr = AttributeBuilder(name, cfg).
            fill({"prefixsearch", "PREFIXSEARCH", "PrefixSearch", "precommit", "PRECOMMIT", "PreCommit"}).get();

    const char * terms[][3] = {{"pre", "PRE", "Pre"},
                               {"pref", "PREF", "Pref"},
                               {"prec", "PREC", "PreC"},
                               {"prex", "PREX", "Prex"}};
    std::vector<DocSet> expected;
    DocSet empty;
    expected.emplace_back(DocSet({1, 2, 3, 4, 5, 6})); // "pre"
    expected.emplace_back(DocSet({1, 2, 3}));          // "pref"
    expected.emplace_back(DocSet({4, 5, 6}));          // "prec"
    expected.emplace_back();                           // "prex"

    for (uint32_t i = 0; i < 4; ++i) {
        for (uint32_t j = 0; j < 3; ++j) {
            if (j == 0 || attr->getConfig().fastSearch()) {
                performSearch(*attr, terms[i][j], expected[i], TermType::PREFIXTERM);
                performSearch(*attr, terms[i][j], empty, TermType::WORD);
            } else {
                performSearch(*attr, terms[i][j], empty, TermType::PREFIXTERM);
                performSearch(*attr, terms[i][j], empty, TermType::WORD);
            }
        }
    }

    // Long range of prefixes with unique strings that causes
    // PostingListFoldedSearchContextT<DataT>::countHits() to populate
    // partial vector of posting indexes, with scan resumed by
    // fillArray or fillBitVector.
    auto& vec = dynamic_cast<StringAttribute &>(*attr.get());
    uint32_t old_size = attr->getNumDocs();
    constexpr uint32_t longrange_values = search::attribute::PostingListFoldedSearchContextT<int32_t>::MAX_POSTING_INDEXES_SIZE + 100;
    attr->addDocs(longrange_values);
    DocSet exp_longrange;
    for (uint32_t i = 0; i < longrange_values; ++i) {
        vespalib::asciistream ss;
        ss << "lpref" << i;
        vespalib::string sss(ss.str());
        exp_longrange.put(old_size + i);
        vec.update(old_size + i, vespalib::string(ss.str()).c_str());
    }
    attr->commit();
    performSearch(*attr, "lpref", exp_longrange, TermType::PREFIXTERM);
}


TEST_F(SearchContextTest, test_prefix_search)
{
    for (const auto & cfg : _stringCfg) {
        testPrefixSearch(cfg.first, cfg.second);
    }
}

//-----------------------------------------------------------------------------
// Test fuzzy search
//-----------------------------------------------------------------------------

void
SearchContextTest::testFuzzySearch(const vespalib::string& name, const Config& cfg)
{
    LOG(info, "testFuzzySearch: vector '%s'", name.c_str());
    auto attr = AttributeBuilder(name, cfg).fill({"fuzzysearch", "notthis", "FUZZYSEARCH"}).get();

    const char * terms[][2] = {
        {"fuzzysearch", "FUZZYSEARCH"},
        {"fuzzysearck", "FUZZYSEARCK"},
        {"fuzzysekkkk", "FUZZYSEKKKK"}
    };
    std::vector<DocSet> expected;
    DocSet empty;
    expected.emplace_back(DocSet({1, 3})); // normal search
    expected.emplace_back(DocSet({1, 3})); // fuzzy search
    expected.emplace_back(); // results

    for (uint32_t i = 0; i < 3; ++i) {
        for (uint32_t j = 0; j < 2; ++j) {
            performSearch(*attr, terms[i][j], expected[i], TermType::FUZZYTERM);
        }
    }
}

TEST_F(SearchContextTest, test_fuzzy_search)
{
    for (const auto & cfg : _stringCfg) {
        testFuzzySearch(cfg.first, cfg.second);
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
    auto & v = dynamic_cast<VectorType &>(*a);
    resetAttribute(v, startValue);
    {
        ResultSetPtr rs = performSearch(v, term);
        EXPECT_EQ(4u, rs->getNumHits());
        ASSERT_TRUE(4u == rs->getNumHits());
        const RankedHit * array = rs->getArray();
        EXPECT_EQ(1u, array[0].getDocId());
        EXPECT_EQ(2u, array[1].getDocId());
        EXPECT_EQ(3u, array[2].getDocId());
        EXPECT_EQ(4u, array[3].getDocId());
    }
    a->clearDoc(1);
    a->clearDoc(3);
    a->commit(true);
    {
        ResultSetPtr rs = performSearch(v, term);
        EXPECT_EQ(2u, rs->getNumHits());
        const RankedHit * array = rs->getArray();
        EXPECT_EQ(2u, array[0].getDocId());
        EXPECT_EQ(4u, array[1].getDocId());
    }
}

TEST_F(SearchContextTest, require_that_search_is_working_after_clear_doc)
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
    auto & va = dynamic_cast<VectorType &>(*a);
    resetAttribute(va, startValue); // triggers vector vector in posting list (count 15)
    AttributePtr b = AttributeFactory::createAttribute(name + "-save", cfg);
    EXPECT_TRUE(a->save(b->getBaseFileName()));
    EXPECT_TRUE(b->load());
    b->clearDoc(6); // goes from vector vector to single vector with count 14
    b->commit(true);
    {
        ResultSetPtr rs = performSearch(dynamic_cast<VectorType &>(*b), term);
        EXPECT_EQ(14u, rs->getNumHits());
        const RankedHit * array = rs->getArray();
        for (uint32_t i = 0; i < 14; ++i) {
            if (i < 5) {
                EXPECT_EQ(i + 1, array[i].getDocId());
            } else
                EXPECT_EQ(i + 2, array[i].getDocId());
        }
    }
    ValueType buf;
    if (cfg.collectionType().isMultiValue()) {
        EXPECT_EQ(0u, b->get(6, &buf, 1));
    } else {
        EXPECT_EQ(1u, b->get(6, &buf, 1));
        EXPECT_EQ(defaultValue, buf);
    }
}

TEST_F(SearchContextTest, require_that_search_is_working_after_load_and_clear_doc)
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
    auto & va = dynamic_cast<VectorType &>(*a);
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
        EXPECT_EQ(1u, rs->getNumHits()); // doc 1 should not have this value
    }
    {
        ResultSetPtr rs = performSearch(va, value2);
        EXPECT_EQ(1u, rs->getNumHits());
    }
}

TEST_F(SearchContextTest, require_that_search_is_working_after_updates)
{
    for (const auto & cfg : _integerCfg) {
        requireThatSearchIsWorkingAfterUpdates<IntegerAttribute>(cfg.first, cfg.second, 10, 20);
    }

    for (const auto & cfg : _stringCfg) {
        requireThatSearchIsWorkingAfterUpdates<StringAttribute>(cfg.first, cfg.second, "foo", "bar");
    }
}

TEST_F(SearchContextTest, require_that_flag_attribute_is_working_when_new_docs_are_added)
{
    LOG(info, "requireThatFlagAttributeIsWorkingWhenNewDocsAreAdded()");
    Config cfg(BasicType::INT8, CollectionType::ARRAY);
    cfg.setFastSearch(true);
    {
        cfg.setGrowStrategy(GrowStrategy::make(1, 0, 1));
        using IL = AttributeBuilder::IntList;
        auto a = AttributeBuilder("flags", cfg).
                fill_array({IL{10, 24}, {20, 24}, {30, 26}, {40, 24}}).get();
        {
            ResultSetPtr rs = performSearch(*a, "<24");
            EXPECT_EQ(2u, rs->getNumHits());
            EXPECT_EQ(1u, rs->getArray()[0].getDocId());
            EXPECT_EQ(2u, rs->getArray()[1].getDocId());
        }
        {
            ResultSetPtr rs = performSearch(*a, "24");
            EXPECT_EQ(3u, rs->getNumHits());
            EXPECT_EQ(1u, rs->getArray()[0].getDocId());
            EXPECT_EQ(2u, rs->getArray()[1].getDocId());
            EXPECT_EQ(4u, rs->getArray()[2].getDocId());
        }
    }
    {
        cfg.setGrowStrategy(GrowStrategy::make(4, 0, 4));
        AttributePtr a = AttributeFactory::createAttribute("flags", cfg);
        auto & fa = dynamic_cast<FlagAttribute &>(*a);
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
                EXPECT_EQ(exp50.size(), rs1->getNumHits());
                EXPECT_EQ(exp50.size(), rs2->getNumHits());
                for (size_t j = 0; j < exp50.size(); ++j) {
                    EXPECT_EQ(exp50[j], rs1->getArray()[j].getDocId());
                    EXPECT_EQ(exp50[j], rs2->getArray()[j].getDocId());
                }
            }
            {
                ResultSetPtr rs = performSearch(fa, "60");
                EXPECT_EQ(exp60.size(), rs->getNumHits());
                for (size_t j = 0; j < exp60.size(); ++j) {
                    EXPECT_EQ(exp60[j], rs->getArray()[j].getDocId());
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
    auto a = AttributeBuilder(name, cfg).fill({value}).get();
    LOG(info, "requireThatInvalidSearchTermGivesZeroHits: vector '%s'", a->getName().c_str());
    ResultSetPtr rs = performSearch(*a, "foo");
    EXPECT_EQ(0u, rs->getNumHits());
}

TEST_F(SearchContextTest, require_that_invalid_search_term_gives_zero_hits)
{
    for (const auto & cfg : _integerCfg) {
        requireThatInvalidSearchTermGivesZeroHits<IntegerAttribute, int32_t>(cfg.first, cfg.second, 10);
    }
    for (const auto & cfg : _floatCfg) {
        requireThatInvalidSearchTermGivesZeroHits<FloatingPointAttribute, double>(cfg.first, cfg.second, 10.0);
    }
}

TEST_F(SearchContextTest, require_that_flag_attribute_handles_the_byte_range)
{
    LOG(info, "requireThatFlagAttributeHandlesTheByteRange()");
    Config cfg(BasicType::INT8, CollectionType::ARRAY);
    cfg.setFastSearch(true);
    using IL = AttributeBuilder::IntList;
    auto a = AttributeBuilder("flags", cfg).
            fill_array({IL{-128}, {-64, -8}, {0, 8}, {64, 24}, {127}}).get();

    performSearch(*a, "-128", DocSet({1}), TermType::WORD);
    performSearch(*a, "127", DocSet({5}), TermType::WORD);
    performSearch(*a, ">-128", DocSet({2, 3, 4, 5}), TermType::WORD);
    performSearch(*a, "<127", DocSet({1, 2, 3, 4}), TermType::WORD);
    performSearch(*a, "[-128;-8]", DocSet({1, 2}), TermType::WORD);
    performSearch(*a, "[-8;8]", DocSet({2, 3}), TermType::WORD);
    performSearch(*a, "[8;127]", DocSet({3, 4, 5}), TermType::WORD);
    performSearch(*a, "[-129;-8]", DocSet({1, 2}), TermType::WORD);
    performSearch(*a, "[8;128]", DocSet({3, 4, 5}), TermType::WORD);
}

void
SearchContextTest::requireThatOutOfBoundsSearchTermGivesZeroHits(const vespalib::string &name,
                                                                 const Config &cfg,
                                                                 int32_t maxValue)
{
    auto a = AttributeBuilder(name, cfg).fill({maxValue}).get();
    vespalib::string term = vespalib::make_string("%" PRIu64 "", (int64_t) maxValue + 1);
    LOG(info, "requireThatOutOfBoundsSearchTermGivesZeroHits: vector '%s', term '%s'", a->getName().c_str(), term.c_str());
    ResultSetPtr rs = performSearch(*a, term);
    EXPECT_EQ(0u, rs->getNumHits());
}

TEST_F(SearchContextTest, require_that_out_of_bounds_search_term_gives_zero_hits)
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

class BoolAttributeFixture {
private:
    search::SingleBoolAttribute _attr;

public:
    BoolAttributeFixture(const SimpleResult& true_docs, uint32_t num_docs)
        : _attr("bool_attr", search::GrowStrategy(), false)
    {
        _attr.addDocs(num_docs);
        for (uint32_t i = 0; i < true_docs.getHitCount(); ++i) {
            uint32_t docid = true_docs.getHit(i);
            _attr.update(docid, 1);
        }
        _attr.commit();
    }
    std::unique_ptr<SearchContext> create_search_context(const std::string& term) const {
        return _attr.getSearch(std::make_unique<search::QueryTermSimple>(term, search::TermType::WORD),
                               SearchContextParams().useBitVector(true));
    }
    SimpleResult search_context(const std::string& term) const {
        auto search_ctx = create_search_context(term);
        SimpleResult result;
        int32_t weight = 10;
        for (uint32_t docid = 1; docid < _attr.getNumDocs(); ++docid) {
            bool match_1 = search_ctx->matches(docid);
            bool match_2 = search_ctx->matches(docid, weight);
            EXPECT_EQ(match_1, match_2);
            EXPECT_EQ(match_2 ? 1 : 0, weight);
            if (match_1) {
                result.addHit(docid);
            }
            weight = 10;
        }
        return result;
    }
    SimpleResult search_iterator(const std::string& term, bool strict) const {
        auto search_ctx = create_search_context(term);
        TermFieldMatchData tfmd;
        auto itr = search_ctx->createIterator(&tfmd, strict);
        SimpleResult result;
        if (strict) {
            result.searchStrict(*itr, _attr.getNumDocs());
        } else {
            result.search(*itr, _attr.getNumDocs());
        }
        return result;
    }
};

TEST_F(SearchContextTest, single_bool_attribute_search_context_handles_true_and_false_queries)
{
    BoolAttributeFixture f(SimpleResult().addHit(3).addHit(5).addHit(7), 9);

    auto true_exp = SimpleResult().addHit(3).addHit(5).addHit(7);
    EXPECT_EQ(true_exp, f.search_context("true"));
    EXPECT_EQ(true_exp, f.search_context("1"));

    auto false_exp = SimpleResult().addHit(1).addHit(2).addHit(4).addHit(6).addHit(8);
    EXPECT_EQ(false_exp, f.search_context("false"));
    EXPECT_EQ(false_exp, f.search_context("0"));
}

TEST_F(SearchContextTest, single_bool_attribute_search_iterator_handles_true_and_false_queries)
{
    BoolAttributeFixture f(SimpleResult().addHit(3).addHit(5).addHit(7), 9);

    auto true_exp = SimpleResult().addHit(3).addHit(5).addHit(7);
    EXPECT_EQ(true_exp, f.search_iterator("true", false));
    EXPECT_EQ(true_exp, f.search_iterator("1", false));
    EXPECT_EQ(true_exp, f.search_iterator("true", true));
    EXPECT_EQ(true_exp, f.search_iterator("1", true));

    auto false_exp = SimpleResult().addHit(1).addHit(2).addHit(4).addHit(6).addHit(8);
    EXPECT_EQ(false_exp, f.search_iterator("false", false));
    EXPECT_EQ(false_exp, f.search_iterator("0", false));
    EXPECT_EQ(false_exp, f.search_iterator("false", true));
    EXPECT_EQ(false_exp, f.search_iterator("0", true));
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

SearchContextTest::~SearchContextTest() = default;

}

GTEST_MAIN_RUN_ALL_TESTS()

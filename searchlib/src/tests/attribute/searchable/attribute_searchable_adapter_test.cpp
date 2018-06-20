// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/attribute/attribute_blueprint_factory.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/attribute/singlenumericattribute.h>
#include <vespa/searchlib/attribute/singlenumericattribute.hpp>
#include <vespa/searchlib/attribute/singlenumericpostattribute.hpp>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/query/tree/location.h>
#include <vespa/searchlib/query/tree/point.h>
#include <vespa/searchlib/query/tree/predicate_query_term.h>
#include <vespa/searchlib/query/tree/rectangle.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/wand/parallel_weak_and_search.h>
#include <memory>

#include <vespa/log/log.h>
LOG_SETUP("attribute_searchable_adapter_test");

using search::AttributeFactory;
using search::AttributeGuard;
using search::AttributeVector;
using search::IAttributeManager;
using search::IntegerAttribute;
using search::SingleStringExtAttribute;
using search::attribute::IAttributeContext;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::query::Location;
using search::query::Node;
using search::query::Point;
using search::query::PredicateQueryTerm;
using search::query::Rectangle;
using search::query::SimpleDotProduct;
using search::query::SimpleLocationTerm;
using search::query::SimplePredicateQuery;
using search::query::SimplePrefixTerm;
using search::query::SimpleRangeTerm;
using search::query::SimpleSuffixTerm;
using search::query::SimpleSubstringTerm;
using search::query::SimpleStringTerm;
using search::query::SimpleWandTerm;
using search::query::SimpleWeightedSetTerm;
using search::query::Weight;
using search::queryeval::Blueprint;
using search::queryeval::FieldSpec;
using search::queryeval::FakeRequestContext;
using search::queryeval::MinMaxPostingInfo;
using search::queryeval::ParallelWeakAndSearch;
using search::queryeval::PostingInfo;
using search::queryeval::SearchIterator;
using std::vector;
using vespalib::string;
using vespalib::make_string;
using namespace search::attribute;
using namespace search;

namespace {

const string field = "field";
const string other = "other";
const int32_t weight = 1;
const uint32_t num_docs = 1000;

class MyAttributeManager : public IAttributeManager {
    AttributeVector::SP _attribute_vector;
    AttributeVector::SP _other;

public:
    MyAttributeManager(MyAttributeManager && rhs);
    explicit MyAttributeManager(AttributeVector *attr);

    explicit MyAttributeManager(AttributeVector::SP attr);
    ~MyAttributeManager();

    void set_other(AttributeVector::SP attr) {
        _other = attr;
    }

    AttributeGuard::UP getAttribute(const string &name) const override {
        if (name == field) {
            return AttributeGuard::UP(new AttributeGuard(_attribute_vector));
        } else if (name == other) {
            return AttributeGuard::UP(new AttributeGuard(_other));
        } else {
            return AttributeGuard::UP(nullptr);
        }
    }

    std::unique_ptr<attribute::AttributeReadGuard> getAttributeReadGuard(const string &name, bool stableEnumGuard) const override {
        if (name == field && _attribute_vector) {
            return _attribute_vector->makeReadGuard(stableEnumGuard);
        } else if (name == other && _other) {
            return _other->makeReadGuard(stableEnumGuard);
        } else {
            return std::unique_ptr<attribute::AttributeReadGuard>();
        }
    }

    void getAttributeList(vector<AttributeGuard> &) const override {
        assert(!"Not implemented");
    }
    IAttributeContext::UP createContext() const override {
        assert(!"Not implemented");
        return IAttributeContext::UP();
    }
};

struct Result {
    struct Hit {
        uint32_t docid;
        double raw_score;
        int32_t match_weight;
        Hit(uint32_t id, double raw, int32_t match_weight_in)
            : docid(id), raw_score(raw), match_weight(match_weight_in) {}
    };
    size_t est_hits;
    bool est_empty;
    bool has_minmax;
    int32_t min_weight;
    int32_t max_weight;
    size_t wand_hits;
    int64_t wand_initial_threshold;
    double wand_boost_factor;
    std::vector<Hit> hits;
    vespalib::string iterator_dump;

    Result(size_t est_hits_in, bool est_empty_in);
    ~Result();

    void set_minmax(int32_t min, int32_t max) {
        has_minmax = true;
        min_weight = min;
        max_weight = max;
    }
};

Result::Result(size_t est_hits_in, bool est_empty_in)
    : est_hits(est_hits_in), est_empty(est_empty_in), has_minmax(false), min_weight(0), max_weight(0), wand_hits(0),
      wand_initial_threshold(0), wand_boost_factor(0.0), hits(), iterator_dump()
{}

Result::~Result() {}


MyAttributeManager::MyAttributeManager(MyAttributeManager && rhs)
    : IAttributeManager(),
      _attribute_vector(std::move(rhs._attribute_vector)),
      _other(std::move(rhs._other))
{}
MyAttributeManager::MyAttributeManager(AttributeVector *attr)
    : _attribute_vector(attr),
      _other()
{}

MyAttributeManager::MyAttributeManager(AttributeVector::SP attr)
    : _attribute_vector(std::move(attr)),
      _other()
{}

MyAttributeManager::~MyAttributeManager() {}

void extract_posting_info(Result &result, const PostingInfo *postingInfo) {
    if (postingInfo != NULL) {
        const MinMaxPostingInfo *minMax = dynamic_cast<const MinMaxPostingInfo *>(postingInfo);
        if (minMax != NULL) {
            result.set_minmax(minMax->getMinWeight(), minMax->getMaxWeight());
        }
    }
}

void extract_wand_params(Result &result, ParallelWeakAndSearch *wand) {
    if (wand != nullptr) {
        result.wand_hits = wand->getMatchParams().scores.getScoresToTrack();
        result.wand_initial_threshold = wand->getMatchParams().scoreThreshold;
        result.wand_boost_factor = wand->getMatchParams().thresholdBoostFactor;
    }
}

Result do_search(IAttributeManager &attribute_manager, const Node &node, bool strict) {
    uint32_t fieldId = 0;
    AttributeContext ac(attribute_manager);
    FakeRequestContext requestContext(&ac);
    AttributeBlueprintFactory source;
    MatchDataLayout mdl;
    TermFieldHandle handle = mdl.allocTermField(fieldId);
    MatchData::UP match_data = mdl.createMatchData();
    Blueprint::UP bp = source.createBlueprint(requestContext, FieldSpec(field, fieldId, handle), node);
    ASSERT_TRUE(bp.get() != nullptr);
    Result result(bp->getState().estimate().estHits, bp->getState().estimate().empty);
    bp->fetchPostings(strict);
    SearchIterator::UP iterator = bp->createSearch(*match_data, strict);
    ASSERT_TRUE(iterator.get() != nullptr);
    iterator->initRange(1, num_docs);
    extract_posting_info(result, iterator->getPostingInfo());
    extract_wand_params(result, dynamic_cast<ParallelWeakAndSearch*>(iterator.get()));
    result.iterator_dump = iterator->asString();
    for (uint32_t docid = 1; docid < num_docs; ++docid) {
        if (iterator->seek(docid)) {
            iterator->unpack(docid);
            result.hits.emplace_back(docid,
                                     match_data->resolveTermField(handle)->getRawScore(),
                                     match_data->resolveTermField(handle)->getWeight());
        }
    }
    return result;
}

bool search(const Node &node, IAttributeManager &attribute_manager,
            bool fast_search = false, bool strict = true, bool empty = false)
{
    Result result = do_search(attribute_manager, node, strict);
    if (fast_search) {
        EXPECT_LESS(result.est_hits, num_docs / 10);
    } else {
        if (empty) {
            EXPECT_TRUE(result.est_empty);
            EXPECT_EQUAL(0u, result.est_hits);
        } else {
            EXPECT_TRUE(!result.est_empty);
            EXPECT_EQUAL(num_docs, result.est_hits);
        }
    }
    return (result.hits.size() == 1) && (result.hits[0].docid == (num_docs - 1));
}

bool search(const string &term, IAttributeManager &attribute_manager,
            bool fast_search = false, bool strict = true, bool empty = false)
{
    TEST_STATE(term.c_str());
    SimpleStringTerm node(term, "field", 0, Weight(0));
    return search(node, attribute_manager, fast_search, strict, empty);
}

template <typename T> struct AttributeVectorTypeFinder {
    //typedef search::SingleValueStringAttribute Type;
    typedef SingleStringExtAttribute Type;
    static void add(Type & a, const T & v) { a.add(v, weight); }
};
template <> struct AttributeVectorTypeFinder<int64_t> {
    typedef search::SingleValueNumericAttribute<search::IntegerAttributeTemplate<int64_t> > Type;
    static void add(Type & a, int64_t v) { a.set(a.getNumDocs()-1, v); a.commit(); }
};

void add_docs(AttributeVector *attr, size_t n) {
    AttributeVector::DocId docid;
    for (size_t i = 0; i < n; ++i) {
        attr->addDoc(docid);
        if (attr->inherits(PredicateAttribute::classId)) {
            const_cast<uint8_t *>(static_cast<PredicateAttribute *>(attr)->getMinFeatureVector().first)[docid] = 0;
        }
    }
    ASSERT_EQUAL(n - 1, docid);
}

template <typename T>
MyAttributeManager makeAttributeManager(T value) {
    typedef AttributeVectorTypeFinder<T> AT;
    typedef typename AT::Type AttributeVectorType;
    AttributeVectorType *attr = new AttributeVectorType(field);
    add_docs(attr, num_docs);
    AT::add(*attr, value);
    return MyAttributeManager(attr);
}

MyAttributeManager makeFastSearchLongAttributeManager(int64_t value) {
    Config cfg(BasicType::INT64, CollectionType::SINGLE);
    cfg.setFastSearch(true);
    AttributeVector::SP attr_ptr = AttributeFactory::createAttribute(field, cfg);
    IntegerAttribute *attr = static_cast<IntegerAttribute *>(attr_ptr.get());
    add_docs(attr, num_docs);
    attr->update(num_docs - 1, value);
    attr->commit();
    return MyAttributeManager(attr_ptr);
}

TEST("requireThatIteratorsCanBeCreated") {
    MyAttributeManager attribute_manager = makeAttributeManager("foo");

    EXPECT_TRUE(search("foo", attribute_manager));
}

TEST("require that missing attribute produces empty search")
{
    MyAttributeManager attribute_manager(nullptr);
    EXPECT_FALSE(search("foo", attribute_manager, false, false, true));
}

TEST("requireThatRangeTermsWorkToo") {
    MyAttributeManager attribute_manager = makeAttributeManager(int64_t(42));

    EXPECT_TRUE(search("[23;46]", attribute_manager));
    EXPECT_TRUE(!search("[10;23]", attribute_manager));
    EXPECT_TRUE(!search(">43", attribute_manager));
    EXPECT_TRUE(search("[10;]", attribute_manager));
}

TEST("requireThatPrefixTermsWork") {
    MyAttributeManager attribute_manager = makeAttributeManager("foo");

    SimplePrefixTerm node("fo", "field", 0, Weight(0));
    EXPECT_TRUE(search(node, attribute_manager));
}

TEST("requireThatLocationTermsWork") {
    // 0xcc is z-curve for (10, 10).
    MyAttributeManager attribute_manager = makeAttributeManager(int64_t(0xcc));

    SimpleLocationTerm node(Location(Point(10, 10), 3, 0), field, 0, Weight(0));
    EXPECT_TRUE(search(node, attribute_manager));
    node = SimpleLocationTerm(Location(Point(100, 100), 3, 0), field, 0, Weight(0));
    EXPECT_TRUE(!search(node, attribute_manager));
    node = SimpleLocationTerm(Location(Point(13, 13), 4, 0), field, 0, Weight(0));
    EXPECT_TRUE(!search(node, attribute_manager));
    node = SimpleLocationTerm(Location(Point(10, 13), 3, 0), field, 0, Weight(0));
    EXPECT_TRUE(search(node, attribute_manager));
}

TEST("requireThatOptimizedLocationTermsWork") {
    // 0xcc is z-curve for (10, 10).
    MyAttributeManager attribute_manager = makeFastSearchLongAttributeManager(int64_t(0xcc));

    SimpleLocationTerm node(Location(Point(10, 10), 3, 0), field, 0, Weight(0));
    EXPECT_TRUE(search(node, attribute_manager, true));
    node = SimpleLocationTerm(Location(Point(100, 100), 3, 0), field, 0, Weight(0));
    EXPECT_TRUE(!search(node, attribute_manager, true));
    node = SimpleLocationTerm(Location(Point(13, 13), 4, 0), field, 0, Weight(0));
    EXPECT_TRUE(!search(node, attribute_manager, true));
    node = SimpleLocationTerm(Location(Point(10, 13), 3, 0), field, 0, Weight(0));
    EXPECT_TRUE(search(node, attribute_manager, true));
}

TEST("require that optimized location search works with wrapped bounding box (no hits)") {
    // 0xcc is z-curve for (10, 10).
    MyAttributeManager attribute_manager = makeFastSearchLongAttributeManager(int64_t(0xcc));
    SimpleLocationTerm term1(Location(Rectangle(5, 5, 15, 15)), field, 0, Weight(0)); // unwrapped
    SimpleLocationTerm term2(Location(Rectangle(15, 5, 5, 15)), field, 0, Weight(0)); // wrapped x
    SimpleLocationTerm term3(Location(Rectangle(5, 15, 15, 5)), field, 0, Weight(0)); // wrapped y
    Result result1 = do_search(attribute_manager, term1, true);
    Result result2 = do_search(attribute_manager, term2, true);
    Result result3 = do_search(attribute_manager, term3, true);
    EXPECT_EQUAL(1u, result1.hits.size());
    EXPECT_EQUAL(0u, result2.hits.size());
    EXPECT_EQUAL(0u, result3.hits.size());
    EXPECT_TRUE(result1.iterator_dump.find("LocationPreFilterIterator") != vespalib::string::npos);
    EXPECT_TRUE(result2.iterator_dump.find("EmptySearch") != vespalib::string::npos);
    EXPECT_TRUE(result3.iterator_dump.find("EmptySearch") != vespalib::string::npos);
}

void set_weights(StringAttribute *attr, uint32_t docid,
                 int32_t foo_weight, int32_t bar_weight, int32_t baz_weight)
{
    attr->clearDoc(docid);
    if (foo_weight > 0) attr->append(docid, "foo", foo_weight);
    if (bar_weight > 0) attr->append(docid, "bar", bar_weight);
    if (baz_weight > 0) attr->append(docid, "baz", baz_weight);
    attr->commit();
}

MyAttributeManager make_weighted_string_attribute_manager(bool fast_search, bool isFilter = false) {
    Config cfg(BasicType::STRING, CollectionType::WSET);
    cfg.setFastSearch(fast_search);
    cfg.setIsFilter(isFilter);
    AttributeVector::SP attr_ptr = AttributeFactory::createAttribute(field, cfg);
    StringAttribute *attr = static_cast<StringAttribute *>(attr_ptr.get());
    add_docs(attr, num_docs);
    set_weights(attr, 10,    0, 200,   0);
    set_weights(attr, 20,  100, 200, 300);
    set_weights(attr, 30,    0,   0, 300);
    set_weights(attr, 40,  100,   0,   0);
    set_weights(attr, 50, 1000,   0, 300);
    MyAttributeManager attribute_manager(attr_ptr);
    return attribute_manager;
}

TEST("require that attribute dot product works") {
    for (int i = 0; i <= 0x3; ++i) {
        bool fast_search = ((i & 0x1) != 0);
        bool strict = ((i & 0x2) != 0);
        MyAttributeManager attribute_manager = make_weighted_string_attribute_manager(fast_search);
        SimpleDotProduct node(field, 0, Weight(1));
        node.append(Node::UP(new SimpleStringTerm("foo", "", 0, Weight(1))));
        node.append(Node::UP(new SimpleStringTerm("bar", "", 0, Weight(1))));
        node.append(Node::UP(new SimpleStringTerm("baz", "", 0, Weight(1))));
        node.append(Node::UP(new SimpleStringTerm("fox", "", 0, Weight(1))));
        Result result = do_search(attribute_manager, node, strict);
        ASSERT_EQUAL(5u, result.hits.size());
        if (fast_search) {
            EXPECT_EQUAL(8u, result.est_hits);
        } else {
            // 'fox' is detected to produce no hits since it has no enum value
            EXPECT_EQUAL(num_docs * 3, result.est_hits);
        }
        EXPECT_FALSE(result.est_empty);
        EXPECT_EQUAL(10u, result.hits[0].docid);
        EXPECT_EQUAL(200.0, result.hits[0].raw_score);
        EXPECT_EQUAL(20u, result.hits[1].docid);
        EXPECT_EQUAL(600.0, result.hits[1].raw_score);
        EXPECT_EQUAL(30u, result.hits[2].docid);
        EXPECT_EQUAL(300.0, result.hits[2].raw_score);
        EXPECT_EQUAL(40u, result.hits[3].docid);
        EXPECT_EQUAL(100.0, result.hits[3].raw_score);
        EXPECT_EQUAL(50u, result.hits[4].docid);
        EXPECT_EQUAL(1300.0, result.hits[4].raw_score);
    }
}

TEST("require that attribute dot product can produce no hits") {
    for (int i = 0; i <= 0x3; ++i) {
        bool fast_search = ((i & 0x1) != 0);
        bool strict = ((i & 0x2) != 0);
        MyAttributeManager attribute_manager = make_weighted_string_attribute_manager(fast_search);
        SimpleDotProduct node(field, 0, Weight(1));
        node.append(Node::UP(new SimpleStringTerm("notfoo", "", 0, Weight(1))));
        node.append(Node::UP(new SimpleStringTerm("notbar", "", 0, Weight(1))));
        node.append(Node::UP(new SimpleStringTerm("notbaz", "", 0, Weight(1))));
        node.append(Node::UP(new SimpleStringTerm("notfox", "", 0, Weight(1))));
        Result result = do_search(attribute_manager, node, strict);
        ASSERT_EQUAL(0u, result.hits.size());
        EXPECT_EQUAL(0u, result.est_hits);
        EXPECT_TRUE(result.est_empty);
    }
}

TEST("require that direct attribute iterators work") {
    for (int i = 0; i <= 0x3; ++i) {
        bool fast_search = ((i & 0x1) != 0);
        bool strict = ((i & 0x2) != 0);
        MyAttributeManager attribute_manager = make_weighted_string_attribute_manager(fast_search);
        SimpleStringTerm empty_node("notfoo", "", 0, Weight(1));
        Result empty_result = do_search(attribute_manager, empty_node, strict);
        EXPECT_EQUAL(0u, empty_result.hits.size());
        SimpleStringTerm node("foo", "", 0, Weight(1));
        Result result = do_search(attribute_manager, node, strict);
        if (fast_search) {
            EXPECT_EQUAL(3u, result.est_hits);
            EXPECT_TRUE(result.has_minmax);
            EXPECT_EQUAL(100, result.min_weight);
            EXPECT_EQUAL(1000, result.max_weight);
            EXPECT_TRUE(result.iterator_dump.find("DocumentWeightSearchIterator") != vespalib::string::npos);
        } else {
            EXPECT_EQUAL(num_docs, result.est_hits);
            EXPECT_FALSE(result.has_minmax);
            EXPECT_TRUE(result.iterator_dump.find("DocumentWeightSearchIterator") == vespalib::string::npos);
        }
        ASSERT_EQUAL(3u, result.hits.size());
        EXPECT_FALSE(result.est_empty);
        EXPECT_EQUAL(20u, result.hits[0].docid);
        EXPECT_EQUAL(40u, result.hits[1].docid);
        EXPECT_EQUAL(50u, result.hits[2].docid);
    }
}

TEST("require that single weighted set turns filter on filter fields") {
        bool fast_search = true;
        bool strict = true;
        bool isFilter = true;
        MyAttributeManager attribute_manager = make_weighted_string_attribute_manager(fast_search, isFilter);
        SimpleStringTerm empty_node("notfoo", "", 0, Weight(1));
        Result empty_result = do_search(attribute_manager, empty_node, strict);
        EXPECT_EQUAL(0u, empty_result.hits.size());
        SimpleStringTerm node("foo", "", 0, Weight(1));
        Result result = do_search(attribute_manager, node, strict);
        EXPECT_EQUAL(3u, result.est_hits);
        EXPECT_TRUE(result.iterator_dump.find("DocumentWeightSearchIterator") == vespalib::string::npos);
        EXPECT_TRUE(result.iterator_dump.find("FilterAttributePostingListIteratorT") != vespalib::string::npos);
        ASSERT_EQUAL(3u, result.hits.size());
        EXPECT_FALSE(result.est_empty);
        EXPECT_EQUAL(20u, result.hits[0].docid);
        EXPECT_EQUAL(40u, result.hits[1].docid);
        EXPECT_EQUAL(50u, result.hits[2].docid);
}

const char *as_str(bool flag) { return flag? "true" : "false"; }

TEST("require that attribute parallel wand works") {
    for (int i = 0; i <= 0x3; ++i) {
        bool fast_search = ((i & 0x1) != 0);
        bool strict = ((i & 0x2) != 0);
        MyAttributeManager attribute_manager = make_weighted_string_attribute_manager(fast_search);
        SimpleWandTerm node(field, 0, Weight(1), 10, 500, 1.5);
        node.append(Node::UP(new SimpleStringTerm("foo", "", 0, Weight(1))));
        node.append(Node::UP(new SimpleStringTerm("bar", "", 0, Weight(1))));
        node.append(Node::UP(new SimpleStringTerm("baz", "", 0, Weight(1))));
        node.append(Node::UP(new SimpleStringTerm("fox", "", 0, Weight(1))));
        Result result = do_search(attribute_manager, node, strict);
        EXPECT_FALSE(result.est_empty);
        if (fast_search) {
            EXPECT_EQUAL(8u, result.est_hits);
        } else {
            // 'fox' is detected to produce no hits since it has no enum value
            EXPECT_EQUAL(num_docs * 3, result.est_hits);
        }
        if (EXPECT_EQUAL(2u, result.hits.size())) {
            if (result.iterator_dump.find("MonitoringDumpIterator") == vespalib::string::npos) {
                EXPECT_EQUAL(10u, result.wand_hits);
                EXPECT_EQUAL(500, result.wand_initial_threshold);
                EXPECT_EQUAL(1.5, result.wand_boost_factor);
            }
            EXPECT_EQUAL(20u, result.hits[0].docid);
            EXPECT_EQUAL(600.0, result.hits[0].raw_score);
            EXPECT_EQUAL(50u, result.hits[1].docid);
            EXPECT_EQUAL(1300.0, result.hits[1].raw_score);
        } else {
            fprintf(stderr, "    (fast_search: %s, strict: %s)\n",
                    as_str(fast_search), as_str(strict));
            LOG_ABORT("should not reach here");
        }
    }
}

TEST("require that attribute weighted set term works") {
    for (int i = 0; i <= 0x3; ++i) {
        bool fast_search = ((i & 0x1) != 0);
        bool strict = ((i & 0x2) != 0);
        MyAttributeManager attribute_manager = make_weighted_string_attribute_manager(fast_search);
        SimpleWeightedSetTerm node(field, 0, Weight(1));
        node.append(Node::UP(new SimpleStringTerm("foo", "", 0, Weight(10))));
        node.append(Node::UP(new SimpleStringTerm("bar", "", 0, Weight(20))));
        node.append(Node::UP(new SimpleStringTerm("baz", "", 0, Weight(30))));
        node.append(Node::UP(new SimpleStringTerm("fox", "", 0, Weight(40))));
        Result result = do_search(attribute_manager, node, strict);
        EXPECT_FALSE(result.est_empty);
        ASSERT_EQUAL(5u, result.hits.size());
        if (fast_search && result.iterator_dump.find("MonitoringDumpIterator") == vespalib::string::npos) {
            fprintf(stderr, "DUMP: %s\n", result.iterator_dump.c_str());
            EXPECT_TRUE(result.iterator_dump.find("AttributeIteratorPack") != vespalib::string::npos);
        }
        EXPECT_EQUAL(10u, result.hits[0].docid);
        EXPECT_EQUAL(20, result.hits[0].match_weight);
        EXPECT_EQUAL(20u, result.hits[1].docid);
        EXPECT_EQUAL(30, result.hits[1].match_weight);
        EXPECT_EQUAL(30u, result.hits[2].docid);
        EXPECT_EQUAL(30, result.hits[2].match_weight);
        EXPECT_EQUAL(40u, result.hits[3].docid);
        EXPECT_EQUAL(10, result.hits[3].match_weight);
        EXPECT_EQUAL(50u, result.hits[4].docid);
        EXPECT_EQUAL(30, result.hits[4].match_weight);
    }
}

TEST("require that predicate query in non-predicate field yields empty.") {
    MyAttributeManager attribute_manager = makeAttributeManager("foo");

    PredicateQueryTerm::UP term(new PredicateQueryTerm);
    SimplePredicateQuery node(std::move(term), field, 0, Weight(1));
    Result result = do_search(attribute_manager, node, true);
    EXPECT_TRUE(result.est_empty);
    EXPECT_EQUAL(0u, result.hits.size());
}

TEST("require that predicate query in predicate field yields results.") {
    PredicateAttribute *attr = new PredicateAttribute(field, Config(BasicType::PREDICATE, CollectionType::SINGLE));
    add_docs(attr, num_docs);
    attr->getIndex().indexEmptyDocument(2);  // matches anything
    attr->getIndex().commit();
    const_cast<PredicateAttribute::IntervalRange *>(attr->getIntervalRangeVector())[2] = 1u;
    MyAttributeManager attribute_manager(attr);

    PredicateQueryTerm::UP term(new PredicateQueryTerm);
    SimplePredicateQuery node(std::move(term), field, 0, Weight(1));
    Result result = do_search(attribute_manager, node, true);
    EXPECT_FALSE(result.est_empty);
    EXPECT_EQUAL(1u, result.hits.size());
}

TEST("require that substring terms work") {
    MyAttributeManager attribute_manager = make_weighted_string_attribute_manager(true);
    SimpleSubstringTerm node("a", "", 0, Weight(1));
    Result result = do_search(attribute_manager, node, true);
    ASSERT_EQUAL(4u, result.hits.size());
    EXPECT_EQUAL(10u, result.hits[0].docid);
    EXPECT_EQUAL(20u, result.hits[1].docid);
    EXPECT_EQUAL(30u, result.hits[2].docid);
    EXPECT_EQUAL(50u, result.hits[3].docid);
}

TEST("require that suffix terms work") {
    MyAttributeManager attribute_manager = make_weighted_string_attribute_manager(true);
    SimpleSuffixTerm node("oo", "", 0, Weight(1));
    Result result = do_search(attribute_manager, node, true);
    ASSERT_EQUAL(3u, result.hits.size());
    EXPECT_EQUAL(20u, result.hits[0].docid);
    EXPECT_EQUAL(40u, result.hits[1].docid);
    EXPECT_EQUAL(50u, result.hits[2].docid);
}

void set_attr_value(AttributeVector &attr, uint32_t docid, size_t value) {
    IntegerAttribute *int_attr = dynamic_cast<IntegerAttribute *>(&attr);
    FloatingPointAttribute *float_attr = dynamic_cast<FloatingPointAttribute *>(&attr);
    StringAttribute *string_attr = dynamic_cast<StringAttribute *>(&attr);
    if (int_attr != nullptr) {
        int_attr->update(docid, value);
        int_attr->commit();
    } else if (float_attr != nullptr) {
        float_attr->update(docid, value);
        float_attr->commit();
    } else if (string_attr != nullptr) {
        ASSERT_LESS(value, size_t(27*26 + 26));
        vespalib::string str;
        str.push_back('a' + value / 27);
        str.push_back('a' + value % 27);
        string_attr->update(docid, str);
        string_attr->commit();    
    } else {
        ASSERT_TRUE(false);
    }
}

MyAttributeManager make_diversity_setup(BasicType::Type field_type, bool field_fast_search,
                                        BasicType::Type other_type, bool other_fast_search)
{
    Config field_cfg(field_type, CollectionType::SINGLE);
    field_cfg.setFastSearch(field_fast_search);
    AttributeVector::SP field_attr = AttributeFactory::createAttribute(field, field_cfg);
    Config other_cfg(other_type, CollectionType::SINGLE);
    other_cfg.setFastSearch(other_fast_search);
    AttributeVector::SP other_attr = AttributeFactory::createAttribute(other, other_cfg);
    add_docs(&*field_attr, num_docs);
    add_docs(&*other_attr, num_docs);
    for (size_t i = 1; i < num_docs; ++i) {
        set_attr_value(*field_attr, i, i / 5);
        set_attr_value(*other_attr, i, i / 10);
    }
    MyAttributeManager attribute_manager(field_attr);
    attribute_manager.set_other(other_attr);
    return attribute_manager;
}

size_t diversity_hits(IAttributeManager &manager, const vespalib::string &term, bool strict) {
    SimpleRangeTerm node(term, "", 0, Weight(1));
    Result result = do_search(manager, node, strict);
    return result.hits.size();
}

std::pair<size_t,size_t> diversity_docid_range(IAttributeManager &manager, const vespalib::string &term, bool strict) {
    SimpleRangeTerm node(term, "", 0, Weight(1));
    Result result = do_search(manager, node, strict);
    std::pair<size_t, size_t> range(0, 0);
    for (const Result::Hit &hit: result.hits) {
        if (range.first == 0) {
            range.first = hit.docid;
            range.second = hit.docid;
        } else {
            EXPECT_GREATER(size_t(hit.docid), range.second);
            range.second = hit.docid;
        }
    }
    return range;
}

TEST("require that diversity range searches work for various types") {
    for (auto field_type: std::vector<BasicType::Type>({BasicType::INT32, BasicType::DOUBLE})) {
        for (auto other_type: std::vector<BasicType::Type>({BasicType::INT16, BasicType::INT32, BasicType::INT64,
                            BasicType::FLOAT, BasicType::DOUBLE, BasicType::STRING}))
        {
            for (bool other_fast_search: std::vector<bool>({true, false})) {
                MyAttributeManager manager = make_diversity_setup(field_type, true, other_type, other_fast_search);
                for (bool strict: std::vector<bool>({true, false})) {
                    TEST_STATE(make_string("field_type: %s, other_type: %s, other_fast_search: %s, strict: %s",
                                           BasicType(field_type).asString(), BasicType(other_type).asString(),
                                           other_fast_search ? "true" : "false", strict ? "true" : "false").c_str());
                    EXPECT_EQUAL(999u, diversity_hits(manager, "[;;1000;other;10]", strict));
                    EXPECT_EQUAL(999u, diversity_hits(manager, "[;;-1000;other;10]", strict));
                    EXPECT_EQUAL(100u, diversity_hits(manager, "[;;1000;other;1]", strict));
                    EXPECT_EQUAL(100u, diversity_hits(manager, "[;;-1000;other;1]", strict));
                    EXPECT_EQUAL(300u, diversity_hits(manager, "[;;1000;other;3]", strict));
                    EXPECT_EQUAL(300u, diversity_hits(manager, "[;;-1000;other;3]", strict));
                    EXPECT_EQUAL(10u, diversity_hits(manager, "[;;10;other;3]", strict));
                    EXPECT_EQUAL(10u, diversity_hits(manager, "[;;-10;other;3]", strict));
                    EXPECT_EQUAL(1u, diversity_docid_range(manager, "[;;10;other;3]", strict).first);
                    EXPECT_EQUAL(30u, diversity_docid_range(manager, "[;;10;other;3]", strict).second);
                    EXPECT_EQUAL(965u, diversity_docid_range(manager, "[;;-10;other;3]", strict).first);
                    EXPECT_EQUAL(997u, diversity_docid_range(manager, "[;;-10;other;3]", strict).second);
                }
            }
        }
    }
}

TEST("require that diversity also works for a single unique value") {
    MyAttributeManager manager = make_diversity_setup(BasicType::INT32, true, BasicType::INT32, true);
    EXPECT_EQUAL(2u, diversity_hits(manager, "[2;2;100;other;2]", true));
    EXPECT_EQUAL(2u, diversity_hits(manager, "[2;2;-100;other;2]", true));
    EXPECT_EQUAL(2u, diversity_hits(manager, "[2;2;100;other;2]", false));
    EXPECT_EQUAL(2u, diversity_hits(manager, "[2;2;-100;other;2]", false));
}

TEST("require that diversity range searches gives empty results for non-existing diversity attributes") {
    MyAttributeManager manager = make_diversity_setup(BasicType::INT32, true, BasicType::INT32, true);
    EXPECT_EQUAL(0u, diversity_hits(manager, "[;;1000;bogus;10]", true));
    EXPECT_EQUAL(0u, diversity_hits(manager, "[;;-1000;bogus;10]", true));
    EXPECT_EQUAL(0u, diversity_hits(manager, "[;;1000;;10]", true));
    EXPECT_EQUAL(0u, diversity_hits(manager, "[;;-1000;;10]", true));
}

TEST("require that loose diversity gives enough diversity and hits while doing less work") {
    MyAttributeManager manager = make_diversity_setup(BasicType::INT32, true, BasicType::INT32, true);
    EXPECT_EQUAL(999u, diversity_hits(manager, "[;;1000;other;10;4;loose]", true));
    EXPECT_EQUAL(1u, diversity_docid_range(manager, "[;;10;other;3;2;loose]", true).first);
    EXPECT_EQUAL(16u, diversity_docid_range(manager, "[;;10;other;3;2;loose]", true).second);
}

TEST("require that strict diversity gives enough diversity and hits while doing less work, even though more than loose, but more correct than loose") {
    MyAttributeManager manager = make_diversity_setup(BasicType::INT32, true, BasicType::INT32, true);
    EXPECT_EQUAL(999u, diversity_hits(manager, "[;;-1000;other;10;4;strict]", true));
    EXPECT_EQUAL(1u, diversity_docid_range(manager, "[;;10;other;3;2;strict]", true).first);
    EXPECT_EQUAL(23u, diversity_docid_range(manager, "[;;10;other;3;2;strict]", true).second);
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }

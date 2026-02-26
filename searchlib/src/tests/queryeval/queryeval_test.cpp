// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/regex/regex.h>
#include <vespa/searchlib/test/initrange.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/booleanmatchiteratorwrapper.h>
#include <vespa/searchlib/queryeval/i_element_gap_inspector.h>
#include <vespa/searchlib/queryeval/nearsearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/queryeval/queryeval_stats.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/simplesearch.h>
#include <vespa/searchlib/queryeval/ranksearch.h>
#include <vespa/searchlib/queryeval/truesearch.h>
#include <vespa/searchlib/queryeval/sourceblendersearch.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/searchlib/queryeval/test/mock_element_gap_inspector.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/attribute/singleboolattribute.h>
#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("query_eval_test");

using namespace search::queryeval;
using search::BitVector;
using search::attribute::SearchContextParams;
using search::fef::MatchData;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::queryeval::test::MockElementGapInspector;
using search::test::InitRangeVerifier;

//-----------------------------------------------------------------------------

constexpr auto lower_bound = Blueprint::FilterConstraint::LOWER_BOUND;
constexpr auto upper_bound = Blueprint::FilterConstraint::UPPER_BOUND;

template <typename T, typename V=std::vector<T> >
class Collect
{
private:
    V _data;

public:
    Collect &add(const T &t) {
        _data.push_back(t);
        return *this;
    }
    operator const V &() const { return _data; }
};

SearchIterator *simple(const std::string &tag) {
    return &((new SimpleSearch(SimpleResult()))->tag(tag));
}

MultiSearch::Children search2(const std::string &t1, const std::string &t2) {
    MultiSearch::Children children;
    children.emplace_back(simple(t1));
    children.emplace_back(simple(t2));
    return children;
}

class ISourceSelectorDummy : public ISourceSelector
{
    static SourceStore _sourceStoreDummy;

public:
    static std::unique_ptr<sourceselector::Iterator>
    makeDummyIterator()
    {
        return std::make_unique<sourceselector::Iterator>(_sourceStoreDummy);
    }
};

ISourceSelector::SourceStore ISourceSelectorDummy::_sourceStoreDummy("foo");

std::unique_ptr<sourceselector::Iterator> selector() {
    return ISourceSelectorDummy::makeDummyIterator();
}

MockElementGapInspector mock_element_gap_inspector(std::nullopt);

//-----------------------------------------------------------------------------

void testMultiSearch(SearchIterator & search) {
    auto & ms = dynamic_cast<MultiSearch &>(search);
    ms.initRange(3, 309);
    EXPECT_EQ(2u, ms.getDocId());
    EXPECT_EQ(309u, ms.getEndId());
    for (const auto & child : ms.getChildren()) {
        EXPECT_EQ(2u, child->getDocId());
        EXPECT_EQ(309u, child->getEndId());
    }
}

TEST(QueryEvalTest, test_that_or_andwith_is_a_noop)
{
    TermFieldMatchData tfmd;
    MultiSearch::Children ch;
    ch.emplace_back(new TrueSearch(tfmd));
    ch.emplace_back(new TrueSearch(tfmd));
    SearchIterator::UP search(OrSearch::create(std::move(ch), true));
    auto filter = std::make_unique<TrueSearch>(tfmd);

    EXPECT_TRUE(search->andWith(std::move(filter), 1));
}

TEST(QueryEvalTest, test_that_non_strict_and_andwidth_is_a_noop)
{
    TermFieldMatchData tfmd;
    MultiSearch::Children ch;
    ch.emplace_back(new TrueSearch(tfmd));
    ch.emplace_back(new TrueSearch(tfmd));
    SearchIterator::UP search(AndSearch::create(std::move(ch), false));
    SearchIterator::UP filter = std::make_unique<TrueSearch>(tfmd);
    filter = search->andWith(std::move(filter), 8);
    EXPECT_TRUE(filter);
}

TEST(QueryEvalTest, test_that_strict_and_andwidth_steals_filter_and_places_it_correctly_based_on_estimate)
{
    TermFieldMatchData tfmd;
    std::vector<SearchIterator *> ch;
    ch.emplace_back(new TrueSearch(tfmd));
    ch.emplace_back(new TrueSearch(tfmd));
    SearchIterator::UP search(AndSearch::create({ch[0], ch[1]}, true));
    dynamic_cast<AndSearch &>(*search).estimate(7);
    auto filter = std::make_unique<TrueSearch>(tfmd);
    SearchIterator * filterP = filter.get();

    EXPECT_TRUE(nullptr == search->andWith(std::move(filter), 8).get());
    const auto & andChildren = dynamic_cast<MultiSearch &>(*search).getChildren();
    EXPECT_EQ(3u, andChildren.size());
    EXPECT_EQ(ch[0], andChildren[0].get());
    EXPECT_EQ(filterP, andChildren[1].get());
    EXPECT_EQ(ch[1], andChildren[2].get());

    auto filter2 = std::make_unique<TrueSearch>(tfmd);
    SearchIterator * filter2P = filter2.get();
    EXPECT_TRUE(nullptr == search->andWith(std::move(filter2), 6).get());
    EXPECT_EQ(4u, andChildren.size());
    EXPECT_EQ(filter2P, andChildren[0].get());
    EXPECT_EQ(ch[0], andChildren[1].get());
    EXPECT_EQ(filterP, andChildren[2].get());
    EXPECT_EQ(ch[1], andChildren[3].get());
}

class NonStrictTrueSearch : public TrueSearch
{
public:
    explicit NonStrictTrueSearch(TermFieldMatchData & tfmd) : TrueSearch(tfmd) { }
    [[nodiscard]] Trinary is_strict() const override { return Trinary::False; }
};

TEST(QueryEvalTest, test_that_strict_and_andwidth_does_not_place_non_strict_iterator_first)
{
    TermFieldMatchData tfmd;
    std::vector<SearchIterator *> ch;
    ch.emplace_back(new TrueSearch(tfmd));
    ch.emplace_back(new TrueSearch(tfmd));
    SearchIterator::UP search(AndSearch::create({ch[0], ch[1]}, true));
    dynamic_cast<AndSearch &>(*search).estimate(7);
    auto filter = std::make_unique<NonStrictTrueSearch>(tfmd);
    SearchIterator * filterP = filter.get();
    EXPECT_TRUE(nullptr == search->andWith(std::move(filter), 6).get());
    const auto & andChildren = dynamic_cast<MultiSearch &>(*search).getChildren();
    EXPECT_EQ(3u, andChildren.size());
    EXPECT_EQ(ch[0], andChildren[0].get());
    EXPECT_EQ(filterP, andChildren[1].get());
    EXPECT_EQ(ch[1], andChildren[2].get());
}

TEST(QueryEvalTest, test_that_strict_rank_search_forwards_to_its_greedy_first_child)
{
    TermFieldMatchData tfmd;
    SearchIterator::UP search = RankSearch::create({ AndSearch::create(search2("a", "b"), true), new TrueSearch(tfmd) }, true);
    auto filter = std::make_unique<TrueSearch>(tfmd);
    EXPECT_TRUE(nullptr == search->andWith(std::move(filter), 8).get());
}

TEST(QueryEvalTest, test_that_non_strict_rank_search_does_not_forward_to_its_greedy_first_child)
{
    TermFieldMatchData tfmd;
    SearchIterator::UP search = RankSearch::create({ AndSearch::create(search2("a", "b"), true), new TrueSearch(tfmd) }, false);
    auto filter = std::make_unique<TrueSearch>(tfmd);
    EXPECT_TRUE(nullptr != search->andWith(std::move(filter), 8).get());
}

TEST(QueryEvalTest, test_that_strict_andnot_search_forwards_to_its_greedy_first_child)
{
    TermFieldMatchData tfmd;
    SearchIterator::UP search = AndNotSearch::create({ AndSearch::create(search2("a", "b"), true), new TrueSearch(tfmd) }, true);
    auto filter = std::make_unique<TrueSearch>(tfmd);
    EXPECT_TRUE(nullptr == search->andWith(std::move(filter), 8).get());
}

TEST(QueryEvalTest, test_that_non_strict_andnot_search_does_not_forward_to_its_greedy_first_child)
{
    TermFieldMatchData tfmd;
    SearchIterator::UP search = AndNotSearch::create({ AndSearch::create(search2("a", "b"), true), new TrueSearch(tfmd) }, false);
    auto filter = std::make_unique<TrueSearch>(tfmd);
    EXPECT_TRUE(nullptr != search->andWith(std::move(filter), 8).get());
}

void expect_match(std::string input, std::string regexp) {
    using vespalib::Regex;
    Regex pattern = Regex::from_pattern(regexp, Regex::Options::DotMatchesNewline);
    EXPECT_TRUE(pattern.partial_match(input)) << "no match for pattern: >>>" << regexp << "<<< in input: >>>\n" << input << "<<<";
}

TEST(QueryEvalTest, test_and) {
    SimpleResult a;
    SimpleResult b;
    a.addHit(5).addHit(10).addHit(16).addHit(30);
    b.addHit(3).addHit(5).addHit(17).addHit(30).addHit(52);

    MatchData::UP md(MatchData::makeTestInstance(100, 10));
    auto and_b = std::make_unique<AndBlueprint>();
    and_b->addChild(std::make_unique<SimpleBlueprint>(a));
    and_b->addChild(std::make_unique<SimpleBlueprint>(b));
    and_b->basic_plan(true, 1000);
    and_b->fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP and_ab = and_b->createSearch(*md);

    EXPECT_TRUE(dynamic_cast<const AndSearch *>(and_ab.get()) != nullptr);
    EXPECT_EQ(4u, dynamic_cast<AndSearch &>(*and_ab).estimate());
    SimpleResult res;
    res.search(*and_ab, 1000);
    SimpleResult expect;
    expect.addHit(5).addHit(30);
    EXPECT_EQ(res, expect);

    SearchIterator::UP filter_ab = and_b->createFilterSearch(upper_bound);
    SimpleResult filter_res;
    filter_res.search(*filter_ab, 1000);
    EXPECT_EQ(res, expect);
    std::string dump = filter_ab->asString();
    expect_match(dump, "upper");
    expect_match(dump, "AndSearchNoStrict.*NoUnpack.*SimpleSearch.*upper.*SimpleSearch.*upper");
    and_b->basic_plan(false, 1000);
    and_b->fetchPostings(ExecuteInfo::FULL);
    filter_ab = and_b->createFilterSearch(lower_bound);
    dump = filter_ab->asString();
    expect_match(dump, "lower");
    expect_match(dump, "AndSearchNoStrict.*NoUnpack.*SimpleSearch.*lower.*SimpleSearch.*lower");
}

TEST(QueryEvalTest, test_or)
{
    {
        SimpleResult a;
        SimpleResult b;
        a.addHit(5).addHit(10);
        b.addHit(5).addHit(17).addHit(30);

        MatchData::UP md(MatchData::makeTestInstance(100, 10));
        auto or_b = std::make_unique<OrBlueprint>();
        or_b->addChild(std::make_unique<SimpleBlueprint>(a));
        or_b->addChild(std::make_unique<SimpleBlueprint>(b));
        or_b->basic_plan(true, 1000);
        or_b->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP or_ab = or_b->createSearch(*md);

        SimpleResult res;
        res.search(*or_ab, 1000);
        SimpleResult expect;
        expect.addHit(5).addHit(10).addHit(17).addHit(30);
        EXPECT_EQ(res, expect);

        SearchIterator::UP filter_ab = or_b->createFilterSearch(upper_bound);
        SimpleResult filter_res;
        filter_res.search(*filter_ab, 1000);
        EXPECT_EQ(res, expect);
        std::string dump = filter_ab->asString();
        expect_match(dump, "upper");
        expect_match(dump, "OrLikeSearch.false.*NoUnpack.*SimpleSearch.*upper.*SimpleSearch.*upper");
        or_b->basic_plan(false, 1000);
        or_b->fetchPostings(ExecuteInfo::FULL);
        filter_ab = or_b->createFilterSearch(lower_bound);
        dump = filter_ab->asString();
        expect_match(dump, "lower");
        expect_match(dump, "OrLikeSearch.false.*NoUnpack.*SimpleSearch.*lower.*SimpleSearch.*lower");
    }
    {
        TermFieldMatchData tfmd;
        MultiSearch::Children ch;
        ch.emplace_back(new TrueSearch(tfmd));
        ch.emplace_back(new TrueSearch(tfmd));
        ch.emplace_back(new TrueSearch(tfmd));
        SearchIterator::UP orSearch(OrSearch::create(std::move(ch), true));
        testMultiSearch(*orSearch);
    }
}

class TestInsertRemoveSearch : public MultiSearch
{
public:
    explicit TestInsertRemoveSearch(ChildrenIterators children) :
        MultiSearch(std::move(children)),
        _accumRemove(0),
        _accumInsert(0)
    { }
    ~TestInsertRemoveSearch() override;
    void onRemove(size_t index) override { _accumRemove += index; }
    void onInsert(size_t index) override { _accumInsert += index; }
    size_t _accumRemove;
    size_t _accumInsert;
private:
    void doSeek(uint32_t docid) override { (void) docid; }
};

TestInsertRemoveSearch::~TestInsertRemoveSearch() = default;

struct MultiSearchRemoveTest {
    static SearchIterator::UP remove(MultiSearch &ms, size_t idx) { return ms.remove(idx); }
};

TEST(QueryEvalTest, test_multi_search)
{
    std::vector<SearchIterator *> orig;
    orig.emplace_back(new EmptySearch());
    orig.emplace_back(new EmptySearch());
    orig.emplace_back(new EmptySearch());
    TestInsertRemoveSearch ms({orig[0], orig[1], orig[2]});
    EXPECT_EQ(3u, ms.getChildren().size());
    EXPECT_EQ(orig[0], ms.getChildren()[0].get());
    EXPECT_EQ(orig[1], ms.getChildren()[1].get());
    EXPECT_EQ(orig[2], ms.getChildren()[2].get());
    EXPECT_EQ(0u, ms._accumInsert);
    EXPECT_EQ(0u, ms._accumRemove);

    EXPECT_EQ(orig[1], MultiSearchRemoveTest::remove(ms, 1).get());
    EXPECT_EQ(2u, ms.getChildren().size());
    EXPECT_EQ(orig[0], ms.getChildren()[0].get());
    EXPECT_EQ(orig[2], ms.getChildren()[1].get());
    EXPECT_EQ(0u, ms._accumInsert);
    EXPECT_EQ(1u, ms._accumRemove);

    orig.emplace_back(new EmptySearch());
    ms.insert(1, SearchIterator::UP(orig.back()));
    EXPECT_EQ(3u, ms.getChildren().size());
    EXPECT_EQ(orig[0], ms.getChildren()[0].get());
    EXPECT_EQ(orig[3], ms.getChildren()[1].get());
    EXPECT_EQ(orig[2], ms.getChildren()[2].get());
    EXPECT_EQ(1u, ms._accumInsert);
    EXPECT_EQ(1u, ms._accumRemove);
}

class DummySingleValueBitNumericAttributeBlueprint : public SimpleLeafBlueprint
{
public:
    explicit DummySingleValueBitNumericAttributeBlueprint(const SimpleResult & result) :
        SimpleLeafBlueprint(),
        _a("a", search::GrowStrategy(), false),
        _sc(),
        _tfmd()
    {
        for (size_t i(0); i < result.getHitCount(); i++) {
            size_t docId(result.getHit(i));
            uint32_t curDoc(0);
            for (_a.addDoc(curDoc); curDoc < docId; _a.addDoc(curDoc));
            _a.update(docId, 1);
        }
        _a.commit();
        _sc = _a.getSearch(std::make_unique<search::QueryTermSimple>("1", search::QueryTermSimple::Type::WORD),
                           SearchContextParams().useBitVector(true));
    }
    ~DummySingleValueBitNumericAttributeBlueprint() override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override {
        auto est = _sc->calc_hit_estimate();
        return est.is_unknown()
            ? default_flow_stats(0)
            : default_flow_stats(docid_limit, est.est_hits(), 0);
    }
    SearchIterator::UP
    createLeafSearch(const TermFieldMatchDataArray &tfmda) const override
    {
        (void) tfmda;
        return _sc->createIterator(&_tfmd, strict());
    }
    SearchIteratorUP createFilterSearchImpl(FilterConstraint constraint) const override {
        return create_default_filter(constraint);
    }
private:
    search::SingleBoolAttribute     _a;
    std::unique_ptr<search::attribute::SearchContext> _sc;
    mutable TermFieldMatchData _tfmd;
};

DummySingleValueBitNumericAttributeBlueprint::~DummySingleValueBitNumericAttributeBlueprint() = default;

TEST(QueryEvalTest, test_andnot)
{
    {
        SimpleResult a;
        SimpleResult b;
        a.addHit(5).addHit(10);
        b.addHit(5).addHit(17).addHit(30);

        MatchData::UP md(MatchData::makeTestInstance(100, 10));
        auto andnot_b = std::make_unique<AndNotBlueprint>();
        andnot_b->addChild(std::make_unique<SimpleBlueprint>(a));
        andnot_b->addChild(std::make_unique<SimpleBlueprint>(b));
        andnot_b->basic_plan(true, 1000);
        andnot_b->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP andnot_ab = andnot_b->createSearch(*md);

        SimpleResult res;
        res.search(*andnot_ab, 1000);
        SimpleResult expect;
        expect.addHit(10);
        EXPECT_EQ(res, expect);

        SearchIterator::UP filter_ab = andnot_b->createFilterSearch(upper_bound);
        SimpleResult filter_res;
        filter_res.search(*filter_ab, 1000);
        EXPECT_EQ(res, expect);
        std::string dump = filter_ab->asString();
        expect_match(dump, "upper");
        expect_match(dump, "AndNotSearch.*SimpleSearch.*<strict,upper>.*SimpleSearch.*<nostrict,lower>");
        andnot_b->basic_plan(false, 1000);
        andnot_b->fetchPostings(ExecuteInfo::FULL);
        filter_ab = andnot_b->createFilterSearch(lower_bound);
        dump = filter_ab->asString();
        expect_match(dump, "lower");
        expect_match(dump, "AndNotSearch.*SimpleSearch.*<nostrict,lower>.*SimpleSearch.*<nostrict,upper>");
    }
    {
        SimpleResult a;
        SimpleResult b;
        a.addHit(1).addHit(5).addHit(10);
        b.addHit(5).addHit(17).addHit(30);

        MatchData::UP md(MatchData::makeTestInstance(100, 10));
        auto andnot_b = std::make_unique<AndNotBlueprint>();
        andnot_b->addChild(std::make_unique<SimpleBlueprint>(a));
        andnot_b->addChild(std::make_unique<DummySingleValueBitNumericAttributeBlueprint>(b));
        andnot_b->basic_plan(true, 1000);
        andnot_b->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP andnot_ab = andnot_b->createSearch(*md);

        SimpleResult res;
        res.search(*andnot_ab, 1000);
        SimpleResult expect;
        expect.addHit(1).addHit(10);

        EXPECT_EQ(res, expect);
    }
    {
        SimpleResult a;
        SimpleResult b;
        SimpleResult c;
        a.addHit(1).addHit(5).addHit(10);
        b.addHit(5).addHit(17).addHit(30);
        c.addHit(1).addHit(5).addHit(10).addHit(17).addHit(30);

        MatchData::UP md(MatchData::makeTestInstance(100, 10));
        auto andnot_b = std::make_unique<AndNotBlueprint>();
        andnot_b->addChild(std::make_unique<SimpleBlueprint>(a));
        andnot_b->addChild(std::make_unique<SimpleBlueprint>(b));

        auto and_b = std::make_unique<AndBlueprint>();
        and_b->addChild(std::make_unique<SimpleBlueprint>(c));
        and_b->addChild(std::move(andnot_b));
        and_b->basic_plan(true, 1000);
        and_b->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP and_cab = and_b->createSearch(*md);

        SimpleResult res;
        res.search(*and_cab, 1000);
        SimpleResult expect;
        expect.addHit(1).addHit(10);

        EXPECT_EQ(res, expect);
    }
    {
    }
}

TEST(QueryEvalTest, test_rank)
{
    {
        SimpleResult a;
        SimpleResult b;
        a.addHit(5).addHit(10).addHit(16).addHit(30);
        b.addHit(3).addHit(5).addHit(17).addHit(30).addHit(52);

        MatchData::UP md(MatchData::makeTestInstance(100, 10));
        auto rank_b = std::make_unique<RankBlueprint>();
        rank_b->addChild(std::make_unique<SimpleBlueprint>(a));
        rank_b->addChild(std::make_unique<SimpleBlueprint>(b));
        rank_b->basic_plan(true, 1000);
        rank_b->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP rank_ab = rank_b->createSearch(*md);

        SimpleResult res;
        res.search(*rank_ab, 1000);
        SimpleResult expect;
        expect.addHit(5).addHit(10).addHit(16).addHit(30);

        EXPECT_EQ(res, expect);
    }
}

std::string
getExpectedSlime() {
    return
"{"
"    '[type]': 'search::queryeval::AndSearchStrict<search::queryeval::(anonymous namespace)::FullUnpack>',"
"    children: {"
"        '[type]': 'std::vector',"
"        [0]: {"
"            '[type]': 'search::queryeval::(anonymous namespace)::AndNotSearchStrict',"
"            children: {"
"                '[type]': 'std::vector',"
"                [0]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: '+'"
"                },"
"                [1]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: '-'"
"                }"
"            }"
"        },"
"        [1]: {"
"            '[type]': 'search::queryeval::AndSearchStrict<search::queryeval::(anonymous namespace)::FullUnpack>',"
"            children: {"
"                '[type]': 'std::vector',"
"                [0]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: 'and_a'"
"                },"
"                [1]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: 'and_b'"
"                }"
"            }"
"        },"
"        [2]: {"
"            '[type]': 'search::queryeval::BooleanMatchIteratorWrapper',"
"            'search': {"
"                '[type]': 'search::queryeval::SimpleSearch',"
"                tag: 'wrapped'"
"            }"
"        },"
"        [3]: {"
"            '[type]': 'search::queryeval::NearSearch',"
"            children: {"
"                '[type]': 'std::vector',"
"                [0]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: 'near_a'"
"                },"
"                [1]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: 'near_b'"
"                }"
"            },"
"            data_size: 0,"
"            window: 5,"
"            num_negative_terms: 0,"
"            exclusion_distance: 0,"
"            strict: true"
"        },"
"        [4]: {"
"            '[type]': 'search::queryeval::ONearSearch',"
"            children: {"
"                '[type]': 'std::vector',"
"                [0]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: 'onear_a'"
"                },"
"                [1]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: 'onear_b'"
"                }"
"            },"
"            data_size: 0,"
"            window: 10,"
"            num_negative_terms: 0,"
"            exclusion_distance: 0,"
"            strict: true"
"        },"
"        [5]: {"
"            '[type]': 'search::queryeval::OrLikeSearch<false, search::queryeval::(anonymous namespace)::FullUnpack>',"
"            children: {"
"                '[type]': 'std::vector',"
"                [0]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: 'or_a'"
"                },"
"                [1]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: 'or_b'"
"                }"
"            },"
"            strict: false"
"        },"
"        [6]: {"
"            '[type]': 'search::queryeval::RankSearch',"
"            children: {"
"                '[type]': 'std::vector',"
"                [0]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: 'rank_a'"
"                },"
"                [1]: {"
"                    '[type]': 'search::queryeval::SimpleSearch',"
"                    tag: 'rank_b'"
"                }"
"            }"
"        },"
"        [7]: {"
"            '[type]': 'search::queryeval::SourceBlenderSearchStrict',"
"            children: {"
"                '[type]': 'std::vector',"
"                [0]: 2,"
"                [1]: 4"
"            },"
"            'Source 2': {"
"                '[type]': 'search::queryeval::SimpleSearch',"
"                tag: 'blend_a'"
"            },"
"            'Source 4': {"
"                '[type]': 'search::queryeval::SimpleSearch',"
"                tag: 'blend_b'"
"            }"
"        }"
"    }"
"}";
}

TEST(QueryEvalTest, test_dump)
{
    using SBChild = SourceBlenderSearch::Child;

    SearchIterator::UP search = AndSearch::create( {
                AndNotSearch::create(search2("+", "-"), true),
                AndSearch::create(search2("and_a", "and_b"), true),
                new BooleanMatchIteratorWrapper(SearchIterator::UP(simple("wrapped")), TermFieldMatchDataArray()),
                new NearSearch(search2("near_a", "near_b"), TermFieldMatchDataArray(), 5, mock_element_gap_inspector, true),
                new ONearSearch(search2("onear_a", "onear_b"), TermFieldMatchDataArray(), 10, mock_element_gap_inspector, true),
                OrSearch::create(search2("or_a", "or_b"), false),
                RankSearch::create(search2("rank_a", "rank_b"),false),
                SourceBlenderSearch::create(selector(), Collect<SBChild, SourceBlenderSearch::Children>()
                                            .add(SBChild(simple("blend_a"), 2))
                                            .add(SBChild(simple("blend_b"), 4)),
                                            true) }, true);
    std::string sas = search->asString();
    EXPECT_TRUE(sas.size() > 50);
    vespalib::Slime slime;
    search->asSlime(vespalib::slime::SlimeInserter(slime));
    auto s = slime.toString();
    vespalib::Slime expectedSlime;
    vespalib::slime::JsonFormat::decode(getExpectedSlime(), expectedSlime);
    EXPECT_EQ(expectedSlime, slime);
    // fprintf(stderr, "%s", search->asString().c_str());
}

TEST(QueryEvalTest, test_field_spec) {
    EXPECT_EQ(8u, sizeof(FieldSpecBase));
    EXPECT_EQ(16u + sizeof(std::string), sizeof(FieldSpec));
}


const size_t unpack_child_cnt = 500;
const size_t max_unpack_size = 31;
const size_t max_unpack_index = 255;

std::vector<size_t> vectorize(const UnpackInfo &unpack) {
    std::vector<size_t> list;
    unpack.each([&](size_t i){list.push_back(i);}, unpack_child_cnt);
    return list;
}

std::vector<size_t> fill_vector(size_t begin, size_t end) {
    std::vector<size_t> list;
    for (size_t i = begin; i < end; ++i) {
        list.push_back(i);
    }
    return list;
}

void verify_unpack(const UnpackInfo &unpack, const std::vector<size_t> &expect) {
    std::vector<size_t> actual = vectorize(unpack);
    EXPECT_EQ(unpack.empty(), expect.empty());
    EXPECT_EQ(unpack.unpackAll(), (expect.size() == unpack_child_cnt));
    EXPECT_EQ(expect, actual);
    size_t child_idx = 0;
    for (size_t next_unpack: expect) {
        while (child_idx < next_unpack) {
            EXPECT_FALSE(unpack.needUnpack(child_idx++));
        }
        EXPECT_TRUE(unpack.needUnpack(child_idx++));
    }
}

TEST(QueryEvalTest, require_that_unpack_info_has_expected_memory_footprint)
{
    EXPECT_EQ(32u, sizeof(UnpackInfo));
}

TEST(QueryEvalTest, require_that_unpack_info_starts_out_empty)
{
    verify_unpack(UnpackInfo(), {});
}

TEST(QueryEvalTest, require_that_unpack_info_force_all_unpacks_all_children)
{
    verify_unpack(UnpackInfo().forceAll(), fill_vector(0, unpack_child_cnt));
}

TEST(QueryEvalTest, require_that_adding_a_large_index_to_unpack_info_forces_unpack_all)
{
    UnpackInfo unpack;
    unpack.add(0);
    unpack.add(max_unpack_index);
    verify_unpack(unpack, {0, max_unpack_index});
    unpack.add(max_unpack_index + 1);
    verify_unpack(unpack, fill_vector(0, unpack_child_cnt));
}

TEST(QueryEvalTest, require_that_adding_too_many_children_to_unpack_info_forces_unpack_all)
{
    UnpackInfo unpack;
    std::vector<size_t> expect;
    for (size_t i = 0; i < max_unpack_size; ++i) {
        unpack.add(i);
        expect.push_back(i);
    }
    verify_unpack(unpack, expect);
    unpack.add(100);
    verify_unpack(unpack, fill_vector(0, unpack_child_cnt));
}

TEST(QueryEvalTest, require_that_adding_normal_unpack_info_indexes_works)
{
    UnpackInfo unpack;
    unpack.add(3).add(5).add(7).add(14).add(50);
    verify_unpack(unpack, {3,5,7,14,50});
}

TEST(QueryEvalTest, require_that_adding_unpack_info_indexes_out_of_order_works)
{
    UnpackInfo unpack;
    unpack.add(5).add(3).add(7).add(50).add(14);
    verify_unpack(unpack, {3,5,7,14,50});
}

TEST(QueryEvalTest, require_that_basic_insert_remove_of_unpack_info_works)
{
    UnpackInfo unpack;
    unpack.insert(1).insert(3);
    verify_unpack(unpack, {1, 3});
    unpack.insert(0);
    verify_unpack(unpack, {0, 2, 4});
    unpack.insert(3);
    verify_unpack(unpack, {0, 2, 3, 5});
    unpack.remove(1);
    verify_unpack(unpack, {0, 1, 2, 4});
    unpack.remove(1);
    verify_unpack(unpack, {0, 1, 3});
    unpack.remove(1);
    verify_unpack(unpack, {0, 2});
    unpack.remove(2);
    verify_unpack(unpack, {0});
    unpack.remove(0);
    verify_unpack(unpack, {});
}

TEST(QueryEvalTest, require_that_inserting_too_many_indexes_into_unpack_info_forces_unpack_all)
{
    for (bool unpack_inserted: {true, false}) {
        UnpackInfo unpack;
        for (size_t i = 0; i < max_unpack_size; ++i) {
            unpack.add(i);
        }
        EXPECT_FALSE(unpack.unpackAll());
        unpack.insert(0, unpack_inserted);
        if (unpack_inserted) {
            verify_unpack(unpack, fill_vector(0, unpack_child_cnt));
        } else {
            verify_unpack(unpack, fill_vector(1, max_unpack_size + 1));
        }
    }
}

TEST(QueryEvalTest, require_that_implicitly_overflowing_indexes_during_insert_in_unpack_info_forces_unpack_all)
{
    for (bool unpack_inserted: {true, false}) {
        UnpackInfo unpack;
        unpack.insert(max_unpack_index);
        EXPECT_FALSE(unpack.unpackAll());
        unpack.insert(5, unpack_inserted);
        verify_unpack(unpack, fill_vector(0, unpack_child_cnt));
    }
}

TEST(QueryEvalTest, require_that_inserting_a_too_high_index_into_unpack_info_forces_unpack_all)
{
    for (bool unpack_inserted: {true, false}) {
        UnpackInfo unpack;
        for (size_t i = 0; i < 10; ++i) {
            unpack.add(i);
        }
        EXPECT_FALSE(unpack.unpackAll());
        unpack.insert(max_unpack_index + 1, unpack_inserted);
        if (unpack_inserted) {
            verify_unpack(unpack, fill_vector(0, unpack_child_cnt));
        } else {
            verify_unpack(unpack, fill_vector(0, 10));
        }
    }
}

TEST(QueryEvalTest, require_that_we_can_insert_indexes_into_unpack_info_that_we_do_not_unpack) {
    UnpackInfo unpack;
    unpack.add(10).add(20).add(30);
    verify_unpack(unpack, {10, 20, 30});    
    unpack.insert(5, false).insert(15, false).insert(25, false).insert(35, false);
    verify_unpack(unpack, {11, 22, 33});    
}

TEST(QueryEvalTest, test_true_search)
{
    EXPECT_EQ(24u, sizeof(EmptySearch));
    EXPECT_EQ(32u, sizeof(TrueSearch));

    TermFieldMatchData tfmd;
    TrueSearch t(tfmd);
    EXPECT_EQ(0u, t.getDocId());
    EXPECT_EQ(0u, t.getEndId());
    t.initRange(7, 10);
    EXPECT_EQ(6u, t.getDocId());
    EXPECT_EQ(10u, t.getEndId());
    EXPECT_TRUE(t.seek(9));
    EXPECT_EQ(9u, t.getDocId());
    EXPECT_FALSE(t.isAtEnd());
    EXPECT_TRUE(t.seek(10));
    EXPECT_EQ(10u, t.getDocId());
    EXPECT_TRUE(t.isAtEnd());
    t.initRange(4, 14);
    EXPECT_EQ(3u, t.getDocId());
    EXPECT_EQ(14u, t.getEndId());
    EXPECT_FALSE(t.isAtEnd());
}

TEST(QueryEvalTest, test_init_range_verifier)
{
    InitRangeVerifier ir;
    EXPECT_EQ(207u, ir.getDocIdLimit());
    EXPECT_EQ(41u, ir.getExpectedDocIds().size());
    auto inverted = InitRangeVerifier::invert(ir.getExpectedDocIds(), 300);
    size_t numInverted = 300 - 41 - 1;
    EXPECT_EQ(numInverted, inverted.size());
    EXPECT_EQ(2u, inverted[0]);
    EXPECT_EQ(299u, inverted[numInverted - 1]);
    ir.verify(*ir.createIterator(ir.getExpectedDocIds(), false));
    ir.verify(*ir.createIterator(ir.getExpectedDocIds(), true));
}

TEST(QueryEvalTest, test_multisearch_and_andsearchstrict_iterators_adheres_to_init_range)
{
    InitRangeVerifier ir;
    {
        SCOPED_TRACE("non-strict");
        ir.verify( AndSearch::create({ ir.createIterator(ir.getExpectedDocIds(), false),
                                       ir.createFullIterator() }, false));
    }
    {
        SCOPED_TRACE("strict");
        ir.verify( AndSearch::create({ ir.createIterator(ir.getExpectedDocIds(), true),
                                       ir.createFullIterator() }, true));
    }
}

TEST(QueryEvalTest, test_andnotsearchstrict_iterators_adheres_to_init_range) {
    InitRangeVerifier ir;

    {
        SCOPED_TRACE("non-strict");
        ir.verify( AndNotSearch::create({ir.createIterator(ir.getExpectedDocIds(), false),
                                         ir.createEmptyIterator() }, false));
    }
    {
        SCOPED_TRACE("strict");
        ir.verify( AndNotSearch::create({ir.createIterator(ir.getExpectedDocIds(), true),
                                         ir.createEmptyIterator() }, true));
    }

    auto inverted = InitRangeVerifier::invert(ir.getExpectedDocIds(), ir.getDocIdLimit());
    {
        SCOPED_TRACE("non-strict full");
        ir.verify( AndNotSearch::create({ir.createFullIterator(),
                                         ir.createIterator(inverted, false) }, false));
    }
    {
        SCOPED_TRACE("strict full");
        ir.verify( AndNotSearch::create({ir.createFullIterator(),
                                         ir.createIterator(inverted, false) }, true));
    }
}

TEST(QueryEvalTest, test_stats)
{
    auto stats = QueryEvalStats::create();
    EXPECT_EQ(0u, stats->exact_nns_distances_computed());
    EXPECT_EQ(0u, stats->approximate_nns_distances_computed());
    EXPECT_EQ(0u, stats->approximate_nns_nodes_visited());

    stats->add_to_exact_nns_distances_computed(42u);
    EXPECT_EQ(42u, stats->exact_nns_distances_computed());
    stats->add_to_exact_nns_distances_computed(42u);
    EXPECT_EQ(84u, stats->exact_nns_distances_computed());

    stats->add_to_approximate_nns_distances_computed(1u);
    EXPECT_EQ(1u, stats->approximate_nns_distances_computed());
    stats->add_to_approximate_nns_distances_computed(1u);
    EXPECT_EQ(2u, stats->approximate_nns_distances_computed());

    stats->add_to_approximate_nns_nodes_visited(2u);
    EXPECT_EQ(2u, stats->approximate_nns_nodes_visited());
    stats->add_to_approximate_nns_nodes_visited(2u);
    EXPECT_EQ(4u, stats->approximate_nns_nodes_visited());
}


GTEST_MAIN_RUN_ALL_TESTS()

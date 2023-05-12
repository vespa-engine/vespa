// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchcore/proton/matching/match_phase_limiter.h>
#include <vespa/searchcore/proton/matching/rangequerylocator.h>
#include <vespa/searchlib/queryeval/termasstring.h>
#include <vespa/searchlib/queryeval/andsearchstrict.h>
#include <vespa/searchlib/queryeval/searchable.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/searchlib/attribute/attribute_blueprint_factory.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/engine/trace.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace proton::matching;
using namespace search::engine;
using search::queryeval::SearchIterator;
using search::queryeval::Searchable;
using search::queryeval::Blueprint;
using search::queryeval::SimpleLeafBlueprint;
using search::queryeval::IRequestContext;
using search::queryeval::FieldSpec;
using search::queryeval::FieldSpecBaseList;
using search::queryeval::AndSearchStrict;
using search::queryeval::termAsString;
using search::queryeval::FakeRequestContext;
using search::fef::TermFieldMatchDataArray;
using search::attribute::BasicType;
using search::attribute::Config;
using vespalib::make_string_short::fmt;

//-----------------------------------------------------------------------------

SearchIterator::UP prepare(SearchIterator * search)
{
    search->initFullRange();
    return SearchIterator::UP(search);
}

struct MockSearch : SearchIterator {
    FieldSpec spec;
    vespalib::string term;
    vespalib::Trinary _strict;
    TermFieldMatchDataArray tfmda;
    bool postings_fetched;
    uint32_t last_seek = beginId();
    uint32_t last_unpack = beginId();
    explicit MockSearch(const vespalib::string &term_in)
        : spec("", 0, 0), term(term_in), _strict(vespalib::Trinary::True), tfmda(), postings_fetched(false) {}
    MockSearch(const FieldSpec &spec_in, const vespalib::string &term_in, bool strict_in,
               const TermFieldMatchDataArray &tfmda_in, bool postings_fetched_in)
        : spec(spec_in), term(term_in),
          _strict(strict_in ? vespalib::Trinary::True : vespalib::Trinary::False),
          tfmda(tfmda_in),
          postings_fetched(postings_fetched_in) {}
    void doSeek(uint32_t docid) override { last_seek = docid; setDocId(docid); }
    void doUnpack(uint32_t docid) override { last_unpack = docid; }
    vespalib::Trinary is_strict() const override { return _strict; } 
    bool strict() const { return (is_strict() == vespalib::Trinary::True); }
};

struct MockBlueprint : SimpleLeafBlueprint {
    FieldSpec spec;
    vespalib::string term;
    bool postings_fetched = false;
    search::queryeval::ExecuteInfo postings_strict = search::queryeval::ExecuteInfo::FALSE;
    MockBlueprint(const FieldSpec &spec_in, const vespalib::string &term_in)
        : SimpleLeafBlueprint(spec_in), spec(spec_in), term(term_in)
    {
        setEstimate(HitEstimate(756, false));
    }    
    SearchIterator::UP createLeafSearch(const TermFieldMatchDataArray &tfmda, bool strict) const override
    {
        if (postings_fetched) {
            EXPECT_EQUAL(postings_strict.isStrict(), strict);
        }
        return std::make_unique<MockSearch>(spec, term, strict, tfmda, postings_fetched);
    }
    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override {
        return create_default_filter(strict, constraint);
    }
    void fetchPostings(const search::queryeval::ExecuteInfo &execInfo) override {
        postings_strict = execInfo;
        postings_fetched = true;
    }
};

struct MockSearchable : Searchable {
    size_t create_cnt = 0;
    Blueprint::UP createBlueprint(const IRequestContext & requestContext,
                                  const FieldSpec &field,
                                  const search::query::Node &term) override
    {
        (void) requestContext;
        ++create_cnt;
        return std::make_unique<MockBlueprint>(field, termAsString(term));
    }
};

class MockRangeLocator : public RangeQueryLocator {
public:
    RangeLimitMetaInfo locate() const override {
        return {};
    }
};

//-----------------------------------------------------------------------------

TEST("require that match phase limit calculator gives expert values") {
    MatchPhaseLimitCalculator calc(5000, 1, 0.2);
    EXPECT_EQUAL(1000u, calc.sample_hits_per_thread(1));
    EXPECT_EQUAL(100u, calc.sample_hits_per_thread(10));
    EXPECT_EQUAL(10000u, calc.wanted_num_docs(0.5));
    EXPECT_EQUAL(50000u, calc.wanted_num_docs(0.1));
}

TEST("require that match phase limit calculator can estimate hits") {
    MatchPhaseLimitCalculator calc(0, 1, 0.2); // max hits not used
    EXPECT_EQUAL(0u, calc.estimated_hits(0.0, 0));
    EXPECT_EQUAL(0u, calc.estimated_hits(0.0, 1));
    EXPECT_EQUAL(0u, calc.estimated_hits(0.0, 1000));
    EXPECT_EQUAL(1u, calc.estimated_hits(1.0, 1));
    EXPECT_EQUAL(10u, calc.estimated_hits(1.0, 10));
    EXPECT_EQUAL(5u, calc.estimated_hits(0.5, 10));
    EXPECT_EQUAL(500u, calc.estimated_hits(0.5, 1000));
}

TEST("require that match phase limit calculator has lower bound on global sample hits") {
    MatchPhaseLimitCalculator calc(100, 1, 0.2);
    EXPECT_EQUAL(128u, calc.sample_hits_per_thread(1));
    EXPECT_EQUAL(4u, calc.sample_hits_per_thread(32));
}

TEST("require that match phase limit calculator has lower bound on thread sample hits") {
    MatchPhaseLimitCalculator calc(5000, 1, 0.2);
    EXPECT_EQUAL(1u, calc.sample_hits_per_thread(10000));
}

TEST("require that match phase limit calculator has lower bound on wanted hits") {
    MatchPhaseLimitCalculator calc(100, 1, 0.2);
    EXPECT_EQUAL(128u, calc.wanted_num_docs(1.0));
}

TEST("require that match phase limit calculator has upper bound on wanted hits") {
    MatchPhaseLimitCalculator calc(100000000, 1, 0.2);
    EXPECT_EQUAL(0x7fffFFFFu, calc.wanted_num_docs(0.0000001));
}

TEST("require that match phase limit calculator gives sane values with no hits") {
    MatchPhaseLimitCalculator calc(100, 1, 0.2);
    EXPECT_EQUAL(128u, calc.wanted_num_docs(1.0));
    EXPECT_EQUAL(0x7fffFFFFu, calc.wanted_num_docs(0.000000001));
    EXPECT_EQUAL(0x7fffFFFFu, calc.wanted_num_docs(0.000000001));
}

TEST("verify numbers used in matching test") {
    MatchPhaseLimitCalculator calc(150, 1, 0.2);
    EXPECT_EQUAL(1u, calc.sample_hits_per_thread(75));
    EXPECT_EQUAL(176u, calc.wanted_num_docs(74.0 / 87.0));
}

TEST("require that max group size is calculated correctly") {
    for (size_t min_groups: std::vector<size_t>({0, 1, 2, 3, 4, 10, 500})) {
        for (size_t wanted_hits: std::vector<size_t>({0, 3, 321, 921})) {
            MatchPhaseLimitCalculator calc(100, min_groups, 0.2);
            if (min_groups == 0) {
                EXPECT_EQUAL(wanted_hits, calc.max_group_size(wanted_hits));
            } else {
                EXPECT_EQUAL((wanted_hits / min_groups), calc.max_group_size(wanted_hits));
            }
        }
    }
}

TEST("require that the attribute limiter works correctly") {
    FakeRequestContext requestContext;
    MockRangeLocator rangeLocator;
    for (int i = 0; i <= 7; ++i) {
        bool descending = (i & 1) != 0;
        bool strict     = (i & 2) != 0;
        bool diverse    = (i & 4) != 0;
        MockSearchable searchable;
        AttributeLimiter limiter(rangeLocator, searchable, requestContext, "limiter_attribute", descending,
                                 "category", 10.0, AttributeLimiter::LOOSE);
        EXPECT_EQUAL(0u, searchable.create_cnt);
        EXPECT_FALSE(limiter.was_used());
        SearchIterator::UP s1 = limiter.create_search(42, diverse ? 3 : 42, strict);
        EXPECT_TRUE(limiter.was_used());
        EXPECT_EQUAL(1u, searchable.create_cnt);
        SearchIterator::UP s2 = limiter.create_search(42, diverse ? 3 : 42, strict);
        EXPECT_EQUAL(1u, searchable.create_cnt);
        auto *ms = dynamic_cast<MockSearch*>(s1.get());
        ASSERT_TRUE(ms != nullptr);
        EXPECT_EQUAL("limiter_attribute", ms->spec.getName());
        EXPECT_EQUAL(0u, ms->spec.getFieldId());
        EXPECT_EQUAL(0u, ms->spec.getHandle());
        EXPECT_EQUAL(strict, ms->strict());
        EXPECT_TRUE(ms->postings_fetched);
        if (descending) {
            if (diverse) {
                EXPECT_EQUAL("[;;-42;category;3;140;loose]", ms->term);
            } else {
                EXPECT_EQUAL("[;;-42]", ms->term);
            }
        } else {
            if (diverse) {
                EXPECT_EQUAL("[;;42;category;3;140;loose]", ms->term);
            } else {
                EXPECT_EQUAL("[;;42]", ms->term);
            }
        }
        ASSERT_EQUAL(1u, ms->tfmda.size());
        EXPECT_EQUAL(0u, ms->tfmda[0]->getFieldId());
    }
}

TEST("require that no limiter has no behavior") {
    NoMatchPhaseLimiter no_limiter;
    MaybeMatchPhaseLimiter &limiter = no_limiter;
    EXPECT_FALSE(limiter.is_enabled());
    EXPECT_EQUAL(0u, limiter.sample_hits_per_thread(1));
    SearchIterator::UP search = limiter.maybe_limit(prepare(new MockSearch("search")), 1.0, 100000000, nullptr);
    limiter.updateDocIdSpaceEstimate(1000, 9000);
    EXPECT_EQUAL(std::numeric_limits<size_t>::max(), limiter.getDocIdSpaceEstimate());
    auto *ms = dynamic_cast<MockSearch*>(search.get());
    ASSERT_TRUE(ms != nullptr);
    EXPECT_EQUAL("search", ms->term);
    EXPECT_FALSE(limiter.was_limited());
}

TEST("require that the match phase limiter may chose not to limit the query") {
    FakeRequestContext requestContext;
    MockSearchable searchable;
    MockRangeLocator rangeLocator;
    MatchPhaseLimiter yes_limiter(10000, rangeLocator, searchable, requestContext,
                                  DegradationParams("limiter_attribute", 1000, true, 1.0, 0.2, 1.0),
                                  DiversityParams("", 1, 10.0, AttributeLimiter::LOOSE));
    MaybeMatchPhaseLimiter &limiter = yes_limiter;
    EXPECT_TRUE(limiter.is_enabled());
    EXPECT_EQUAL(20u, limiter.sample_hits_per_thread(10));
    SearchIterator::UP search = limiter.maybe_limit(prepare(new MockSearch("search")), 0.005, 100000, nullptr);
    limiter.updateDocIdSpaceEstimate(1000, 9000);
    EXPECT_EQUAL(10000u, limiter.getDocIdSpaceEstimate());
    auto *ms = dynamic_cast<MockSearch*>(search.get());
    ASSERT_TRUE(ms != nullptr);
    EXPECT_EQUAL("search", ms->term);
    EXPECT_FALSE(limiter.was_limited());
}

struct MaxFilterCoverageLimiterFixture {

    FakeRequestContext requestContext;
    MockSearchable searchable;
    MockRangeLocator rangeLocator;

    MatchPhaseLimiter::UP getMaxFilterCoverageLimiter() {
        auto yes_limiter = std::make_unique<MatchPhaseLimiter>(10000, rangeLocator, searchable, requestContext,
                                                               DegradationParams("limiter_attribute", 10000, true, 0.05, 1.0, 1.0),
                                                               DiversityParams("", 1, 10.0, AttributeLimiter::LOOSE));
        MaybeMatchPhaseLimiter &limiter = *yes_limiter;
        EXPECT_TRUE(limiter.is_enabled());
        EXPECT_EQUAL(1000u, limiter.sample_hits_per_thread(10));
        return yes_limiter;
    }
};

TEST_F("require that the match phase limiter may chose not to limit the query when considering max-filter-coverage", MaxFilterCoverageLimiterFixture) {
    MatchPhaseLimiter::UP limiterUP = f.getMaxFilterCoverageLimiter();
    MaybeMatchPhaseLimiter & limiter = *limiterUP;
    SearchIterator::UP search = limiter.maybe_limit(prepare(new MockSearch("search")), 0.10, 1900000, nullptr);
    limiter.updateDocIdSpaceEstimate(1000, 1899000);
    EXPECT_EQUAL(1900000u, limiter.getDocIdSpaceEstimate());
    auto *ms = dynamic_cast<MockSearch *>(search.get());
    ASSERT_TRUE(ms != nullptr);
    EXPECT_EQUAL("search", ms->term);
    EXPECT_FALSE(limiter.was_limited());
}

TEST_F("require that the match phase limiter may chose to limit the query even when considering max-filter-coverage", MaxFilterCoverageLimiterFixture) {
    MatchPhaseLimiter::UP limiterUP = f.getMaxFilterCoverageLimiter();
    MaybeMatchPhaseLimiter & limiter = *limiterUP;
    SearchIterator::UP search = limiter.maybe_limit(prepare(new MockSearch("search")), 0.10, 2100000, nullptr);
    limiter.updateDocIdSpaceEstimate(1000, 2099000);
    EXPECT_EQUAL(159684u, limiter.getDocIdSpaceEstimate());
    auto *strict_and = dynamic_cast<LimitedSearch*>(search.get());
    ASSERT_TRUE(strict_and != nullptr);
    const auto *ms1 = dynamic_cast<const MockSearch*>(&strict_and->getFirst());
    ASSERT_TRUE(ms1 != nullptr);
    const auto *ms2 = dynamic_cast<const MockSearch*>(&strict_and->getSecond());
    ASSERT_TRUE(ms2 != nullptr);
    EXPECT_EQUAL("[;;-100000]", ms1->term);
    EXPECT_EQUAL("search", ms2->term);
    EXPECT_TRUE(ms1->strict());
    EXPECT_TRUE(ms2->strict());
    EXPECT_TRUE(limiter.was_limited());
}

void verify(vespalib::stringref expected, const vespalib::Slime & slime) {
    vespalib::Slime expectedSlime;
    vespalib::slime::JsonFormat::decode(expected, expectedSlime);
    EXPECT_EQUAL(expectedSlime, slime);
}

TEST("require that the match phase limiter is able to pre-limit the query") {
    FakeRequestContext requestContext;
    MockSearchable searchable;
    MockRangeLocator rangeLocator;
    MatchPhaseLimiter yes_limiter(10000, rangeLocator, searchable, requestContext,
                                  DegradationParams("limiter_attribute", 500, true, 1.0, 0.2, 1.0),
                                  DiversityParams("", 1, 10.0, AttributeLimiter::LOOSE));
    MaybeMatchPhaseLimiter &limiter = yes_limiter;
    EXPECT_TRUE(limiter.is_enabled());
    EXPECT_EQUAL(12u, limiter.sample_hits_per_thread(10));
    RelativeTime clock(std::make_unique<CountingClock>(vespalib::count_ns(10000000s), 1700000L));
    Trace trace(clock, 7);
    trace.start(4, false);
    SearchIterator::UP search = limiter.maybe_limit(prepare(new MockSearch("search")), 0.1, 100000, trace.maybeCreateCursor(7, "limit"));
    limiter.updateDocIdSpaceEstimate(1000, 9000);
    EXPECT_EQUAL(1680u, limiter.getDocIdSpaceEstimate());
    auto *strict_and = dynamic_cast<LimitedSearch*>(search.get());
    ASSERT_TRUE(strict_and != nullptr);
    const auto *ms1 = dynamic_cast<const MockSearch*>(&strict_and->getFirst());
    ASSERT_TRUE(ms1 != nullptr);
    const auto *ms2 = dynamic_cast<const MockSearch*>(&strict_and->getSecond());
    ASSERT_TRUE(ms2 != nullptr);
    EXPECT_EQUAL("[;;-5000]", ms1->term);
    EXPECT_EQUAL("search", ms2->term);
    EXPECT_TRUE(ms1->strict());
    EXPECT_TRUE(ms2->strict());
    search->seek(100);
    EXPECT_EQUAL(100u, ms1->last_seek);
    EXPECT_EQUAL(100u, ms2->last_seek);
    search->unpack(100);
    EXPECT_EQUAL(0u, ms1->last_unpack); // will not unpack limiting term
    EXPECT_EQUAL(100u, ms2->last_unpack);
    EXPECT_TRUE(limiter.was_limited());
    trace.done();
    verify(
        "{"
        "    start_time: '1970-04-26 17:46:40.000 UTC',"
        "    traces: ["
        "        {"
        "            timestamp_ms: 1.7,"
        "            tag: 'limit',"
        "            hit_rate: 0.1,"
        "            num_docs: 100000,"
        "            max_filter_docs: 100000,"
        "            wanted_docs: 5000,"
        "            action: 'Will limit with prefix filter',"
        "            max_group_size: 5000,"
        "            current_docid: 0,"
        "            end_docid: 2147483647,"
        "            estimated_total_hits: 10000"
        "        }"
        "    ],"
        "    duration_ms: 3.4"
        "}", trace.getSlime());
}

TEST("require that the match phase limiter is able to post-limit the query") {
    MockSearchable searchable;
    FakeRequestContext requestContext;
    MockRangeLocator rangeLocator;
    MatchPhaseLimiter yes_limiter(10000, rangeLocator, searchable, requestContext,
                                  DegradationParams("limiter_attribute", 1500, true, 1.0, 0.2, 1.0),
                                  DiversityParams("", 1, 10.0, AttributeLimiter::LOOSE));
    MaybeMatchPhaseLimiter &limiter = yes_limiter;
    EXPECT_TRUE(limiter.is_enabled());
    EXPECT_EQUAL(30u, limiter.sample_hits_per_thread(10));
    SearchIterator::UP search = limiter.maybe_limit(prepare(new MockSearch("search")), 0.1, 100000, nullptr);
    limiter.updateDocIdSpaceEstimate(1000, 9000);
    EXPECT_EQUAL(1680u, limiter.getDocIdSpaceEstimate());
    auto *strict_and = dynamic_cast<LimitedSearch*>(search.get());
    ASSERT_TRUE(strict_and != nullptr);
    const auto *ms1 = dynamic_cast<const MockSearch*>(&strict_and->getFirst());
    ASSERT_TRUE(ms1 != nullptr);
    const auto *ms2 = dynamic_cast<const MockSearch*>(&strict_and->getSecond());
    ASSERT_TRUE(ms2 != nullptr);
    EXPECT_EQUAL("search", ms1->term);
    EXPECT_EQUAL("[;;-15000]", ms2->term);
    EXPECT_TRUE(ms1->strict());
    EXPECT_FALSE(ms2->strict());
    search->seek(100);
    EXPECT_EQUAL(100u, ms1->last_seek);
    EXPECT_EQUAL(100u, ms2->last_seek);
    search->unpack(100);
    EXPECT_EQUAL(100u, ms1->last_unpack);
    EXPECT_EQUAL(0u, ms2->last_unpack); // will not unpack limiting term
    EXPECT_TRUE(limiter.was_limited());
}

void verifyDiversity(AttributeLimiter::DiversityCutoffStrategy strategy)
{
    MockSearchable searchable;
    FakeRequestContext requestContext;
    MockRangeLocator rangeLocator;
    MatchPhaseLimiter yes_limiter(10000, rangeLocator, searchable, requestContext,
                                  DegradationParams("limiter_attribute", 500, true, 1.0, 0.2, 1.0),
                                  DiversityParams("category", 10, 13.1, strategy));
    MaybeMatchPhaseLimiter &limiter = yes_limiter;
    SearchIterator::UP search = limiter.maybe_limit(prepare(new MockSearch("search")), 0.1, 100000, nullptr);
    limiter.updateDocIdSpaceEstimate(1000, 9000);
    EXPECT_EQUAL(1680u, limiter.getDocIdSpaceEstimate());
    auto *strict_and = dynamic_cast<LimitedSearch*>(search.get());
    ASSERT_TRUE(strict_and != nullptr);
    const auto *ms1 = dynamic_cast<const MockSearch*>(&strict_and->getFirst());
    ASSERT_TRUE(ms1 != nullptr);
    if (strategy == AttributeLimiter::LOOSE) {
        EXPECT_EQUAL("[;;-5000;category;500;131;loose]", ms1->term);
    } else if (strategy == AttributeLimiter::STRICT) {
        EXPECT_EQUAL("[;;-5000;category;500;131;strict]", ms1->term);
    } else {
        ASSERT_TRUE(false);
    }
}

TEST("require that the match phase limiter can use loose diversity") {
    verifyDiversity(AttributeLimiter::LOOSE);
}

TEST("require that the match phase limiter can use strict diversity") {
    verifyDiversity(AttributeLimiter::STRICT);
}

struct RangeLimitFixture {
    RangeLimitFixture()
        : attrSearchable(),
          attrManager(),
          attributeContext(attrManager.createContext()),
          requestContext(attributeContext.get()),
          a1FieldSpec("a1", 1, 1),
          f1FieldSpec("f1", 2, 2)
    {
        attrManager.addAttribute(search::AttributeFactory::createAttribute(a1FieldSpec.getName(), Config(BasicType("int64"))));
        attrManager.addAttribute(search::AttributeFactory::createAttribute(f1FieldSpec.getName(), Config(BasicType("double"))));
    }
    search::AttributeBlueprintFactory attrSearchable;
    search::attribute::test::MockAttributeManager attrManager;
    search::attribute::IAttributeContext::UP attributeContext;
    FakeRequestContext requestContext;
    FieldSpec a1FieldSpec;
    FieldSpec f1FieldSpec;
};

void
verifyLocateRange(const vespalib::string & from, const vespalib::string & to,
                  const FieldSpec & fieldSpec, RangeLimitFixture & f)
{
    search::query::SimpleNumberTerm term(fmt("[%s;%s]", from.c_str(), to.c_str()), fieldSpec.getName(), 0, search::query::Weight(1));
    Blueprint::UP bp = f.attrSearchable.createBlueprint(f.requestContext, fieldSpec, term);
    EXPECT_FALSE(LocateRangeItemFromQuery(*bp, 0).locate().valid());
    EXPECT_TRUE(LocateRangeItemFromQuery(*bp, fieldSpec.getFieldId()).locate().valid());
    LocateRangeItemFromQuery locator(*bp, fieldSpec.getFieldId());
    RangeLimitMetaInfo rangeInfo = locator.locate();
    EXPECT_EQUAL(from, rangeInfo.low());
    EXPECT_EQUAL(to, rangeInfo.high());
}

TEST_F("require that RangeLocator locates range from attribute blueprint", RangeLimitFixture) {
    verifyLocateRange("7", "100", f.a1FieldSpec, f);
    verifyLocateRange("7.7", "100.3", f.f1FieldSpec, f);
}


void
verifyRangeIsReflectedInLimiter(const vespalib::string & from, const vespalib::string & to,
                                const FieldSpec & fieldSpec, RangeLimitFixture & f)
{
    search::query::SimpleNumberTerm term(fmt("[%s;%s]", from.c_str(), to.c_str()), fieldSpec.getName(), 0, search::query::Weight(1));
    Blueprint::UP bp = f.attrSearchable.createBlueprint(f.requestContext, fieldSpec, term);
    LocateRangeItemFromQuery locator(*bp, fieldSpec.getFieldId());
    RangeLimitMetaInfo rangeInfo = locator.locate();
    EXPECT_EQUAL(from, rangeInfo.low());
    EXPECT_EQUAL(to, rangeInfo.high());

    MockSearchable mockSearchable;
    MatchPhaseLimiter yes_limiter(10000, locator, mockSearchable, f.requestContext,
                                  DegradationParams(fieldSpec.getName(), 500, true, 1.0, 0.2, 1.0),
                                  DiversityParams());
    MaybeMatchPhaseLimiter &limiter = yes_limiter;
    SearchIterator::UP search = limiter.maybe_limit(prepare(new MockSearch("search")), 0.1, 100000, nullptr);
    limiter.updateDocIdSpaceEstimate(1000, 9000);
    EXPECT_EQUAL(1680u, limiter.getDocIdSpaceEstimate());
    auto *strict_and = dynamic_cast<LimitedSearch *>(search.get());
    ASSERT_TRUE(strict_and != nullptr);
    const auto *ms1 = dynamic_cast<const MockSearch *>(&strict_and->getFirst());
    ASSERT_TRUE(ms1 != nullptr);
    EXPECT_EQUAL(fmt("[%s;%s;-5000]", from.c_str(), to.c_str()), ms1->term);
}
TEST_F("require that range term is used to adjust limiter", RangeLimitFixture) {
    verifyRangeIsReflectedInLimiter("7", "100", f.a1FieldSpec, f);
    verifyRangeIsReflectedInLimiter("7.7", "100.3", f.f1FieldSpec, f);
}

TEST_MAIN() { TEST_RUN_ALL(); }

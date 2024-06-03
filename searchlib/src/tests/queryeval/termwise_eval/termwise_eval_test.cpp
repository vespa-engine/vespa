// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/queryeval/termwise_search.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/termwise_blueprint_helper.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace search;
using namespace search::fef;
using namespace search::queryeval;

//-----------------------------------------------------------------------------

const uint32_t my_field = 0;

//-----------------------------------------------------------------------------

struct MyTerm : public SearchIterator {
    size_t pos;
    bool is_strict;
    std::vector<uint32_t> hits;
    MyTerm(const std::vector<uint32_t> &hits_in, bool is_strict_in)
        : pos(0), is_strict(is_strict_in), hits(hits_in) {}
    void initRange(uint32_t beginid, uint32_t endid) override {
        pos = 0;
        SearchIterator::initRange(beginid, endid);
        if (is_strict) {
            doSeek(beginid);
        }
    }
    void doSeek(uint32_t docid) override {
        while ((pos < hits.size()) && (hits[pos] < docid)) {
            ++pos;
        }
        if (is_strict) {
            if ((pos == hits.size()) || isAtEnd(hits[pos])) {
                setAtEnd();
            } else {
                setDocId(hits[pos]);
            }
        } else {
            if (isAtEnd(docid)) {
                setAtEnd();
            } else if ((pos < hits.size()) && (hits[pos] == docid)) {
                setDocId(docid);
            }
        }
    }
    void doUnpack(uint32_t) override {}
    void visitMembers(vespalib::ObjectVisitor &visitor) const override {
        visit(visitor, "hits", hits);
        visit(visitor, "strict", is_strict);
    }
};

struct MyBlueprint : SimpleLeafBlueprint {
    std::vector<uint32_t> hits;
    MyBlueprint(const std::vector<uint32_t> &hits_in)
        : SimpleLeafBlueprint(), hits(hits_in)
    {
        setEstimate(HitEstimate(hits.size(), hits.empty()));
    }
    MyBlueprint(const std::vector<uint32_t> &hits_in, bool allow_termwise_eval)
        : SimpleLeafBlueprint(), hits(hits_in)
    {
        setEstimate(HitEstimate(hits.size(), hits.empty()));
        set_allow_termwise_eval(allow_termwise_eval);
    }
    MyBlueprint(const std::vector<uint32_t> &hits_in, bool allow_termwise_eval, TermFieldHandle handle)
        : SimpleLeafBlueprint(FieldSpecBase(my_field, handle)), hits(hits_in)
    {
        setEstimate(HitEstimate(hits.size(), hits.empty()));
        set_allow_termwise_eval(allow_termwise_eval);
    }
    ~MyBlueprint() override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override {
        return default_flow_stats(docid_limit, getState().estimate().estHits, 0);
    }
    SearchIterator::UP createLeafSearch(const fef::TermFieldMatchDataArray &) const override {
        return std::make_unique<MyTerm>(hits, strict());
    }
    SearchIteratorUP createFilterSearch(FilterConstraint constraint) const override {
        return create_default_filter(constraint);
    }
};

MyBlueprint::~MyBlueprint() = default;

struct MyOr : OrBlueprint {
    bool use_my_value;
    bool my_value;
    MyOr(bool use_my_value_in, bool my_value_in = true)
        : use_my_value(use_my_value_in), my_value(my_value_in) {}
    bool supports_termwise_children() const override {
        if (use_my_value) {
            return my_value;
        }
        // the default value for intermediate blueprints
        return IntermediateBlueprint::supports_termwise_children();
    }
};

//-----------------------------------------------------------------------------

UnpackInfo no_unpack() { return UnpackInfo(); }

UnpackInfo selective_unpack() {
    UnpackInfo unpack;
    unpack.add(0); // 'only unpack first child' => trigger selective unpack
    return unpack;
}

SearchIterator *TERM(std::initializer_list<uint32_t> hits, bool strict) {
    return new MyTerm(hits, strict);
}

SearchIterator::UP ANDNOT(ChildrenIterators children, bool strict) {
    return AndNotSearch::create(std::move(children), strict);
}

SearchIterator::UP AND(ChildrenIterators children, bool strict) {
    return AndSearch::create(std::move(children), strict);
}

SearchIterator::UP ANDz(ChildrenIterators children, bool strict) {
    return AndSearch::create(std::move(children), strict, no_unpack());
}

SearchIterator::UP ANDs(ChildrenIterators children, bool strict) {
    return AndSearch::create(std::move(children), strict, selective_unpack());
}

SearchIterator::UP OR(ChildrenIterators children, bool strict) {
    return OrSearch::create(std::move(children), strict);
}

SearchIterator::UP ORz(ChildrenIterators children, bool strict) {
    return OrSearch::create(std::move(children), strict, no_unpack());
}

SearchIterator::UP ORs(ChildrenIterators children, bool strict) {
    return OrSearch::create(std::move(children), strict, selective_unpack());
}

//-----------------------------------------------------------------------------

template <typename T>
std::unique_ptr<T> UP(T *t) { return std::unique_ptr<T>(t); }

//-----------------------------------------------------------------------------

SearchIterator::UP make_search(bool strict) {
    return AND({OR({ TERM({2,7}, true),
                     TERM({4,8}, true),
                     TERM({5,6,9}, true) }, true),
                OR({ TERM({1,4,7}, false),
                     TERM({2,5,8}, true),
                     TERM({3,6}, false) }, false),
                OR({ TERM({1,2,3}, false),
                     TERM({4,6}, false),
                     TERM({8,9}, false) }, false)}, strict);
}

SearchIterator::UP make_filter_search(bool strict) {
    return ANDNOT({ TERM({1,2,3,4,5,6,7,8,9}, true),
                    TERM({1,9}, false),
                    TERM({3,7}, true),
                    TERM({5}, false) }, strict);
}

void add_if_inside(uint32_t docid, uint32_t begin, uint32_t end, std::vector<uint32_t> &expect) {
    if (docid >= begin && docid < end) {
        expect.push_back(docid);
    }
}

std::vector<uint32_t> make_expect(uint32_t begin, uint32_t end) {
    std::vector<uint32_t> expect;
    add_if_inside(2, begin, end, expect);
    add_if_inside(4, begin, end, expect);
    add_if_inside(6, begin, end, expect);
    add_if_inside(8, begin, end, expect);
    return expect;
}

void
verify(const std::vector<uint32_t> &expect, SearchIterator &search, uint32_t begin, uint32_t end, const vespalib::string& label)
{
    SCOPED_TRACE(label);
    std::vector<uint32_t> actual;
    search.initRange(begin, end);
    for (uint32_t docid = begin; docid < end; ++docid) {
        if (search.seek(docid)) {
            actual.push_back(docid);
        }
    }
    EXPECT_EQ(expect, actual);
}

//-----------------------------------------------------------------------------

MatchData::UP make_match_data() {
    uint32_t num_handles = 100;
    uint32_t num_fields = 1;
    return MatchData::makeTestInstance(num_handles, num_fields);
}

//-----------------------------------------------------------------------------

TEST(TermwiseEvalTest, require_that_pseudo_term_produces_correct_results)
{
    verify({1,2,3,4,5}, *UP(TERM({1,2,3,4,5}, true)), 1, 6, "strict full");
    verify({1,2,3,4,5}, *UP(TERM({1,2,3,4,5}, false)), 1, 6, "non-strict full");
    verify({3,4,5}, *UP(TERM({1,2,3,4,5}, true)), 3, 6, "strict last");
    verify({3,4,5}, *UP(TERM({1,2,3,4,5}, false)), 3, 6, "non-strict last");
    verify({1,2,3}, *UP(TERM({1,2,3,4,5}, true)), 1, 4, "strict first");
    verify({1,2,3}, *UP(TERM({1,2,3,4,5}, false)), 1, 4, "non-strict first");
}

TEST(TermwiseEvalTest, require_that_normal_search_gives_expected_results)
{
    auto search = make_search(true);
    verify(make_expect(1, 10), *search, 1, 10, "strict normal");
}

TEST(TermwiseEvalTest, require_that_filter_search_gives_expected_results)
{
    auto search = make_filter_search(true);
    verify(make_expect(1, 10), *search, 1, 10, "strict filter");
}

TEST(TermwiseEvalTest, require_that_termwise_and_or_or_search_produces_appropriate_results)
{
    for (uint32_t begin: {1, 2, 5}) {
        for (uint32_t end: {6, 7, 10}) {
            for (bool strict_search: {true, false}) {
                for (bool strict_wrapper: {true, false}) {
                    auto label = make_string("begin: %u, end: %u, strict_search: %s, strict_wrapper: %s",
                                             begin, end, strict_search ? "true" : "false",
                                             strict_wrapper ? "true" : "false");
                    auto search = make_termwise(make_search(strict_search), strict_wrapper);
                    verify(make_expect(begin, end), *search, begin, end, label);
                }
            }
        }
    }
}

TEST(TermwiseEvalTest, require_that_termwise_filter_search_produces_appropriate_results)
{
    for (uint32_t begin: {1, 2, 5}) {
        for (uint32_t end: {6, 7, 10}) {
            for (bool strict_search: {true, false}) {
                for (bool strict_wrapper: {true, false}) {
                    auto label = make_string("begin: %u, end: %u, strict_search: %s, strict_wrapper: %s",
                                             begin, end, strict_search ? "true" : "false",
                                             strict_wrapper ? "true" : "false");
                    auto search = make_termwise(make_filter_search(strict_search), strict_wrapper);
                    verify(make_expect(begin, end), *search, begin, end, label);
                }
            }
        }
    }
}

TEST(TermwiseEvalTest, require_that_termwise_andnot_with_single_term_works)
{
    verify({2,3,4}, *make_termwise(ANDNOT({ TERM({1,2,3,4,5}, true) }, true), true), 2, 5, "termwise andnot");
}

TEST(TermwiseEvalTest, require_that_pseudo_term_is_rewindable)
{
    auto search = UP(TERM({1,2,3,4,5}, true));
    verify({3,4,5}, *search, 3, 6, "pseudo term end");
    verify({1,2,3,4}, *search, 1, 5, "pseudo term rewound to start");
}

TEST(TermwiseEvalTest, require_that_termwise_wrapper_is_rewindable)
{
    auto search = make_termwise(make_search(true), true);
    verify(make_expect(3, 7), *search, 3, 7, "termwise wrapper end");
    verify(make_expect(1, 5), *search, 1, 5, "termwise wrapper rewound to start");
}

//-----------------------------------------------------------------------------

TEST(TermwiseEvalTest, require_that_leaf_blueprints_allow_termwise_evaluation_by_default)
{
    MyBlueprint bp({});
    EXPECT_TRUE(bp.getState().allow_termwise_eval());
}

TEST(TermwiseEvalTest, require_that_leaf_blueprints_can_enable_and_disable_termwise_evaluation)
{
    MyBlueprint enable({}, true);
    MyBlueprint disable({}, false);
    EXPECT_TRUE(enable.getState().allow_termwise_eval());
    EXPECT_FALSE(disable.getState().allow_termwise_eval());
}

TEST(TermwiseEvalTest, require_that_intermediate_blueprints_disallow_termwise_evaluation_by_default)
{
    MyOr bp(false);
    bp.addChild(UP(new MyBlueprint({}, true)));
    bp.addChild(UP(new MyBlueprint({}, true)));
    EXPECT_FALSE(bp.getState().allow_termwise_eval());
}

TEST(TermwiseEvalTest, require_that_intermediate_blueprints_can_enable_and_disable_termwise_evaluation)
{
    MyOr enable(true, true);
    enable.addChild(UP(new MyBlueprint({}, true)));
    enable.addChild(UP(new MyBlueprint({}, true)));
    EXPECT_TRUE(enable.getState().allow_termwise_eval());
    MyOr disable(true, false);
    disable.addChild(UP(new MyBlueprint({}, true)));
    disable.addChild(UP(new MyBlueprint({}, true)));
    EXPECT_FALSE(disable.getState().allow_termwise_eval());
}

TEST(TermwiseEvalTest, require_that_intermediate_blueprints_cannot_be_termwise_unless_all_its_children_are_termwise)
{
    MyOr bp(true, true);
    bp.addChild(UP(new MyBlueprint({}, true)));
    bp.addChild(UP(new MyBlueprint({}, false)));
    EXPECT_FALSE(bp.getState().allow_termwise_eval());
}

//-----------------------------------------------------------------------------

TEST(TermwiseEvalTest, require_that_leafs_have_tree_size_1)
{
    MyBlueprint bp({});
    EXPECT_EQ(1u, bp.getState().tree_size());
}

TEST(TermwiseEvalTest, require_that_tree_size_is_accumulated_correctly_by_intermediate_nodes)
{
    MyOr bp(false);
    EXPECT_EQ(1u, bp.getState().tree_size());
    bp.addChild(UP(new MyBlueprint({})));
    bp.addChild(UP(new MyBlueprint({})));
    EXPECT_EQ(3u, bp.getState().tree_size());
    auto child = UP(new MyOr(false));
    child->addChild(UP(new MyBlueprint({})));
    child->addChild(UP(new MyBlueprint({})));
    bp.addChild(std::move(child));
    EXPECT_EQ(6u, bp.getState().tree_size());
}

//-----------------------------------------------------------------------------

TEST(TermwiseEvalTest, require_that_any_blueprint_node_can_obtain_the_root)
{
    MyOr bp(false);
    bp.addChild(UP(new MyBlueprint({1,2,3})));
    bp.addChild(UP(new MyBlueprint({1,2,3,4,5,6})));
    EXPECT_TRUE(&bp != &bp.getChild(0));
    EXPECT_TRUE(&bp != &bp.getChild(1));
    EXPECT_TRUE(&bp == &bp.getChild(0).root());
    EXPECT_TRUE(&bp == &bp.getChild(1).root());
    EXPECT_TRUE(&bp == &bp.root());
}

//-----------------------------------------------------------------------------

TEST(TermwiseEvalTest, require_that_match_data_keeps_track_of_the_termwise_limit)
{
    auto md = make_match_data();
    EXPECT_EQ(1.0, md->get_termwise_limit());
    md->set_termwise_limit(0.03);
    EXPECT_EQ(0.03, md->get_termwise_limit());
}

//-----------------------------------------------------------------------------

TEST(TermwiseEvalTest, require_that_terwise_test_search_string_dump_is_detailed_enough)
{
    EXPECT_EQ(make_termwise(OR({ TERM({1,2,3}, true), TERM({2,3}, true), TERM({3}, true) }, true), true)->asString(),
              make_termwise(OR({ TERM({1,2,3}, true), TERM({2,3}, true), TERM({3}, true) }, true), true)->asString());

    EXPECT_NE(make_termwise(OR({ TERM({1,2,3}, true), TERM({2,3}, true), TERM({3}, true) }, true), true)->asString(),
              make_termwise(OR({ TERM({1,2,3}, true), TERM({2,3}, false), TERM({3}, true) }, true), true)->asString());

    EXPECT_NE(make_termwise(OR({ TERM({1,2,3}, true), TERM({2,3}, true), TERM({3}, true) }, true), true)->asString(),
              make_termwise(OR({ TERM({1,2,3}, true), TERM({2,3}, true), TERM({3}, true) }, false), true)->asString());

    EXPECT_NE(make_termwise(OR({ TERM({1,2,3}, true), TERM({2,3}, true), TERM({3}, true) }, true), true)->asString(),
              make_termwise(OR({ TERM({1,2,3}, true), TERM({2,3}, true), TERM({3}, true) }, true), false)->asString());

    EXPECT_NE(make_termwise(OR({ TERM({1,2,3}, true), TERM({2,3}, true), TERM({3}, true) }, true), true)->asString(),
              make_termwise(OR({ TERM({1,2,3}, true), TERM({3}, true), TERM({2,3}, true) }, true), true)->asString());
}

//-----------------------------------------------------------------------------

TEST(TermwiseEvalTest, require_that_basic_termwise_evaluation_works)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(2)->tagAsNotNeeded();
    OrBlueprint my_or;
    my_or.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_or.addChild(UP(new MyBlueprint({2}, true, 2)));
    for (bool strict: {true, false}) {
        my_or.basic_plan(strict, 100);
        EXPECT_EQ(my_or.createSearch(*md)->asString(),
                  make_termwise(OR({ TERM({1}, strict), TERM({2}, strict) }, strict), strict)->asString());
    }
}

TEST(TermwiseEvalTest, require_that_the_hit_rate_must_be_high_enough_for_termwise_evaluation_to_be_activated)
{
    auto md = make_match_data();
    md->set_termwise_limit(1.0); // <-
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(2)->tagAsNotNeeded();
    OrBlueprint my_or;
    my_or.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_or.addChild(UP(new MyBlueprint({2}, true, 2)));
    for (bool strict: {true, false}) {
        my_or.basic_plan(strict, 100);
        EXPECT_TRUE(my_or.createSearch(*md)->asString().find("TermwiseSearch") == vespalib::string::npos);
    }
}

TEST(TermwiseEvalTest, require_that_enough_unranked_termwise_terms_are_present_for_termwise_evaluation_to_be_activated)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(2)->tagAsNotNeeded();
    OrBlueprint my_or;
    my_or.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_or.addChild(UP(new MyBlueprint({2}, false, 2))); // <- not termwise
    my_or.addChild(UP(new MyBlueprint({3}, true, 3)));  // <- ranked
    for (bool strict: {true, false}) {
        my_or.basic_plan(strict, 100);
        EXPECT_TRUE(my_or.createSearch(*md)->asString().find("TermwiseSearch") == vespalib::string::npos);
    }
}

TEST(TermwiseEvalTest, require_that_termwise_evaluation_can_be_multi_level_but_not_duplicated)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(2)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    OrBlueprint my_or;
    my_or.addChild(UP(new MyBlueprint({1}, true, 1)));
    auto child = UP(new OrBlueprint());
    child->addChild(UP(new MyBlueprint({2}, true, 2)));
    child->addChild(UP(new MyBlueprint({3}, true, 3)));
    my_or.addChild(std::move(child));
    for (bool strict: {true, false}) {
        my_or.null_plan(strict, 100);
        EXPECT_EQ(my_or.createSearch(*md)->asString(),
                  make_termwise(OR({ TERM({1}, strict),
                                     ORz({ TERM({2}, strict), TERM({3}, strict) }, strict) },
                                   strict), strict)->asString());
    }
}

//-----------------------------------------------------------------------------

TEST(TermwiseEvalTest, require_that_or_can_be_completely_termwise)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(2)->tagAsNotNeeded();
    OrBlueprint my_or;
    my_or.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_or.addChild(UP(new MyBlueprint({2}, true, 2)));
    for (bool strict: {true, false}) {
        my_or.basic_plan(strict, 100);
        EXPECT_EQ(my_or.createSearch(*md)->asString(),
                  make_termwise(OR({ TERM({1}, strict), TERM({2}, strict) }, strict), strict)->asString());
    }
}

TEST(TermwiseEvalTest, require_that_or_can_be_partially_termwise)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    OrBlueprint my_or;
    my_or.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_or.addChild(UP(new MyBlueprint({2}, true, 2)));
    my_or.addChild(UP(new MyBlueprint({3}, true, 3)));
    for (bool strict: {true, false}) {
        my_or.basic_plan(strict, 100);
        EXPECT_EQ(my_or.createSearch(*md)->asString(),
                  ORs({ make_termwise(OR({ TERM({1}, strict), TERM({3}, strict) }, strict), strict),
                           TERM({2}, strict) }, strict)->asString());
    }
}

TEST(TermwiseEvalTest, require_that_or_puts_termwise_subquery_at_the_right_place)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(2)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    OrBlueprint my_or;
    my_or.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_or.addChild(UP(new MyBlueprint({2}, true, 2)));
    my_or.addChild(UP(new MyBlueprint({3}, true, 3)));
    for (bool strict: {true, false}) {
        my_or.basic_plan(strict, 100);
        EXPECT_EQ(my_or.createSearch(*md)->asString(),
                  ORs({ TERM({1}, strict),
                        make_termwise(OR({ TERM({2}, strict), TERM({3}, strict) }, strict),
                                      strict) }, strict)->asString());
    }
}

TEST(TermwiseEvalTest, require_that_or_can_use_termwise_eval_also_when_having_non_termwise_children)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(2)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    OrBlueprint my_or;
    my_or.addChild(UP(new MyBlueprint({1}, false, 1)));
    my_or.addChild(UP(new MyBlueprint({2}, true, 2)));
    my_or.addChild(UP(new MyBlueprint({3}, true, 3)));
    for (bool strict: {true, false}) {
        my_or.basic_plan(strict, 100);
        EXPECT_EQ(my_or.createSearch(*md)->asString(),
                  ORz({ TERM({1}, strict),
                        make_termwise(OR({ TERM({2}, strict), TERM({3}, strict) }, strict),
                                      strict)},
                      strict)->asString());
    }
}

//-----------------------------------------------------------------------------

TEST(TermwiseEvalTest, require_that_and_can_be_completely_termwise)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(2)->tagAsNotNeeded();
    AndBlueprint my_and;
    my_and.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_and.addChild(UP(new MyBlueprint({2}, true, 2)));
    for (bool strict: {true, false}) {
        my_and.basic_plan(strict, 100);
        EXPECT_EQ(my_and.createSearch(*md)->asString(),
                  make_termwise(AND({ TERM({1}, strict), TERM({2}, false) }, strict), strict)->asString());
    }
}

TEST(TermwiseEvalTest, require_that_and_can_be_partially_termwise)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    AndBlueprint my_and;
    my_and.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_and.addChild(UP(new MyBlueprint({2}, true, 2)));
    my_and.addChild(UP(new MyBlueprint({3}, true, 3)));
    for (bool strict: {true, false}) {
        my_and.basic_plan(strict, 100);
        EXPECT_EQ(my_and.createSearch(*md)->asString(),
                     ANDs({ make_termwise(AND({ TERM({1}, strict), TERM({3}, false) },
                                              strict),
                                          strict),
                            TERM({2}, false) }, strict)->asString());
    }
}

TEST(TermwiseEvalTest, require_that_and_puts_termwise_subquery_at_the_right_place)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(2)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    AndBlueprint my_and;
    my_and.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_and.addChild(UP(new MyBlueprint({2}, true, 2)));
    my_and.addChild(UP(new MyBlueprint({3}, true, 3)));
    for (bool strict: {true, false}) {
        my_and.basic_plan(strict, 100);
        EXPECT_EQ(my_and.createSearch(*md)->asString(),
                     ANDs({ TERM({1}, strict),
                            make_termwise(AND({ TERM({2}, false), TERM({3}, false) }, false),
                                          false) }, strict)->asString());
    }
}

TEST(TermwiseEvalTest, require_that_and_can_use_termwise_eval_also_when_having_non_termwise_children)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(2)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    AndBlueprint my_and;
    my_and.addChild(UP(new MyBlueprint({1}, false, 1)));
    my_and.addChild(UP(new MyBlueprint({2}, true, 2)));
    my_and.addChild(UP(new MyBlueprint({3}, true, 3)));
    for (bool strict: {true, false}) {
        my_and.basic_plan(strict, 100);
        EXPECT_EQ(my_and.createSearch(*md)->asString(),
                     ANDz({ TERM({1}, strict),
                            make_termwise(AND({ TERM({2}, false), TERM({3}, false) }, false),
                                          false) }, strict)->asString());
    }
}

//-----------------------------------------------------------------------------

TEST(TermwiseEvalTest, require_that_andnot_can_be_completely_termwise)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(1)->tagAsNotNeeded();
    AndNotBlueprint my_andnot;
    my_andnot.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_andnot.addChild(UP(new MyBlueprint({2}, true, 2)));
    for (bool strict: {true, false}) {
        my_andnot.basic_plan(strict, 100);
        EXPECT_EQ(my_andnot.createSearch(*md)->asString(),
                     make_termwise(ANDNOT({ TERM({1}, strict), TERM({2}, false) },
                                          strict), strict)->asString());
    }
}

TEST(TermwiseEvalTest, require_that_andnot_can_be_partially_termwise)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    AndNotBlueprint my_andnot;
    my_andnot.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_andnot.addChild(UP(new MyBlueprint({2}, true, 2)));
    my_andnot.addChild(UP(new MyBlueprint({3}, true, 3)));
    for (bool strict: {true, false}) {
        my_andnot.basic_plan(strict, 100);
        EXPECT_EQ(my_andnot.createSearch(*md)->asString(),
                     ANDNOT({ TERM({1}, strict),
                              make_termwise(OR({ TERM({2}, false), TERM({3}, false) }, false),
                                            false) }, strict)->asString());
    }
}

TEST(TermwiseEvalTest, require_that_andnot_can_be_partially_termwise_with_first_child_being_termwise)
{
    auto md = make_match_data();
    md->set_termwise_limit(0.0);
    md->resolveTermField(1)->tagAsNotNeeded();
    AndNotBlueprint my_andnot;
    my_andnot.addChild(UP(new MyBlueprint({1}, true, 1)));
    my_andnot.addChild(UP(new MyBlueprint({2}, false, 2)));
    my_andnot.addChild(UP(new MyBlueprint({3}, true, 3)));
    for (bool strict: {true, false}) {
        my_andnot.basic_plan(strict, 100);
        EXPECT_EQ(my_andnot.createSearch(*md)->asString(),
                     ANDNOT({ make_termwise(ANDNOT({ TERM({1}, strict), TERM({3}, false) }, strict),
                                            strict),
                              TERM({2}, false) }, strict)->asString());
    }
}

//-----------------------------------------------------------------------------

TEST(TermwiseEvalTest, require_that_termwise_blueprint_helper_calculates_unpack_info_correctly)
{
    OrBlueprint my_or;
    my_or.addChild(UP(new MyBlueprint({1}, false, 1))); // termwise not allowed
    my_or.addChild(UP(new MyBlueprint({2}, false, 2))); // termwise not allowed and ranked
    my_or.addChild(UP(new MyBlueprint({3}, true, 3)));
    my_or.addChild(UP(new MyBlueprint({4}, true, 4))); // ranked
    my_or.addChild(UP(new MyBlueprint({5}, true, 5)));
    MultiSearch::Children dummy_searches(5);
    UnpackInfo unpack; // non-termwise unpack info
    unpack.add(1);
    unpack.add(3);
    TermwiseBlueprintHelper helper(my_or, std::move(dummy_searches), unpack);
    EXPECT_EQ(helper.get_result().size(), 3u);
    EXPECT_EQ(helper.get_termwise_children().size(), 2u);
    EXPECT_EQ(helper.first_termwise, 2u);
    EXPECT_TRUE(!helper.termwise_unpack.needUnpack(0));
    EXPECT_TRUE(helper.termwise_unpack.needUnpack(1));
    EXPECT_TRUE(!helper.termwise_unpack.needUnpack(2));
    EXPECT_TRUE(helper.termwise_unpack.needUnpack(3));
    EXPECT_TRUE(!helper.termwise_unpack.needUnpack(4));
    EXPECT_TRUE(!helper.termwise_unpack.needUnpack(5));
}

class Verifier : public search::test::SearchIteratorVerifier {
public:
    SearchIterator::UP create(bool strict) const override {
        return make_termwise(createIterator(getExpectedDocIds(), strict), strict);
    }
};
TEST(TermwiseEvalTest, test_termwise_adheres_to_search_iterator_requirements)
{
    Verifier verifier;
    verifier.verify();
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()

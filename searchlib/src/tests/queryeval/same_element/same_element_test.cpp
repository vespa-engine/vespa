// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/queryeval/element_id_extractor.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>
#include <vespa/searchlib/queryeval/same_element_search.h>
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>

using namespace search::fef;
using namespace search::queryeval;

enum class QueryTweak {
    NORMAL,       // All children of query root are term nodes
    AND,          // Last child is AND with two term nodes
    OR,           // Last child is OR with two term nodes
    ANDNOT,       // Last child is ANDNOT with two term nodes
    RANK          // Last child is RANK with two term nodes
};

using OptElems = std::optional<std::vector<uint32_t>>;

void verify_elements(SameElementSearch &se, uint32_t docid, const std::initializer_list<uint32_t> list) {
    SCOPED_TRACE("verify elements, docid=" + std::to_string(docid));
    std::vector<uint32_t> expect(list);
    std::vector<uint32_t> actual;
    se.find_matching_elements(docid, actual);
    EXPECT_EQ(actual, expect);
}

void verify_md_elements(MatchData& md, const std::string& label, uint32_t docid, std::vector<OptElems> exp)
{
    SCOPED_TRACE("verify md_elements, " + label + ", docid=" + std::to_string(docid));
    std::vector<OptElems> act;
    act.reserve(exp.size());
    for (uint32_t i = 0; i < exp.size(); ++i) {
        act.emplace_back();
        auto& tfmd = *md.resolveTermField(i);
        if (tfmd.has_data(docid)) {
            ElementIdExtractor::get_element_ids(tfmd, docid, act.back().emplace());
        }
    }
    EXPECT_EQ(exp, act);
}

OptElems hit(std::vector<uint32_t> elems) {
    return elems;
}

OptElems nohit() {
    return std::nullopt;
}

FieldSpec make_field_spec(MatchDataLayout& mdl) {
    // This field spec is aligned with the match data created below.
    uint32_t field_id = 0;
    return {"foo", field_id, mdl.allocTermField(field_id)};
}

std::unique_ptr<MatchDataLayout> make_match_data_layout() {
    auto mdl = std::make_unique<MatchDataLayout>();
    return mdl;
}

std::unique_ptr<SameElementBlueprint> make_blueprint(QueryTweak query_tweak, MatchDataLayout& mdl,
                                                     const std::vector<FakeResult> &children,
                                                     bool fake_attr,
                                                     std::vector<uint32_t> element_filter = std::vector<uint32_t>()) {
    std::vector<std::unique_ptr<Blueprint>> bp_children;
    bp_children.reserve(children.size());
    std::vector<TermFieldHandle> descendants_index_handles;
    std::unique_ptr<IntermediateBlueprint> bp_tweak;
    for (size_t i = 0; i < children.size(); ++i) {
        switch (query_tweak) {
            case QueryTweak::AND:
                if (i + 2 == children.size()) {
                    bp_tweak = std::make_unique<AndBlueprint>();
                }
                break;
            case QueryTweak::OR:
                if (i + 2 == children.size()) {
                    bp_tweak = std::make_unique<OrBlueprint>();
                }
                break;
            case QueryTweak::ANDNOT:
                if (i + 2 == children.size()) {
                    bp_tweak = std::make_unique<AndNotBlueprint>(true);
                }
                break;
            case QueryTweak::RANK:
                if (i + 2 == children.size()) {
                    bp_tweak = std::make_unique<RankBlueprint>();
                }
                break;
            default:
                ;
        }
        uint32_t field_id = i;
        std::string field_name = vespalib::make_string("f%u", field_id);
        FieldSpec field(field_name, field_id, mdl.allocTermField(field_id), false);
        descendants_index_handles.emplace_back(field.getHandle());
        auto fake = std::make_unique<FakeBlueprint>(field, children[i]);
        fake->is_attr(fake_attr);
        if (bp_tweak) {
            bp_tweak->addChild(std::move(fake));
        } else {
            bp_children.emplace_back(std::move(fake));
        }
    }
    if (bp_tweak) {
        bp_children.emplace_back(std::move(bp_tweak));
    }
    auto result = std::make_unique<SameElementBlueprint>(make_field_spec(mdl), descendants_index_handles,
                                                         false, std::move(element_filter));
    for (auto& fake : bp_children) {
        result->addChild(std::move(fake));
    }
    return result;
}

std::unique_ptr<SameElementBlueprint> make_blueprint(MatchDataLayout& mdl,
                                                     const std::vector<FakeResult> &children,
                                                     bool fake_attr = false,
                                                     std::vector<uint32_t> element_filter = std::vector<uint32_t>()) {
    return make_blueprint(QueryTweak::NORMAL, mdl, children, fake_attr, std::move(element_filter));
}

Blueprint::UP finalize(Blueprint::UP bp, bool strict) {
    bp->setDocIdLimit(1000);
    Blueprint::UP result = Blueprint::optimize_and_sort(std::move(bp), strict);
    result->fetchPostings(ExecuteInfo::FULL);
    result->freeze();
    return result;
}

SimpleResult find_matches(const std::vector<FakeResult> &children, std::vector<uint32_t> element_filter = std::vector<uint32_t>()) {
    auto mdl = make_match_data_layout();
    auto bp = finalize(make_blueprint(*mdl, children, false, std::move(element_filter)), false);
    auto md = mdl->createMatchData();
    auto search = bp->createSearch(*md);
    return SimpleResult().search(*search, 1000);
}

FakeResult make_result(const std::vector<std::pair<uint32_t,std::vector<uint32_t> > > &match_data) {
    FakeResult result;
    uint32_t pos_should_be_ignored = 0;
    for (const auto &doc: match_data) {
        result.doc(doc.first);
        for (const auto &elem: doc.second) {
            result.elem(elem).pos(++pos_should_be_ignored);
        }
    }
    return result;
}

TEST(SameElementTest, require_that_simple_match_can_be_found) {
    auto a = make_result({{5, {1,3,7}}});
    auto b = make_result({{5, {3,5,10}}});
    SimpleResult result = find_matches({a, b});
    SimpleResult expect({5});
    EXPECT_EQ(result, expect);
}

TEST(SameElementTest, require_that_matching_elements_can_be_identified) {
    auto a = make_result({{5, {1,3,7,12}}, {10, {1,2,3}}});
    auto b = make_result({{5, {3,5,7,10}}, {10, {4,5,6}}});
    auto mdl = make_match_data_layout();
    auto bp = finalize(make_blueprint(*mdl, {a,b}), false);
    auto md = mdl->createMatchData();
    auto search = bp->createSearch(*md);
    search->initRange(1, 1000);
    auto *se = dynamic_cast<SameElementSearch*>(search.get());
    ASSERT_TRUE(se != nullptr);
    verify_elements(*se, 5, {3, 7});
    verify_elements(*se, 10, {});
    verify_elements(*se, 20, {});
}

TEST(SameElementTest, require_that_children_must_match_within_same_element) {
    auto a = make_result({{5, {1,3,7}}});
    auto b = make_result({{5, {2,5,10}}});
    SimpleResult result = find_matches({a, b});
    SimpleResult expect;
    EXPECT_EQ(result, expect);
}

TEST(SameElementTest, require_that_strict_iterator_seeks_to_next_hit_and_can_unpack_matching_docid) {
    auto mdl = make_match_data_layout();
    auto a = make_result({{5, {1,2}}, {7, {1,2}}, {8, {1,2}}, {9, {1,2}}});
    auto b = make_result({{5, {3}}, {6, {1,2}}, {7, {2,4}}, {9, {1}}});
    auto sebp = make_blueprint(*mdl, {a, b});
    auto handle = sebp->get_field().getHandle();
    auto bp = finalize(std::move(sebp), true);
    auto md = mdl->createMatchData();
    auto search = bp->createSearch(*md);
    auto* tfmd = md->resolveTermField(handle);
    search->initRange(1, 1000);
    EXPECT_LT(search->getDocId(), 1u);
    EXPECT_FALSE(search->seek(1));
    EXPECT_EQ(search->getDocId(), 7u);
    search->unpack(7);
    EXPECT_TRUE(tfmd->has_ranking_data(7u));
    EXPECT_TRUE(search->seek(9));
    EXPECT_EQ(search->getDocId(), 9u);
    EXPECT_TRUE(tfmd->has_ranking_data(7u));
    search->unpack(9);
    EXPECT_TRUE(tfmd->has_ranking_data(9u));
    EXPECT_FALSE(search->seek(10));
    EXPECT_TRUE(search->isAtEnd());
}

TEST(SameElementTest, require_that_results_are_estimated_appropriately) {
    auto a = make_result({{5, {0}}, {5, {0}}, {5, {0}}});
    auto b = make_result({{5, {0}}, {5, {0}}});
    auto c = make_result({{5, {0}}, {5, {0}}, {5, {0}}, {5, {0}}});
    auto mdl = make_match_data_layout();
    auto bp = finalize(make_blueprint(*mdl, {a,b,c}), true);
    EXPECT_EQ(bp->getState().estimate().estHits, 2u);
}

TEST(SameElementTest, require_that_children_are_sorted) {
    auto a = make_result({{5, {0}}, {5, {0}}, {5, {0}}});
    auto b = make_result({{5, {0}}, {5, {0}}});
    auto c = make_result({{5, {0}}, {5, {0}}, {5, {0}}, {5, {0}}});
    auto mdl = make_match_data_layout();
    auto bp = finalize(make_blueprint(*mdl, {a,b,c}), true);
    EXPECT_EQ(dynamic_cast<SameElementBlueprint&>(*bp).getChild(0).getState().estimate().estHits, 2u);
    EXPECT_EQ(dynamic_cast<SameElementBlueprint&>(*bp).getChild(1).getState().estimate().estHits, 3u);
    EXPECT_EQ(dynamic_cast<SameElementBlueprint&>(*bp).getChild(2).getState().estimate().estHits, 4u);
}

TEST(SameElementTest, require_that_and_below_same_element_works)
{
    auto a = make_result({{3, {5, 7, 10, 12}}, {7, {5, 7}}, {9, {4, 6, 9, 10}}});
    auto b = make_result({{3, {4, 7, 12, 14}}, {7, {6}}, {9, {3, 9, 13}}});
    auto mdl = make_match_data_layout();
    auto bp = finalize(make_blueprint(QueryTweak::AND, *mdl, {a, b}, false), false);
    auto md = mdl->createMatchData();
    auto search = bp->createSearch(*md);
    search->initRange(1, 1000);
    auto *se = dynamic_cast<SameElementSearch*>(search.get());
    ASSERT_TRUE(se != nullptr);
    verify_elements(*se, 3, {7, 12});
    verify_elements(*se, 7, {});
    verify_elements(*se, 9, {9});
    md->soft_reset();
    search->initRange(1, 1000);
    EXPECT_TRUE(search->seek(3));
    verify_md_elements(*md, "before unpack", 3, { hit({7, 12}), hit({7, 12}) });
    search->unpack(3);
    verify_md_elements(*md, "after unpack", 3, { hit({7, 12}), hit({7, 12}) });
    EXPECT_FALSE(search->seek(7));
    verify_md_elements(*md, "before unpack", 7, { nohit(), nohit() });
    EXPECT_TRUE(search->seek(9));
    verify_md_elements(*md, "before unpack", 9, { hit({9}), hit({9}) });
    search->unpack(9);
    verify_md_elements(*md, "after unpack", 9, { hit({9}), hit({9}) });
}

TEST(SameElementTest, require_that_or_below_same_element_works)
{
    auto a = make_result({{3, {5, 10}}, {9, {6}}});
    auto b = make_result({{3, {7, 12}}, {9, {4, 9}}});
    auto mdl = make_match_data_layout();
    auto bp = finalize(make_blueprint(QueryTweak::OR, *mdl, {a, b}, false), true);
    auto md = mdl->createMatchData();
    auto search = bp->createSearch(*md);
    search->initRange(1, 1000);
    auto *se = dynamic_cast<SameElementSearch*>(search.get());
    ASSERT_TRUE(se != nullptr);
    verify_elements(*se, 3, {5, 7, 10, 12});
    verify_elements(*se, 9, {4, 6, 9});
}

TEST(SameElementTest, require_that_and_not_below_same_element_works)
{
    auto a = make_result({{3, {5, 7, 10, 12}}, {5, {5, 10}}, {9, {4, 6, 9}}});
    auto b = make_result({{3, {7, 12}}, {5, {5, 7, 10, 12}}, {9, {4, 9}}});
    auto mdl = make_match_data_layout();
    auto bp = finalize(make_blueprint(QueryTweak::ANDNOT, *mdl, {a, b}, false), true);
    auto md = mdl->createMatchData();
    auto search = bp->createSearch(*md);
    search->initRange(1, 1000);
    auto *se = dynamic_cast<SameElementSearch*>(search.get());
    ASSERT_TRUE(se != nullptr);
    ASSERT_TRUE(se->seek(3));
    verify_elements(*se, 3, {5, 10});
    ASSERT_FALSE(se->seek(5));
    verify_elements(*se, 5, {});
    ASSERT_TRUE(se->seek(9));
    verify_elements(*se, 9, {6});
}

TEST(SameElementTest, require_that_rank_below_same_element_works)
{
    auto a = make_result({{3, {5, 7, 10, 12}}, {5, {5, 10}}, {9, {4, 5, 9}}});
    auto b = make_result({{3, {7, 12}}, {5, {5, 7, 10, 12}}, {9, {4, 9, 12}}});
    auto mdl = make_match_data_layout();
    auto bp = finalize(make_blueprint(QueryTweak::RANK, *mdl, {a, b}, false), true);
    auto md = mdl->createMatchData();
    auto search = bp->createSearch(*md);
    search->initRange(1, 1000);
    auto *se = dynamic_cast<SameElementSearch*>(search.get());
    ASSERT_TRUE(se != nullptr);
    ASSERT_TRUE(se->seek(3));
    verify_elements(*se, 3, {5, 7, 10, 12});
    ASSERT_TRUE(se->seek(5));
    verify_elements(*se, 5, {5, 10});
    ASSERT_TRUE(se->seek(9));
    verify_elements(*se, 9, {4, 5, 9});
    md->soft_reset();
    search->initRange(1, 1000);
    EXPECT_TRUE(search->seek(3));
    search->unpack(3);
    verify_md_elements(*md, "after unpack", 3, { hit({5, 7, 10, 12}), hit({7, 12}) });
    EXPECT_TRUE(search->seek(5));
    search->unpack(5);
    verify_md_elements(*md, "after unpack", 5, { hit({5, 10}), hit({5, 10}) });
    EXPECT_TRUE(search->seek(9));
    search->unpack(9);
    verify_md_elements(*md, "after unpack", 9, { hit({4, 5, 9}), hit({4, 9}) });
}

TEST(SameElementTest, require_that_simple_match_can_be_found_with_element_filter) {
    auto a = make_result({{5, {1,3,7}}});
    auto b = make_result({{5, {3,5,10}}});
    SimpleResult result = find_matches({a, b}, {3}); // Element filter for id 3
    SimpleResult expect({5});
    EXPECT_EQ(result, expect);
}

TEST(SameElementTest, require_that_simple_match_can_be_found_with_element_filter_with_multiple_ids) {
    auto a = make_result({{5, {1,3,7}}});
    auto b = make_result({{5, {3,5,10}}});
    SimpleResult result = find_matches({a, b}, {1,2,3}); // Element filter for ids 1, 2, and 3
    SimpleResult expect = SimpleResult({5});
    EXPECT_EQ(result, expect);
}

TEST(SameElementTest, require_that_element_filter_prevents_simple_match) {
    auto a = make_result({{5, {1,3,7}}});
    auto b = make_result({{5, {3,5,10}}});
    SimpleResult result = find_matches({a, b}, {2}); // Element filter for id 2
    SimpleResult expect;
    EXPECT_EQ(result, expect);
}


TEST(SameElementTest, require_that_element_filter_selects_precisely_correct_elements) {
    auto a = make_result({{5, {1,3,7,12}},
                          {10, {1,2,3}},
                          {15, {3,4,5}},
                          {20, {4,5,6}},
                          {25, {1}}});
    auto mdl = make_match_data_layout();
    auto bp = finalize(make_blueprint(*mdl, {a}, false, {1, 3}), false);
    auto md = mdl->createMatchData();
    auto search = bp->createSearch(*md);
    search->initRange(1, 1000);
    auto *se = dynamic_cast<SameElementSearch*>(search.get());
    ASSERT_TRUE(se != nullptr);
    verify_elements(*se, 5, {1, 3});
    verify_elements(*se, 10, {1, 3});
    verify_elements(*se, 15, {3});
    verify_elements(*se, 20, {});
    verify_elements(*se, 25, {1});
    verify_elements(*se, 30, {});
}

GTEST_MAIN_RUN_ALL_TESTS()

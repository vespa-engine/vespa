// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>
#include <vespa/searchlib/queryeval/same_element_search.h>
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::fef;
using namespace search::queryeval;

enum class QueryTweak {
    NORMAL,       // All children of query root are term nodes
    AND,          // Last child is AND with two term nodes
    OR            // Last child is OR with two term nodes
};

void verify_elements(SameElementSearch &se, uint32_t docid, const std::initializer_list<uint32_t> list) {
    SCOPED_TRACE("verify elements, docid=" + std::to_string(docid));
    std::vector<uint32_t> expect(list);
    std::vector<uint32_t> actual;
    se.find_matching_elements(docid, actual);
    EXPECT_EQ(actual, expect);
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
                                                     const std::vector<FakeResult> &children, bool fake_attr) {
    std::vector<std::unique_ptr<Blueprint>> bp_children;
    bp_children.reserve(children.size());
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
            default:
                ;
        }
        uint32_t field_id = i;
        std::string field_name = vespalib::make_string("f%u", field_id);
        FieldSpec field(field_name, field_id, mdl.allocTermField(field_id), false);
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
    auto result = std::make_unique<SameElementBlueprint>(make_field_spec(mdl), false);
    for (auto& fake : bp_children) {
        result->addChild(std::move(fake));
    }
    return result;
}

std::unique_ptr<SameElementBlueprint> make_blueprint(MatchDataLayout& mdl,
                                                     const std::vector<FakeResult> &children, bool fake_attr = false) {
    return make_blueprint(QueryTweak::NORMAL, mdl, children, fake_attr);
}

Blueprint::UP finalize(Blueprint::UP bp, bool strict) {
    bp->setDocIdLimit(1000);
    Blueprint::UP result = Blueprint::optimize_and_sort(std::move(bp), strict);
    result->fetchPostings(ExecuteInfo::FULL);
    result->freeze();
    return result;
}

SimpleResult find_matches(const std::vector<FakeResult> &children) {
    auto mdl = make_match_data_layout();
    auto bp = finalize(make_blueprint(*mdl, children), false);
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
    EXPECT_EQ(tfmd->getDocId(), 7u);
    EXPECT_TRUE(search->seek(9));
    EXPECT_EQ(search->getDocId(), 9u);
    EXPECT_EQ(tfmd->getDocId(), 7u);
    search->unpack(9);
    EXPECT_EQ(tfmd->getDocId(), 9u);
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
    auto a = make_result({{3, {5, 7, 10, 12}}, {9, {4, 6, 9, 10}}});
    auto b = make_result({{3, {4, 7, 12, 14}}, {9, {3, 9, 13}}});
    auto mdl = make_match_data_layout();
    auto bp = finalize(make_blueprint(QueryTweak::AND, *mdl, {a, b}, false), true);
    auto md = mdl->createMatchData();
    auto search = bp->createSearch(*md);
    search->initRange(1, 1000);
    auto *se = dynamic_cast<SameElementSearch*>(search.get());
    ASSERT_TRUE(se != nullptr);
    verify_elements(*se, 3, {7, 12});
    verify_elements(*se, 9, {9});
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

GTEST_MAIN_RUN_ALL_TESTS()

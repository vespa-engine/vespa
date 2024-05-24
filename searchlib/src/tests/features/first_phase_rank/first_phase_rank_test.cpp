// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/first_phase_rank_feature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/ft_test_app_base.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::features::FirstPhaseRankBlueprint;
using search::features::FirstPhaseRankLookup;
using search::features::setup_search_features;
using search::fef::Blueprint;
using search::fef::BlueprintFactory;
using search::fef::ObjectStore;
using search::fef::test::IndexEnvironment;
using search::fef::test::DummyDependencyHandler;
using StringVector = std::vector<vespalib::string>;

constexpr feature_t unranked = std::numeric_limits<feature_t>::max();

struct FirstPhaseRankBlueprintTest : public ::testing::Test {
    BlueprintFactory factory;
    IndexEnvironment index_env;

    FirstPhaseRankBlueprintTest()
        : ::testing::Test(),
          factory(),
          index_env()
    {
        setup_search_features(factory);
    }

    ~FirstPhaseRankBlueprintTest() override;

    std::shared_ptr<Blueprint> make_blueprint() const {
        return factory.createBlueprint("firstPhaseRank");
    }

    void expect_setup_fail(const StringVector& params, const vespalib::string& exp_fail_msg) {
        auto blueprint = make_blueprint();
        DummyDependencyHandler deps(*blueprint);
        EXPECT_FALSE(blueprint->setup(index_env, params));
        EXPECT_EQ(exp_fail_msg, deps.fail_msg);
    }

    std::shared_ptr<Blueprint> expect_setup_succeed(const StringVector& params) {
        auto blueprint = make_blueprint();
        DummyDependencyHandler deps(*blueprint);
        EXPECT_TRUE(blueprint->setup(index_env, params));
        EXPECT_EQ(0, deps.input.size());
        EXPECT_EQ(StringVector({"score"}), deps.output);
        return blueprint;
    }
};

FirstPhaseRankBlueprintTest::~FirstPhaseRankBlueprintTest() = default;

TEST_F(FirstPhaseRankBlueprintTest, blueprint_can_be_created_from_factory)
{
    auto bp = make_blueprint();
    EXPECT_TRUE(bp);
    EXPECT_TRUE(dynamic_pointer_cast<FirstPhaseRankBlueprint>(bp));
}

TEST_F(FirstPhaseRankBlueprintTest, blueprint_setup_fails_when_parameter_list_is_not_empty)
{
    expect_setup_fail({"is"},
                      "The parameter list used for setting up rank feature firstPhaseRank is not valid: "
                      "Expected 0 parameter(s), but got 1");
}

TEST_F(FirstPhaseRankBlueprintTest, blueprint_setup_succeeds)
{
    expect_setup_succeed({});
}

TEST_F(FirstPhaseRankBlueprintTest, blueprint_can_prepare_shared_state)
{
    auto blueprint = expect_setup_succeed({});
    search::fef::test::QueryEnvironment query_env;
    ObjectStore store;
    EXPECT_EQ(nullptr, FirstPhaseRankLookup::get_mutable_shared_state(store));
    EXPECT_EQ(nullptr, FirstPhaseRankLookup::get_shared_state(store));
    blueprint->prepareSharedState(query_env, store);
    EXPECT_NE(nullptr, FirstPhaseRankLookup::get_mutable_shared_state(store));
    EXPECT_NE(nullptr, FirstPhaseRankLookup::get_shared_state(store));
}

TEST_F(FirstPhaseRankBlueprintTest, dump_features)
{
    FtTestAppBase::FT_DUMP_EMPTY(factory, "firstPhaseRank", index_env);
}

struct FirstPhaseRankExecutorTest : public ::testing::Test {
    BlueprintFactory factory;
    FtFeatureTest test;

    FirstPhaseRankExecutorTest()
        : ::testing::Test(),
          factory(),
          test(factory, "firstPhaseRank")
    {
        setup_search_features(factory);
    }
    ~FirstPhaseRankExecutorTest() override;
    void setup(std::vector<std::pair<uint32_t,uint32_t>> ranks) {
        EXPECT_TRUE(test.setup());
        auto* lookup = FirstPhaseRankLookup::get_mutable_shared_state(test.getQueryEnv().getObjectStore());
        ASSERT_NE(nullptr, lookup);
        for (auto& entry : ranks) {
            lookup->add(entry.first, entry.second);
        }
    }
    bool execute(feature_t exp_score, uint32_t docid) {
        return test.execute(exp_score, 0.000001, docid);
    }
};

FirstPhaseRankExecutorTest::~FirstPhaseRankExecutorTest() = default;

TEST_F(FirstPhaseRankExecutorTest, unranked_docid_gives_huge_output)
{
    setup({});
    EXPECT_TRUE(execute(unranked, 1));
}

TEST_F(FirstPhaseRankExecutorTest, ranked_docid_gives_expected_output)
{
    setup({{3, 5}, {7, 4}});
    EXPECT_TRUE(execute(unranked, 2));
    EXPECT_TRUE(execute(5, 3));
    EXPECT_TRUE(execute(unranked, 4));
    EXPECT_TRUE(execute(unranked, 5));
    EXPECT_TRUE(execute(unranked, 6));
    EXPECT_TRUE(execute(4, 7));
    EXPECT_TRUE(execute(unranked, 8));
}

GTEST_MAIN_RUN_ALL_TESTS()

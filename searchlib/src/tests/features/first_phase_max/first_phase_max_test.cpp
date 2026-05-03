// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/first_phase_max_feature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/test/ft_test_app_base.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::features::FirstPhaseMaxBlueprint;
using search::features::setup_search_features;
using search::fef::Blueprint;
using search::fef::BlueprintFactory;
using search::fef::ObjectStore;
using search::fef::test::DummyDependencyHandler;
using search::fef::test::IndexEnvironment;

using StringVector = std::vector<std::string>;

constexpr feature_t no_score = -std::numeric_limits<feature_t>::infinity();

// -------------- Blueprint test -----------------------

struct FirstPhaseMaxBlueprintTest : public ::testing::Test {
    BlueprintFactory factory;
    IndexEnvironment index_env;

    FirstPhaseMaxBlueprintTest() : ::testing::Test(), factory(), index_env() { setup_search_features(factory); }

    ~FirstPhaseMaxBlueprintTest() override = default;

    std::shared_ptr<Blueprint> make_blueprint() const { return factory.createBlueprint("firstPhaseMax"); }

    void expect_setup_fail(const StringVector& params, const std::string& exp_fail_msg) {
        auto                   blueprint = make_blueprint();
        DummyDependencyHandler deps(*blueprint);
        EXPECT_FALSE(blueprint->setup(index_env, params));
        EXPECT_EQ(exp_fail_msg, deps.fail_msg);
    }

    std::shared_ptr<Blueprint> expect_setup_succeed(const StringVector& params) {
        auto                   blueprint = make_blueprint();
        DummyDependencyHandler deps(*blueprint);
        EXPECT_TRUE(blueprint->setup(index_env, params));
        EXPECT_EQ(0, deps.input.size());
        EXPECT_EQ(StringVector({"score"}), deps.output);
        return blueprint;
    }
};

TEST_F(FirstPhaseMaxBlueprintTest, blueprint_can_be_created_from_factory) {
    auto bp = make_blueprint();
    EXPECT_TRUE(bp);
    EXPECT_TRUE(dynamic_pointer_cast<FirstPhaseMaxBlueprint>(bp));
}

TEST_F(FirstPhaseMaxBlueprintTest, blueprint_setup_fails_when_parameter_list_is_not_empty) {
    expect_setup_fail({"is"}, "The parameter list used for setting up rank feature firstPhaseMax is not valid: "
                              "Expected 0 parameter(s), but got 1");
}

TEST_F(FirstPhaseMaxBlueprintTest, blueprint_setup_succeeds) {
    expect_setup_succeed({});
}

TEST_F(FirstPhaseMaxBlueprintTest, blueprint_can_prepare_shared_state) {
    auto                                blueprint = expect_setup_succeed({});
    search::fef::test::QueryEnvironment query_env;
    ObjectStore                         store;
    EXPECT_EQ(nullptr, FirstPhaseMaxBlueprint::get_mutable_shared_state(store));
    EXPECT_EQ(nullptr, FirstPhaseMaxBlueprint::get_shared_state(store));
    blueprint->prepareSharedState(query_env, store);
    EXPECT_NE(nullptr, FirstPhaseMaxBlueprint::get_mutable_shared_state(store));
    EXPECT_NE(nullptr, FirstPhaseMaxBlueprint::get_shared_state(store));
}

TEST_F(FirstPhaseMaxBlueprintTest, dump_features) {
    FtTestAppBase::FT_DUMP_EMPTY(factory, "firstPhaseMax", index_env);
}

// -------------- Executor test -----------------------

struct FirstPhaseMaxExecutorTest : public ::testing::Test {
    BlueprintFactory factory;
    FtFeatureTest    test;

    FirstPhaseMaxExecutorTest() : ::testing::Test(), factory(), test(factory, "firstPhaseMax") {
        setup_search_features(factory);
    }

    ~FirstPhaseMaxExecutorTest() override = default;

    void setup_with_score(feature_t score) {
        EXPECT_TRUE(test.setup());
        auto* max = FirstPhaseMaxBlueprint::get_mutable_shared_state(test.getQueryEnv().getObjectStore());
        ASSERT_NE(nullptr, max);
        *max = score;
    }

    void setup_without_score() { EXPECT_TRUE(test.setup()); }

    bool execute(feature_t exp_score, uint32_t docid) { return test.execute(exp_score, 0.000001, docid); }
};

TEST_F(FirstPhaseMaxExecutorTest, returns_default_when_no_score_set) {
    setup_without_score();
    EXPECT_TRUE(execute(no_score, 1));
    EXPECT_TRUE(execute(no_score, 2));
    EXPECT_TRUE(execute(no_score, 3));
}

TEST_F(FirstPhaseMaxExecutorTest, returns_same_score_for_all_docids) {
    setup_with_score(42.0);
    EXPECT_TRUE(execute(42.0, 1));
    EXPECT_TRUE(execute(42.0, 2));
    EXPECT_TRUE(execute(42.0, 3));
    EXPECT_TRUE(execute(42.0, 100));
}

GTEST_MAIN_RUN_ALL_TESTS()

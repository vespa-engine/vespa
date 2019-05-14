// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/bm25_feature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::features;
using namespace search::fef;
using CollectionType = FieldInfo::CollectionType;
using StringVector = std::vector<vespalib::string>;

struct Bm25BlueprintTest : public ::testing::Test {
    BlueprintFactory factory;
    test::IndexEnvironment index_env;

    Bm25BlueprintTest()
        : factory(),
          index_env()
    {
        setup_search_features(factory);
        test::IndexEnvironmentBuilder builder(index_env);
        builder.addField(FieldType::INDEX, CollectionType::SINGLE, "is");
        builder.addField(FieldType::INDEX, CollectionType::ARRAY, "ia");
        builder.addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "iws");
        builder.addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "as");
    }

    Blueprint::SP make_blueprint() const {
        return factory.createBlueprint("bm25");
    }

    void expect_setup_fail(const StringVector& params) {
        auto blueprint = make_blueprint();
        test::DummyDependencyHandler deps(*blueprint);
        EXPECT_FALSE(blueprint->setup(index_env, params));
    }

    void expect_setup_succeed(const StringVector& params) {
        auto blueprint = make_blueprint();
        test::DummyDependencyHandler deps(*blueprint);
        EXPECT_TRUE(blueprint->setup(index_env, params));
        EXPECT_EQ(0, deps.input.size());
        EXPECT_EQ(StringVector({"score"}), deps.output);
    }
};

TEST_F(Bm25BlueprintTest, blueprint_can_be_created_from_factory)
{
    auto bp = factory.createBlueprint("bm25");
    EXPECT_TRUE(bp.get() != nullptr);
    EXPECT_TRUE(dynamic_cast<Bm25Blueprint*>(bp.get()) != nullptr);
}

TEST_F(Bm25BlueprintTest, blueprint_setup_fails_when_parameter_list_is_not_valid)
{
    expect_setup_fail({});           // wrong parameter number
    expect_setup_fail({"as"});       // 'as' is an attribute
    expect_setup_fail({"is", "ia"}); // wrong parameter number
}

TEST_F(Bm25BlueprintTest, blueprint_setup_succeeds_for_index_field)
{
    expect_setup_succeed({"is"});
    expect_setup_succeed({"ia"});
    expect_setup_succeed({"iws"});
}


struct Bm25ExecutorTest : public ::testing::Test {
    BlueprintFactory factory;
    FtFeatureTest test;
    test::MatchDataBuilder::UP match_data;

    Bm25ExecutorTest()
        : factory(),
          test(factory, "bm25(foo)"),
          match_data()
    {
        setup_search_features(factory);
        test.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        test.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
        test.getQueryEnv().getBuilder().addIndexNode({"foo"});
        test.getQueryEnv().getBuilder().addIndexNode({"foo"});
        test.getQueryEnv().getBuilder().addIndexNode({"bar"});

        EXPECT_TRUE(test.setup());

        match_data = test.createMatchDataBuilder();
        clear_term(0, 0);
        clear_term(1, 0);
        clear_term(2, 1);
    }
    bool execute(feature_t exp_score) {
        return test.execute(exp_score);
    }
    void clear_term(uint32_t term_id, uint32_t field_id) {
        auto* tfmd = match_data->getTermFieldMatchData(term_id, field_id);
        ASSERT_TRUE(tfmd != nullptr);
        tfmd->reset(123);
    }
    void prepare_term(uint32_t term_id, uint32_t field_id, uint16_t num_occs, uint16_t field_length, uint32_t doc_id = 1) {
        auto* tfmd = match_data->getTermFieldMatchData(term_id, field_id);
        ASSERT_TRUE(tfmd != nullptr);
        tfmd->reset(doc_id);
        tfmd->setNumOccs(num_occs);
        tfmd->setFieldLength(field_length);
    }

    feature_t get_score(feature_t num_occs, feature_t field_length) const {
        return (num_occs * 2.2) / (num_occs + (1.2 * (0.25 + 0.75 * field_length / 10.0)));
    }
};

TEST_F(Bm25ExecutorTest, score_is_calculated_for_a_single_term)
{
    prepare_term(0, 0, 3, 20);
    EXPECT_TRUE(execute(get_score(3.0, 20)));
}

TEST_F(Bm25ExecutorTest, score_is_calculated_for_multiple_terms)
{
    prepare_term(0, 0, 3, 20);
    prepare_term(1, 0, 7, 5);
    EXPECT_TRUE(execute(get_score(3.0, 20) + get_score(7.0, 5.0)));
}

TEST_F(Bm25ExecutorTest, term_that_does_not_match_document_is_ignored)
{
    prepare_term(0, 0, 3, 20);
    prepare_term(1, 0, 7, 5, 123);
    EXPECT_TRUE(execute(get_score(3.0, 20)));
}

TEST_F(Bm25ExecutorTest, term_searching_another_field_is_ignored)
{
    prepare_term(2, 1, 3, 20);
    EXPECT_TRUE(execute(0.0));
}

GTEST_MAIN_RUN_ALL_TESTS()

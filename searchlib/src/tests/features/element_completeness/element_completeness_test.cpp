// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/features/element_completeness_feature.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/ft_test_app_base.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using CollectionType = FieldInfo::CollectionType;

std::vector<vespalib::string> featureNamesFoo() {
    std::vector<vespalib::string> f;
    f.push_back("elementCompleteness(foo).completeness");
    f.push_back("elementCompleteness(foo).fieldCompleteness");
    f.push_back("elementCompleteness(foo).queryCompleteness");
    f.push_back("elementCompleteness(foo).elementWeight");
    return f;
}

const size_t TOTAL = 0;
const size_t FIELD = 1;
const size_t QUERY = 2;
const size_t WEIGHT = 3;

FtIndex indexFoo() {
    FtIndex idx;
    idx.field("foo");
    return idx;
}

struct BlueprintFactoryFixture {
    BlueprintFactory factory;
    BlueprintFactoryFixture() : factory()
    {
        setup_search_features(factory);
    }
};

struct IndexFixture {
    IndexEnvironment indexEnv;
    IndexFixture() : indexEnv()
    {
        IndexEnvironmentBuilder builder(indexEnv);
        builder.addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "foo");
        builder.addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "bar");
    }
};

struct FeatureDumpFixture : public IDumpFeatureVisitor {
    std::vector<vespalib::string> expect;
    size_t dumped;
    virtual void visitDumpFeature(const vespalib::string &name) override {
        EXPECT_LT(dumped, expect.size());
        EXPECT_EQ(expect[dumped++], name);
    }
    FeatureDumpFixture() : IDumpFeatureVisitor(), expect(featureNamesFoo()), dumped(0) {}
};

struct RankFixture : BlueprintFactoryFixture {
    Properties idxProps;
    RankFixture() : BlueprintFactoryFixture(), idxProps() {}
    void test(const vespalib::string &queryStr, const FtIndex &index,
              feature_t field, feature_t query, int32_t weight = 1, feature_t factor = 0.5,
              bool useStaleMatchData = false)
    {
        SCOPED_TRACE(queryStr);
        std::vector<vespalib::string> names = featureNamesFoo();
        ASSERT_TRUE(names.size() == 4u);
        RankResult expect;
        expect.addScore(names[TOTAL], field*factor + query*(1-factor))
            .addScore(names[FIELD], field).addScore(names[QUERY], query)
            .addScore(names[WEIGHT], (double)weight);
        FtFeatureTest ft(factory, names);
        ft.getIndexEnv().getProperties().import(idxProps);
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "bar");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "baz");
        FtTestAppBase::FT_SETUP(ft, FtUtil::toQuery(queryStr), index, 1);
        RankResult actual;
        EXPECT_TRUE(ft.executeOnly(actual, useStaleMatchData ? 2 : 1));
        for (size_t i = 0; i < names.size(); ++i) {
            SCOPED_TRACE(names[i].c_str());
            EXPECT_EQ(expect.getScore(names[i]), actual.getScore(names[i]));
        }
    }
};

TEST(ElementCompletenessTest, require_that_blueprint_can_be_created_from_factory)
{
    BlueprintFactoryFixture f;
    Blueprint::SP bp = f.factory.createBlueprint("elementCompleteness");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<ElementCompletenessBlueprint*>(bp.get()) != 0);
}

TEST(ElementCompletenessTest, require_that_appropriate_features_are_dumped)
{
    ElementCompletenessBlueprint f1;
    IndexFixture f2;
    FeatureDumpFixture f3;
    f1.visitDumpFeatures(f2.indexEnv, f3);
    EXPECT_EQ(f3.expect.size(), f3.dumped);
}

TEST(ElementCompletenessTest, require_that_setup_can_be_done_on_index_field)
{
    ElementCompletenessBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "foo")));
}

TEST(ElementCompletenessTest, require_that_setup_can_not_be_done_on_attribute_field)
{
    ElementCompletenessBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(bar)", f1.getBaseName().c_str()));
    EXPECT_TRUE(!((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "bar")));
}

TEST(ElementCompletenessTest, require_that_default_config_parameters_are_correct)
{
    ElementCompletenessBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "foo")));
    EXPECT_EQ(0u,  f1.getParams().fieldId);
    EXPECT_EQ(0.5, f1.getParams().fieldCompletenessImportance);
}

TEST(ElementCompletenessTest, require_that_blueprint_can_be_configured){
    ElementCompletenessBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    f2.indexEnv.getProperties().add("elementCompleteness(foo).fieldCompletenessImportance", "0.75");
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "foo")));
    EXPECT_EQ(0.75, f1.getParams().fieldCompletenessImportance);
}

TEST(ElementCompletenessTest, require_that_no_match_gives_zero_outputs)
{
    RankFixture f;
    f.test("x", indexFoo().element("y"), 0.0, 0.0, 0);
}

TEST(ElementCompletenessTest, require_that_perfect_match_gives_max_outputs)
{
    RankFixture f;
    f.test("x", indexFoo().element("x"), 1.0, 1.0);
}

TEST(ElementCompletenessTest, require_that_matching_half_the_field_gives_appropriate_outputs)
{
    RankFixture f;
    f.test("x", indexFoo().element("x y"), 0.5, 1.0);
    f.test("x y", indexFoo().element("x y a b"), 0.5, 1.0);
}

TEST(ElementCompletenessTest, require_that_matching_half_the_query_gives_appropriate_outputs)
{
    RankFixture f;
    f.test("x y", indexFoo().element("x"), 1.0, 0.5);
    f.test("x y a b", indexFoo().element("x y"), 1.0, 0.5);
}

TEST(ElementCompletenessTest, require_that_query_completeness_is_affected_by_query_term_weight)
{
    RankFixture f;
    f.test("x!300 y!100", indexFoo().element("y"), 1.0, 0.25);
    f.test("x!300 y!100", indexFoo().element("x"), 1.0, 0.75);
}

TEST(ElementCompletenessTest, require_that_field_completeness_is_not_affected_by_duplicate_field_tokens)
{
    RankFixture f;
    {
        SCOPED_TRACE("x y y y");
        f.test("x", indexFoo().element("x y y y"), 0.25, 1.00);
    }
    {
        SCOPED_TRACE("x x y y");
        f.test("x", indexFoo().element("x x y y"), 0.25, 1.00);
    }
    {
        SCOPED_TRACE("x x x y");
        f.test("x", indexFoo().element("x x x y"), 0.25, 1.00);
    }
    {
        SCOPED_TRACE("x x x x");
        f.test("x", indexFoo().element("x x x x"), 0.25, 1.00);
    }
}

TEST(ElementCompletenessTest, require_that_field_completeness_is_affected_by_duplicate_query_terms)
{
    RankFixture f;
    f.test("x", indexFoo().element("x x x x"), 0.25, 1.00);
    f.test("x x", indexFoo().element("x x x x"), 0.50, 1.00);
    f.test("x x x", indexFoo().element("x x x x"), 0.75, 1.00);
    f.test("x x x x", indexFoo().element("x x x x"), 1.00, 1.00);
}

TEST(ElementCompletenessTest, require_that_a_single_field_token_can_match_multiple_query_terms)
{
    RankFixture f;
    f.test("x", indexFoo().element("x"), 1.00, 1.00);
    f.test("x x", indexFoo().element("x"), 1.00, 1.00);
    f.test("x x x", indexFoo().element("x"), 1.00, 1.00);
    f.test("x x x x", indexFoo().element("x"), 1.00, 1.00);
}

TEST(ElementCompletenessTest, require_that_field_completeness_importance_can_be_adjusted)
{
    RankFixture f;
    f.idxProps.clear().add("elementCompleteness(foo).fieldCompletenessImportance", "0.1");
    {
        SCOPED_TRACE("0.1");
        f.test("x y", indexFoo().element("x"), 1.0, 0.5, 1, 0.1);
    }
    f.idxProps.clear().add("elementCompleteness(foo).fieldCompletenessImportance", "0.4");
    {
        SCOPED_TRACE("0.4");
        f.test("x y", indexFoo().element("x"), 1.0, 0.5, 1, 0.4);
    }
    f.idxProps.clear().add("elementCompleteness(foo).fieldCompletenessImportance", "0.7");
    {
        SCOPED_TRACE("0.7");
        f.test("x y", indexFoo().element("x"), 1.0, 0.5, 1, 0.7);
    }
}

TEST(ElementCompletenessTest, require_that_order_is_not_relevant)
{
    RankFixture f;
    f.test("x y a b", indexFoo().element("n x n y"), 0.5, 0.5);
    f.test("a b x y", indexFoo().element("y x n n"), 0.5, 0.5);
    f.test("a y x b", indexFoo().element("x n y n"), 0.5, 0.5);
}

TEST(ElementCompletenessTest, require_that_element_is_selected_based_on_completeness_times_element_weight)
{
    RankFixture f;
    f.idxProps.clear().add("elementCompleteness(foo).fieldCompletenessImportance", "0.0");
    {
        SCOPED_TRACE("10");
        f.test("x y a b", indexFoo().element("x", 39).element("y", 39).element("a b", 19).element("x y a b", 10), 1.0, 1.0, 10, 0.0);
    }
    {
        SCOPED_TRACE("21");
        f.test("x y a b", indexFoo().element("x", 39).element("y", 39).element("a b", 21).element("x y a b", 10), 1.0, 0.5, 21, 0.0);
    }
    {
        SCOPED_TRACE("45");
        f.test("x y a b", indexFoo().element("x", 39).element("y", 45).element("a b", 21).element("x y a b", 10), 1.0, 0.25, 45, 0.0);
    }
}

TEST(ElementCompletenessTest, require_that_stale_match_data_is_ignored)
{
    RankFixture f;
    f.test("x y a b", indexFoo().element("x y"), 0.0, 0.0, 0, 0.5, true);
}

GTEST_MAIN_RUN_ALL_TESTS()

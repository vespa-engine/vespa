// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/features/text_similarity_feature.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/ft_test_app_base.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <initializer_list>

using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using CollectionType = FieldInfo::CollectionType;

std::vector<vespalib::string> featureNamesFoo() {
    std::vector<vespalib::string> f;
    f.push_back("textSimilarity(foo).score");
    f.push_back("textSimilarity(foo).proximity");
    f.push_back("textSimilarity(foo).order");
    f.push_back("textSimilarity(foo).queryCoverage");
    f.push_back("textSimilarity(foo).fieldCoverage");
    return f;
}

const size_t SCORE     = 0;
const size_t PROXIMITY = 1;
const size_t ORDER     = 2;
const size_t QUERY     = 3;
const size_t FIELD     = 4;

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
        builder.addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        builder.addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "bar");
        builder.addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "baz");
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
    RankFixture() : BlueprintFactoryFixture() {}
    double get_feature(const vespalib::string &query, const FtIndex &index, size_t select,
                       bool useStaleMatchData = false)
    {
        std::vector<vespalib::string> names = featureNamesFoo();
        EXPECT_TRUE(names.size() == 5u);
        FtFeatureTest ft(factory, names);
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        FtTestAppBase::FT_SETUP(ft, FtUtil::toQuery(query), index, 1);
        RankResult actual;
        EXPECT_TRUE(ft.executeOnly(actual, useStaleMatchData ? 2 : 1));
        return actual.getScore(names[select]);
    }
};

double prox(uint32_t dist) {
    return (dist > 8) ? 0 : (1.0 - (((dist-1)/8.0) * ((dist-1)/8.0)));
}

double comb(std::initializer_list<double> values) {
    double sum = 0.0;
    for (double value: values) {
        sum += value;
    }
    return (sum/values.size());
}

double mix(double proximity, double order, double query, double field) {
    return (0.35 * proximity) + (0.15 * order) + (0.30 * query) + (0.20 * field);
}

TEST(TextSimilarityFeatureTest, require_that_blueprint_can_be_created_from_factory)
{
    BlueprintFactoryFixture f;
    Blueprint::SP bp = f.factory.createBlueprint("textSimilarity");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<TextSimilarityBlueprint*>(bp.get()) != 0);
}

TEST(TextSimilarityFeatureTest, require_that_appropriate_features_are_dumped)
{
    TextSimilarityBlueprint f1;
    IndexFixture f2;
    FeatureDumpFixture f3;
    f1.visitDumpFeatures(f2.indexEnv, f3);
    EXPECT_EQ(f3.expect.size(), f3.dumped);
}

TEST(TextSimilarityFeatureTest, require_that_setup_can_be_done_on_single_value_index_field)
{
    TextSimilarityBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "foo")));
}

TEST(TextSimilarityFeatureTest, require_that_setup_can_not_be_done_on_weighted_set_index_field)
{
    TextSimilarityBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(bar)", f1.getBaseName().c_str()));
    EXPECT_TRUE(!((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "bar")));
}

TEST(TextSimilarityFeatureTest, require_that_setup_can_not_be_done_on_single_value_attribute_field)
{
    TextSimilarityBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(baz)", f1.getBaseName().c_str()));
    EXPECT_TRUE(!((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "baz")));
}

TEST(TextSimilarityFeatureTest, require_that_no_match_gives_zero_outputs)
{
    RankFixture f1;
    EXPECT_EQ(0.0, f1.get_feature("x", indexFoo().element("y"), SCORE));
    EXPECT_EQ(0.0, f1.get_feature("x", indexFoo().element("y"), PROXIMITY));
    EXPECT_EQ(0.0, f1.get_feature("x", indexFoo().element("y"), ORDER));
    EXPECT_EQ(0.0, f1.get_feature("x", indexFoo().element("y"), QUERY));
    EXPECT_EQ(0.0, f1.get_feature("x", indexFoo().element("y"), FIELD));
}

TEST(TextSimilarityFeatureTest, require_that_minal_perfect_match_gives_max_outputs)
{
    RankFixture f1;
    EXPECT_EQ(1.0, f1.get_feature("x", indexFoo().element("x"), SCORE));
    EXPECT_EQ(1.0, f1.get_feature("x", indexFoo().element("x"), PROXIMITY));
    EXPECT_EQ(1.0, f1.get_feature("x", indexFoo().element("x"), ORDER));
    EXPECT_EQ(1.0, f1.get_feature("x", indexFoo().element("x"), QUERY));
    EXPECT_EQ(1.0, f1.get_feature("x", indexFoo().element("x"), FIELD));
}

TEST(TextSimilarityFeatureTest, require_that_larger_perfect_match_gives_max_outputs)
{
    RankFixture f1;
    EXPECT_EQ(1.0, f1.get_feature("a b c d e f g", indexFoo().element("a b c d e f g"), SCORE));
    EXPECT_EQ(1.0, f1.get_feature("a b c d e f g", indexFoo().element("a b c d e f g"), PROXIMITY));
    EXPECT_EQ(1.0, f1.get_feature("a b c d e f g", indexFoo().element("a b c d e f g"), ORDER));
    EXPECT_EQ(1.0, f1.get_feature("a b c d e f g", indexFoo().element("a b c d e f g"), QUERY));
    EXPECT_EQ(1.0, f1.get_feature("a b c d e f g", indexFoo().element("a b c d e f g"), FIELD));
}

TEST(TextSimilarityFeatureTest, require_that_extra_query_terms_reduces_order_but_not_proximity)
{
    RankFixture f1;
    EXPECT_EQ(1.0, f1.get_feature("x y", indexFoo().element("x"), PROXIMITY));
    EXPECT_EQ(1.0, f1.get_feature("x y y", indexFoo().element("x"), PROXIMITY));
    EXPECT_EQ(1.0, f1.get_feature("x y y y", indexFoo().element("x"), PROXIMITY));

    EXPECT_EQ(0.0, f1.get_feature("x y", indexFoo().element("x"), ORDER));
    EXPECT_EQ(0.0, f1.get_feature("x y y", indexFoo().element("x"), ORDER));
    EXPECT_EQ(0.0, f1.get_feature("x y y y", indexFoo().element("x"), ORDER));
}

TEST(TextSimilarityFeatureTest, require_that_extra_field_terms_reduces_proximity_but_not_order)
{
    RankFixture f1;
    EXPECT_EQ(prox(2), f1.get_feature("x", indexFoo().element("x y"), PROXIMITY));
    EXPECT_EQ(prox(3), f1.get_feature("x", indexFoo().element("x y y"), PROXIMITY));
    EXPECT_EQ(prox(4), f1.get_feature("x", indexFoo().element("x y y y"), PROXIMITY));

    EXPECT_EQ(1.0, f1.get_feature("x", indexFoo().element("x y"), ORDER));
    EXPECT_EQ(1.0, f1.get_feature("x", indexFoo().element("x y y"), ORDER));
    EXPECT_EQ(1.0, f1.get_feature("x", indexFoo().element("x y y y"), ORDER));
}

TEST(TextSimilarityFeatureTest, require_that_proximity_acts_as_expected)
{
    RankFixture f1;
    EXPECT_EQ(1.0, f1.get_feature("a b c d e", indexFoo().element("a b c d e"), PROXIMITY));
    EXPECT_EQ(comb({prox(2), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("a x b c d e"), PROXIMITY));
    EXPECT_EQ(comb({prox(3), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("a x x b c d e"), PROXIMITY));
    EXPECT_EQ(comb({prox(4), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("a x x x b c d e"), PROXIMITY));
    EXPECT_EQ(comb({prox(2), prox(2), prox(2), prox(2)}), f1.get_feature("a b c d e", indexFoo().element("a x b x c x d x e"), PROXIMITY));
    EXPECT_EQ(comb({prox(2), prox(2), prox(1), prox(3)}), f1.get_feature("a b c d e", indexFoo().element("a x b x c d x x e"), PROXIMITY));
}

TEST(TextSimilarityFeatureTest, require_that_field_order_does_not_affect_proximity_score)
{
    RankFixture f1;
    EXPECT_EQ(1.0, f1.get_feature("a b c d e", indexFoo().element("d c a b e"), PROXIMITY));
    EXPECT_EQ(comb({prox(2), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("d x c a b e"), PROXIMITY));
    EXPECT_EQ(comb({prox(3), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("d x x c a b e"), PROXIMITY));
    EXPECT_EQ(comb({prox(4), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("d x x x c a b e"), PROXIMITY));
    EXPECT_EQ(comb({prox(2), prox(2), prox(2), prox(2)}), f1.get_feature("a b c d e", indexFoo().element("d x c x a x b x e"), PROXIMITY));
    EXPECT_EQ(comb({prox(2), prox(2), prox(1), prox(3)}), f1.get_feature("a b c d e", indexFoo().element("d x c x a b x x e"), PROXIMITY));
}

TEST(TextSimilarityFeatureTest, require_that_order_score_acts_as_expected)
{
    RankFixture f1;
    EXPECT_EQ(1.0, f1.get_feature("a b c d e", indexFoo().element("a b c d e"), ORDER));
    EXPECT_EQ(comb({1.0, 1.0, 1.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("a b c e d"), ORDER));
    EXPECT_EQ(comb({0.0, 1.0, 1.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("b a c e d"), ORDER));
    EXPECT_EQ(comb({0.0, 1.0, 0.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("b a e d c"), ORDER));
    EXPECT_EQ(comb({0.0, 0.0, 0.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("e d c b a"), ORDER));
}

TEST(TextSimilarityFeatureTest, require_that_proximity_does_not_affect_order_score)
{
    RankFixture f1;
    EXPECT_EQ(1.0, f1.get_feature("a b c d e", indexFoo().element("a b c d e"), ORDER));
    EXPECT_EQ(comb({1.0, 1.0, 1.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("a x b x c x e x d"), ORDER));
    EXPECT_EQ(comb({0.0, 1.0, 1.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("b x a x c x e x d"), ORDER));
    EXPECT_EQ(comb({0.0, 1.0, 0.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("b x a x e x d x c"), ORDER));
    EXPECT_EQ(comb({0.0, 0.0, 0.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("e x d x c x b x a"), ORDER));
}

TEST(TextSimilarityFeatureTest, require_that_query_coverage_acts_as_expected)
{
    RankFixture f1;
    EXPECT_EQ(5.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b c d e"), QUERY));
    EXPECT_EQ(4.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b c d"), QUERY));
    EXPECT_EQ(3.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b c"), QUERY));
    EXPECT_EQ(2.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b"), QUERY));
    EXPECT_EQ(4.0/7.0, f1.get_feature("a!200 b!200 c d e", indexFoo().element("a b"), QUERY));
    EXPECT_EQ(2.0/7.0, f1.get_feature("a b c!500", indexFoo().element("a b"), QUERY));
    EXPECT_EQ(5.0/7.0, f1.get_feature("a b c!500", indexFoo().element("c"), QUERY));
}

TEST(TextSimilarityFeatureTest, require_that_field_coverage_acts_as_expected)
{
    RankFixture f1;
    EXPECT_EQ(5.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b c d e"), FIELD));
    EXPECT_EQ(4.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a x c d e"), FIELD));
    EXPECT_EQ(3.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b x x e"), FIELD));
    EXPECT_EQ(2.0/5.0, f1.get_feature("a b c d e", indexFoo().element("x x x d e"), FIELD));
}

TEST(TextSimilarityFeatureTest, require_that_first_unique_match_is_used_per_query_term)
{
    RankFixture f1;
    EXPECT_EQ(prox(3), f1.get_feature("a b", indexFoo().element("a a a b"), PROXIMITY));
    EXPECT_EQ(1.0, f1.get_feature("a b", indexFoo().element("a a a b"), ORDER));
    EXPECT_EQ(1.0, f1.get_feature("a b", indexFoo().element("a a a b"), QUERY));
    EXPECT_EQ(2.0/4.0, f1.get_feature("a b", indexFoo().element("a a a b"), FIELD));

    EXPECT_EQ(comb({prox(1), prox(2)}), f1.get_feature("a b a", indexFoo().element("a a a b"), PROXIMITY));
    EXPECT_EQ(0.5, f1.get_feature("a b a", indexFoo().element("a a a b"), ORDER));
    EXPECT_EQ(1.0, f1.get_feature("a b a", indexFoo().element("a a a b"), QUERY));
    EXPECT_EQ(3.0/4.0, f1.get_feature("a b a", indexFoo().element("a a a b"), FIELD));
}

TEST(TextSimilarityFeatureTest, require_that_overall_score_combines_individual_signals_appropriately)
{
    RankFixture f1;
    EXPECT_EQ(comb({prox(1), prox(3), prox(2)}), f1.get_feature("a b c d e", indexFoo().element("a c x x b x d"), PROXIMITY));
    EXPECT_EQ(comb({1.0, 0.0, 1.0}), f1.get_feature("a b c d e", indexFoo().element("a c x x b x d"), ORDER));
    EXPECT_EQ(4.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a c x x b x d"), QUERY));
    EXPECT_EQ(4.0/7.0, f1.get_feature("a b c d e", indexFoo().element("a c x x b x d"), FIELD));
    EXPECT_EQ(mix(comb({prox(1), prox(3), prox(2)}), comb({1.0, 0.0, 1.0}), 4.0/5.0, 4.0/7.0),
              f1.get_feature("a b c d e", indexFoo().element("a c x x b x d"), SCORE));
}

GTEST_MAIN_RUN_ALL_TESTS()

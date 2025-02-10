// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/elementwise_blueprint.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::features::ElementwiseBlueprint;
using search::features::setup_search_features;
using search::fef::Blueprint;
using search::fef::BlueprintFactory;
using search::fef::FeatureNameBuilder;
using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::test::DummyDependencyHandler;
using search::fef::test::IndexEnvironment;
using search::fef::test::IndexEnvironmentBuilder;
using CollectionType = FieldInfo::CollectionType;
using StringVector = std::vector<std::string>;

namespace {

std::string elementwise_feature_base_name = "elementwise";

std::string feature_name(const StringVector& params) {
    FeatureNameBuilder builder;
    builder.baseName(elementwise_feature_base_name);
    for (auto& param : params) {
        builder.parameter(param);
    }
    return builder.buildName();
}

}

class ElementwiseBlueprintTest : public ::testing::Test {
protected:
    BlueprintFactory factory;
    IndexEnvironment index_env;

    ElementwiseBlueprintTest();
    ~ElementwiseBlueprintTest() override;

    std::shared_ptr<Blueprint> make_blueprint() const;
    void expect_setup_fail(const StringVector& params);
    void expect_bm25_setup_succeed(const StringVector& params);
};

ElementwiseBlueprintTest::ElementwiseBlueprintTest()
    : ::testing::Test()
{
    setup_search_features(factory);
    IndexEnvironmentBuilder builder(index_env);
    builder.addField(FieldType::INDEX, CollectionType::SINGLE, "is");
    builder.addField(FieldType::INDEX, CollectionType::ARRAY, "ia");
    builder.addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "iws");
    builder.addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "as");
}

ElementwiseBlueprintTest::~ElementwiseBlueprintTest() = default;

std::shared_ptr<Blueprint>
ElementwiseBlueprintTest::make_blueprint() const
{
    return factory.createBlueprint(elementwise_feature_base_name);
}

void
ElementwiseBlueprintTest::expect_setup_fail(const StringVector& params)
{
    SCOPED_TRACE(feature_name(params));
    auto blueprint = make_blueprint();
    DummyDependencyHandler deps(*blueprint);
    EXPECT_FALSE(blueprint->setup(index_env, params));
    std::cerr << "fail msg: " << deps.fail_msg << std::endl;
}

void
ElementwiseBlueprintTest::expect_bm25_setup_succeed(const StringVector& params)
{
    SCOPED_TRACE(feature_name(params));
    auto blueprint = make_blueprint();
    DummyDependencyHandler deps(*blueprint);
    EXPECT_TRUE(blueprint->setup(index_env, params));
    EXPECT_EQ(0, deps.input.size());
    EXPECT_EQ(StringVector({"score"}), deps.output);
}

TEST_F(ElementwiseBlueprintTest, blueprint_can_be_created_from_factory)
{
    auto bp = factory.createBlueprint(elementwise_feature_base_name);
    EXPECT_TRUE(bp.get() != nullptr);
    EXPECT_TRUE(dynamic_cast<ElementwiseBlueprint*>(bp.get()) != nullptr);
}

TEST_F(ElementwiseBlueprintTest, blueprint_setup_fails_when_feature_is_unknown)
{
    expect_setup_fail({"unknownFeature", "x"});   // unknown feature

}

TEST_F(ElementwiseBlueprintTest, blueprint_setup_fails_when_parameter_list_is_not_valid)
{
    expect_setup_fail({});           // wrong parameter number
    expect_setup_fail({"bm25"});   // wrong parameter number
    expect_setup_fail({"bm25", "x"});   // wrong parameter number
    expect_setup_fail({"bm25(as)", "x"});       // 'as' is an attribute
    expect_setup_fail({"bm25(is,ia)", "x"}); // wrong parameter number
}

TEST_F(ElementwiseBlueprintTest, blueprint_setup_fails_when_cell_type_is_malformed)
{
    expect_setup_fail({"bm25(is)", "x", "complex"});
}

TEST_F(ElementwiseBlueprintTest, blueprint_setup_succeeds_for_index_field)
{
    expect_bm25_setup_succeed({"bm25(is)", "x"});
    expect_bm25_setup_succeed({"bm25(ia)", "x"});
    expect_bm25_setup_succeed({"bm25(iws)", "x"});
}

GTEST_MAIN_RUN_ALL_TESTS()

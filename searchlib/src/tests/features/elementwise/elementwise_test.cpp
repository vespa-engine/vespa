// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/features/elementwise_blueprint.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/test/ft_test_app_base.h>
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
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;
using vespalib::eval::TensorSpec;

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

} // namespace

class ElementwiseBlueprintTest : public ::testing::Test {
protected:
    BlueprintFactory factory;
    IndexEnvironment index_env;

    ElementwiseBlueprintTest();
    ~ElementwiseBlueprintTest() override;

    std::shared_ptr<Blueprint> make_blueprint() const;
    void expect_setup_fail(const StringVector& params);
    void expect_bm25_setup_succeed(const StringVector& params);
    void expect_matches_setup_succeed(const StringVector& params, const std::string& exp_type);
};

ElementwiseBlueprintTest::ElementwiseBlueprintTest() : ::testing::Test() {
    setup_search_features(factory);
    IndexEnvironmentBuilder builder(index_env);
    builder.addField(FieldType::INDEX, CollectionType::SINGLE, "is");
    builder.addField(FieldType::INDEX, CollectionType::ARRAY, "ia");
    builder.addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "iws");
    builder.addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "as");
    builder.addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "aa");
    builder.addField(FieldType::VIRTUAL, CollectionType::ARRAY, "va");
}

ElementwiseBlueprintTest::~ElementwiseBlueprintTest() = default;

std::shared_ptr<Blueprint> ElementwiseBlueprintTest::make_blueprint() const {
    return factory.createBlueprint(elementwise_feature_base_name);
}

void ElementwiseBlueprintTest::expect_setup_fail(const StringVector& params) {
    SCOPED_TRACE(feature_name(params));
    auto                   blueprint = make_blueprint();
    DummyDependencyHandler deps(*blueprint);
    EXPECT_FALSE(blueprint->setup(index_env, params));
    std::cerr << "fail msg: " << deps.fail_msg << std::endl;
}

void ElementwiseBlueprintTest::expect_bm25_setup_succeed(const StringVector& params) {
    SCOPED_TRACE(feature_name(params));
    auto                   blueprint = make_blueprint();
    DummyDependencyHandler deps(*blueprint);
    EXPECT_TRUE(blueprint->setup(index_env, params));
    EXPECT_EQ(0, deps.input.size());
    EXPECT_EQ(StringVector({"score"}), deps.output);
}

void ElementwiseBlueprintTest::expect_matches_setup_succeed(const StringVector& params, const std::string& exp_type) {
    SCOPED_TRACE(feature_name(params));
    auto                   blueprint = make_blueprint();
    DummyDependencyHandler deps(*blueprint);
    EXPECT_TRUE(blueprint->setup(index_env, params));
    EXPECT_EQ(0, deps.input.size());
    EXPECT_EQ(StringVector({"score"}), deps.output);
    ASSERT_EQ(1, deps.output_type.size());
    EXPECT_TRUE(deps.output_type[0].is_object());
    EXPECT_EQ(exp_type, deps.output_type[0].type().to_spec());
}

TEST_F(ElementwiseBlueprintTest, blueprint_can_be_created_from_factory) {
    auto bp = factory.createBlueprint(elementwise_feature_base_name);
    EXPECT_TRUE(bp.get() != nullptr);
    EXPECT_TRUE(dynamic_cast<ElementwiseBlueprint*>(bp.get()) != nullptr);
}

TEST_F(ElementwiseBlueprintTest, blueprint_setup_fails_when_feature_is_unknown) {
    expect_setup_fail({"unknownFeature", "x"}); // unknown feature
}

TEST_F(ElementwiseBlueprintTest, blueprint_setup_fails_when_parameter_list_is_not_valid) {
    expect_setup_fail({});                   // wrong parameter number
    expect_setup_fail({"bm25"});             // wrong parameter number
    expect_setup_fail({"bm25", "x"});        // wrong parameter number
    expect_setup_fail({"bm25(as)", "x"});    // 'as' is an attribute
    expect_setup_fail({"bm25(is,ia)", "x"}); // wrong parameter number
}

TEST_F(ElementwiseBlueprintTest, blueprint_setup_fails_when_cell_type_is_malformed) {
    expect_setup_fail({"bm25(is)", "x", "complex"});
}

TEST_F(ElementwiseBlueprintTest, blueprint_setup_succeeds_for_index_field) {
    expect_bm25_setup_succeed({"bm25(is)", "x"});
    expect_bm25_setup_succeed({"bm25(ia)", "x"});
    expect_bm25_setup_succeed({"bm25(iws)", "x"});
}

TEST_F(ElementwiseBlueprintTest, matches_blueprint_setup_succeeds_for_array_of_struct_or_map_field) {
    expect_matches_setup_succeed({"matches(va)", "x"}, "tensor(x{})");
    expect_matches_setup_succeed({"matches(va)", "x", "float"}, "tensor<float>(x{})");
    expect_matches_setup_succeed({"matches(va)", "y", "int8"}, "tensor<int8>(y{})");
    expect_matches_setup_succeed({"matches(va)", "x", "bfloat16"}, "tensor<bfloat16>(x{})");
}

TEST_F(ElementwiseBlueprintTest, matches_blueprint_setup_fails_when_parameter_list_is_not_valid) {
    expect_setup_fail({"matches", "x"});            // wrong parameter number
    expect_setup_fail({"matches(unknown)", "x"});   // unknown field
    expect_setup_fail({"matches(va,is)", "x"});     // wrong parameter number
    expect_setup_fail({"matches(va)", "x", "foo"}); // malformed cell type
}

TEST_F(ElementwiseBlueprintTest, matches_blueprint_setup_fails_when_field_is_not_array_of_struct_or_map) {
    expect_setup_fail({"matches(is)", "x"});
    expect_setup_fail({"matches(ia)", "x"});
    expect_setup_fail({"matches(iws)", "x"});
    expect_setup_fail({"matches(as)", "x"});
    expect_setup_fail({"matches(aa)", "x"});
}

class ElementwiseMatchesExecutorTest : public ::testing::Test {
protected:
    BlueprintFactory                        factory;
    FtFeatureTest                           test;
    search::fef::test::MatchDataBuilder::UP match_data;

    ElementwiseMatchesExecutorTest();
    ~ElementwiseMatchesExecutorTest() override;
    void add_virtual_term(const std::string& field_name) {
        auto* term = test.getQueryEnv().getBuilder().add_virtual_node(field_name);
        term->setUniqueId(test.getQueryEnv().getNumTerms() - 1);
    }
    uint32_t field_id(const std::string& field_name) { return test.getIndexEnv().getFieldByName(field_name)->id(); }
    TermFieldMatchData* tfmd(uint32_t term_id, const std::string& field_name) {
        return match_data->getTermFieldMatchData(term_id, field_id(field_name));
    }
    void add_term_without_handle(const std::string& field_name) {
        auto& terms = test.getQueryEnv().getTerms();
        terms.push_back(search::fef::SimpleTermData());
        terms.back().addField(field_id(field_name)); // handle is left as IllegalHandle
        terms.back().setUniqueId(terms.size() - 1);
    }
    void setup() {
        ASSERT_TRUE(test.setup());
        match_data = test.createMatchDataBuilder();
        tfmd(0, "va")->reset(123);
        tfmd(1, "va")->reset(123);
        tfmd(2, "other")->reset(123);
    }
    void prepare_term(uint32_t term_id, const std::string& field_name, const std::vector<uint32_t>& element_ids,
                      uint32_t doc_id = 1) {
        auto* md = tfmd(term_id, field_name);
        md->reset(doc_id);
        for (auto element_id : element_ids) {
            // a sameElement search iterator unpacks one position per matching element
            md->appendPosition(TermFieldMatchDataPosition(element_id, 0, 1, 1));
        }
    }
    static TensorSpec expected(const std::vector<uint32_t>& element_ids) {
        TensorSpec spec("tensor<float>(x{})");
        for (auto element_id : element_ids) {
            spec.add({{"x", std::to_string(element_id)}}, 1.0);
        }
        return spec;
    }
    TensorSpec execute(uint32_t doc_id = 1) {
        return vespalib::eval::spec_from_value(test.resolveObjectFeature(doc_id));
    }
};

ElementwiseMatchesExecutorTest::ElementwiseMatchesExecutorTest()
    : factory(), test(factory, feature_name({"matches(va)", "x", "float"})), match_data() {
    setup_search_features(factory);
    auto& builder = test.getIndexEnv().getBuilder();
    builder.addField(FieldType::VIRTUAL, CollectionType::ARRAY, "va");
    builder.addField(FieldType::VIRTUAL, CollectionType::ARRAY, "other");
    add_virtual_term("va");
    add_virtual_term("va");
    add_virtual_term("other");
}

ElementwiseMatchesExecutorTest::~ElementwiseMatchesExecutorTest() = default;

TEST_F(ElementwiseMatchesExecutorTest, empty_tensor_when_no_terms_match) {
    setup();
    EXPECT_EQ(expected({}), execute());
}

TEST_F(ElementwiseMatchesExecutorTest, matching_elements_are_collected_across_terms) {
    setup();
    prepare_term(0, "va", {1, 4});
    prepare_term(1, "va", {0, 4, 7});
    prepare_term(2, "other", {2, 3});
    EXPECT_EQ(expected({0, 1, 4, 7}), execute());
}

TEST_F(ElementwiseMatchesExecutorTest, match_data_for_other_docid_is_ignored) {
    setup();
    prepare_term(0, "va", {1, 4}, 2);
    prepare_term(1, "va", {5});
    EXPECT_EQ(expected({5}), execute());
}

TEST_F(ElementwiseMatchesExecutorTest, match_data_hidden_from_ranking_is_ignored) {
    setup();
    prepare_term(0, "va", {1, 4});
    prepare_term(1, "va", {5});
    tfmd(0, "va")->set_hidden_from_ranking();
    EXPECT_EQ(expected({5}), execute());
}

TEST_F(ElementwiseMatchesExecutorTest, term_field_without_handle_is_ignored) {
    add_term_without_handle("va");
    setup();
    prepare_term(0, "va", {1, 4});
    EXPECT_EQ(expected({1, 4}), execute());
}

GTEST_MAIN_RUN_ALL_TESTS()

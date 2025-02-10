// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/bm25_feature.h>
#include <vespa/searchlib/features/bm25_utils.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/ft_test_app_base.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <cmath>

using namespace search::features;
using namespace search::fef;
using namespace search::fef::objectstore;
using vespalib::eval::TensorSpec;
using CollectionType = FieldInfo::CollectionType;
using StringVector = std::vector<std::string>;

struct TestParam {
    std::string _name;
    std::string _tensor_type_spec;
    std::string _dimension_name;
    std::string _cell_type_name;
    bool        _elementwise;

    TestParam(const std::string& name, const std::string& tensor_type_spec,
              const std::string& dimension_name, const std::string& cell_type_name, bool elementwise);
    TestParam(const TestParam&);
    ~TestParam();
    std::string feature_base_name() const;
    std::string feature_name(const std::string& base_name, const StringVector& params) const;
    StringVector wrap_params(const StringVector& params) const;
    std::string feature_name(const StringVector& params) const;
    std::string average_length_suffix() const;
};

TestParam::TestParam(const std::string& name, const std::string& tensor_type_spec,
                     const std::string& dimension_name, const std::string& cell_type_name, bool elementwise)
    : _name(name),
      _tensor_type_spec(tensor_type_spec),
      _dimension_name(dimension_name),
      _cell_type_name(cell_type_name),
      _elementwise(elementwise)
{
}

TestParam::TestParam(const TestParam&) = default;
TestParam::~TestParam() = default;

std::string
TestParam::feature_base_name() const
{
    return _elementwise ? "elementwise" : "bm25";
}

std::string
TestParam::feature_name(const std::string& base_name, const StringVector& params) const
{
    FeatureNameBuilder builder;
    builder.baseName(base_name);
    for (auto& param : params) {
        builder.parameter(param);
    }
    return builder.buildName();
}

StringVector
TestParam::wrap_params(const StringVector& params) const
{
    if (!_elementwise) {
        return params;
    }
    StringVector wrapped_params;
    wrapped_params.emplace_back(feature_name("bm25", params));
    wrapped_params.emplace_back(_dimension_name);
    wrapped_params.emplace_back(_cell_type_name);
    return wrapped_params;
}

std::string
TestParam::feature_name(const StringVector& params) const
{
    return feature_name(feature_base_name(), wrap_params(params));
}

std::string
TestParam::average_length_suffix() const
{
    return _elementwise ? "averageElementLength" : "averageFieldLength";
}

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << param._name;
    return os;
}

auto test_values = ::testing::Values(
    TestParam("bm25", "error",
              "", "", false),
    TestParam("elementwiseBm25", "tensor(x{})",
              "x", "double", true),
    TestParam("elementwiseBm25float", "tensor<float>(x{})",
              "x", "float", true),
    TestParam("elementwiseBm25bfloat16", "tensor<bfloat16>(x{})",
              "x", "bfloat16", true),
    TestParam("elementwiseBm25int8", "tensor<int8>(x{})",
              "x", "int8", true));

struct Bm25BlueprintTest : public ::testing::TestWithParam<TestParam> {
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
    ~Bm25BlueprintTest() override;

    Blueprint::SP make_blueprint() const {
        return factory.createBlueprint(GetParam().feature_base_name());
    }
    std::string feature_name() const { return GetParam().feature_name({"is"}); }

    void expect_setup_fail(const StringVector& params) {
        auto blueprint = make_blueprint();
        test::DummyDependencyHandler deps(*blueprint);
        EXPECT_FALSE(blueprint->setup(index_env, GetParam().wrap_params(params)));
    }

    Blueprint::SP expect_setup_succeed(const StringVector& params) {
        auto blueprint = make_blueprint();
        test::DummyDependencyHandler deps(*blueprint);
        EXPECT_TRUE(blueprint->setup(index_env, GetParam().wrap_params(params)));
        EXPECT_EQ(0, deps.input.size());
        EXPECT_EQ(StringVector({"score"}), deps.output);
        return blueprint;
    }
};

Bm25BlueprintTest::~Bm25BlueprintTest() = default;


INSTANTIATE_TEST_SUITE_P(Bm25BlueprintMultiTest, Bm25BlueprintTest, test_values, testing::PrintToStringParamName());

TEST_P(Bm25BlueprintTest, blueprint_can_be_created_from_factory)
{
    auto bp = factory.createBlueprint("bm25");
    EXPECT_TRUE(bp.get() != nullptr);
    EXPECT_TRUE(dynamic_cast<Bm25Blueprint*>(bp.get()) != nullptr);
}

TEST_P(Bm25BlueprintTest, blueprint_setup_fails_when_parameter_list_is_not_valid)
{
    expect_setup_fail({});           // wrong parameter number
    expect_setup_fail({"as"});       // 'as' is an attribute
    expect_setup_fail({"is", "ia"}); // wrong parameter number
}

TEST_P(Bm25BlueprintTest, blueprint_setup_fails_when_k1_param_is_malformed)
{
    index_env.getProperties().add(feature_name() + ".k1", "malformed");
    expect_setup_fail({"is"});
}

TEST_P(Bm25BlueprintTest, blueprint_setup_fails_when_b_param_is_malformed)
{
    index_env.getProperties().add(feature_name() + ".b", "malformed");
    expect_setup_fail({"is"});
}

TEST_P(Bm25BlueprintTest, blueprint_setup_fails_when_avg_field_length_is_malformed)
{
    index_env.getProperties().add(feature_name() + "."  + GetParam().average_length_suffix(), "malformed");
    expect_setup_fail({"is"});
}

TEST_P(Bm25BlueprintTest, blueprint_setup_succeeds_for_index_field)
{
    expect_setup_succeed({"is"});
    expect_setup_succeed({"ia"});
    expect_setup_succeed({"iws"});
}

TEST_P(Bm25BlueprintTest, blueprint_can_prepare_shared_state_with_average_field_length)
{
    auto blueprint = expect_setup_succeed({"is"});
    test::QueryEnvironment query_env;
    query_env.get_field_length_info_map()["is"] =
        search::index::FieldLengthInfo(10.0, 10.0, 1);
    ObjectStore store;
    blueprint->prepareSharedState(query_env, store);
    EXPECT_DOUBLE_EQ(10.0, as_value<double>(*store.get(GetParam()._elementwise ? "bm25.ael.is" : "bm25.afl.is")));
}

TEST_P(Bm25BlueprintTest, dump_features_for_all_index_fields)
{
    StringList expected;
    if (!GetParam()._elementwise) {
        expected.add("bm25(is)").add("bm25(ia)").add("bm25(iws)");
    }
    FtTestAppBase::FT_DUMP(factory, GetParam().feature_base_name(), index_env,
                           expected);
}

struct Scorer {

    double avg_field_length;
    double k1_param;
    double b_param;

    Scorer() :
        avg_field_length(10),
        k1_param(1.2),
        b_param(0.75)
    {}

    feature_t score(feature_t num_occs, feature_t field_length, double inverse_doc_freq) const {
        return inverse_doc_freq * (num_occs * (1 + k1_param)) /
                (num_occs + (k1_param * ((1 - b_param) + b_param * field_length / avg_field_length)));
    }
};

struct Bm25ExecutorTest : public ::testing::TestWithParam<TestParam> {
    BlueprintFactory factory;
    FtFeatureTest test;
    test::MatchDataBuilder::UP match_data;
    Scorer scorer;
    static constexpr uint32_t total_doc_count = 100;

    Bm25ExecutorTest()
        : factory(),
          test(factory, GetParam().feature_name({"foo"})),
          match_data()
    {
        setup_search_features(factory);
        test.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        test.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
        add_query_term("foo", 25);
        add_query_term("foo", 35);
        add_query_term("bar", 45);
        test.getQueryEnv().getBuilder().set_avg_field_length("foo", 10);
    }
    ~Bm25ExecutorTest() override;
    void add_query_term(const std::string& field_name, uint32_t matching_doc_count) {
        auto* term = test.getQueryEnv().getBuilder().addIndexNode({field_name});
        term->field(0).setDocFreq(matching_doc_count, total_doc_count);
        term->setUniqueId(test.getQueryEnv().getNumTerms() - 1);
    }
    void setup() {
        EXPECT_TRUE(test.setup());
        match_data = test.createMatchDataBuilder();
        clear_term(0, 0);
        clear_term(1, 0);
        clear_term(2, 1);
    }
    std::string feature_name() const { return GetParam().feature_name({"foo"}); }
    bool execute(feature_t exp_score) {
        constexpr double epsilon = 0.000001;
        if (!GetParam()._elementwise) {
            return test.execute(exp_score, epsilon);
        }
        TensorSpec exp_spec(GetParam()._tensor_type_spec);
        if (exp_score != 0.0) {
            exp_spec.add({{"x", "0"}}, exp_score);
        }
        exp_spec = exp_spec.normalize();
        auto value = test.resolveObjectFeature();
        auto spec = spec_from_value(value.get());
        bool success = true;
        EXPECT_EQ(exp_spec, spec) << (success = false, "");
        return success;
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
        if (!GetParam()._elementwise) {
            tfmd->setNumOccs(num_occs);
            tfmd->setFieldLength(field_length);
        } else {
            for (uint32_t pos = 0; pos < num_occs; ++pos) {
                tfmd->appendPosition({0, pos, 1, field_length});
            }
        }
    }

    void append_term(uint32_t term_id, uint32_t field_id, uint32_t element_id, uint32_t element_length, uint16_t num_occs) {
        auto* tfmd = match_data->getTermFieldMatchData(term_id, field_id);
        ASSERT_TRUE(tfmd != nullptr);
        if (!GetParam()._elementwise) {
            // flatten
            tfmd->setNumOccs(tfmd->getNumOccs() + num_occs);
            tfmd->setFieldLength(tfmd->getFieldLength() + element_length);;
        } else {
            for (uint32_t pos = 0; pos < num_occs; ++pos) {
                tfmd->appendPosition({element_id, pos, 1, element_length});
            }
        }
    }

    double idf(uint32_t matching_doc_count) const {
        return Bm25Utils::calculate_inverse_document_frequency({matching_doc_count, total_doc_count});
    }

    feature_t score(feature_t num_occs, feature_t field_length, double inverse_doc_freq) const {
        return scorer.score(num_occs, field_length, inverse_doc_freq);
    }
};

Bm25ExecutorTest::~Bm25ExecutorTest() = default;

INSTANTIATE_TEST_SUITE_P(Bm25ExecutorMultiTest, Bm25ExecutorTest, test_values, testing::PrintToStringParamName());

TEST_P(Bm25ExecutorTest, score_is_calculated_for_a_single_term)
{
    setup();
    prepare_term(0, 0, 3, 20);
    EXPECT_TRUE(execute(score(3.0, 20, idf(25))));
}

TEST_P(Bm25ExecutorTest, score_is_calculated_for_multiple_terms)
{
    setup();
    prepare_term(0, 0, 3, 20);
    prepare_term(1, 0, 7, 5);
    EXPECT_TRUE(execute(score(3.0, 20, idf(25)) + score(7.0, 5.0, idf(35))));
}

TEST_P(Bm25ExecutorTest, term_that_does_not_match_document_is_ignored)
{
    setup();
    prepare_term(0, 0, 3, 20);
    uint32_t unmatched_doc_id = 123;
    prepare_term(1, 0, 7, 5, unmatched_doc_id);
    EXPECT_TRUE(execute(score(3.0, 20, idf(25))));
}

TEST_P(Bm25ExecutorTest, term_searching_another_field_is_ignored)
{
    setup();
    prepare_term(2, 1, 3, 20);
    EXPECT_TRUE(execute(0.0));
}

TEST_P(Bm25ExecutorTest, uses_average_field_length_from_shared_state_if_found)
{
    std::string key(GetParam()._elementwise ? "bm25.ael.foo" : "bm25.afl.foo");
    test.getQueryEnv().getObjectStore().add(key, std::make_unique<AnyWrapper<double>>(15));
    setup();
    prepare_term(0, 0, 3, 20);
    scorer.avg_field_length = 15;
    EXPECT_TRUE(execute(score(3.0, 20, idf(25))));
}

TEST_P(Bm25ExecutorTest, calculates_inverse_document_frequency)
{
    EXPECT_DOUBLE_EQ(std::log(1 + (99 + 0.5) / (1 + 0.5)),
                     Bm25Utils::calculate_inverse_document_frequency({1, 100}));
    EXPECT_DOUBLE_EQ(std::log(1 + (60 + 0.5) / (40 + 0.5)),
                     Bm25Utils::calculate_inverse_document_frequency({40, 100}));
    EXPECT_DOUBLE_EQ(std::log(1 + (0.5) / (100 + 0.5)),
                     Bm25Utils::calculate_inverse_document_frequency({100, 100}));
    EXPECT_DOUBLE_EQ(std::log(1 + (0.5) / (100 + 0.5)),
                    Bm25Utils::calculate_inverse_document_frequency({200, 100}));
    EXPECT_DOUBLE_EQ(std::log(1 + (99 + 0.5) / (1 + 0.5)),
                     Bm25Utils::calculate_inverse_document_frequency({0, 100}));
    EXPECT_DOUBLE_EQ(std::log(1 + (0.5) / (1 + 0.5)),
                     Bm25Utils::calculate_inverse_document_frequency({0, 0}));
}

TEST_P(Bm25ExecutorTest, k1_param_can_be_overriden)
{
    test.getIndexEnv().getProperties().add(feature_name() + ".k1", "2.5");
    setup();
    prepare_term(0, 0, 3, 20);
    scorer.k1_param = 2.5;
    EXPECT_TRUE(execute(score(3.0, 20, idf(25))));
}

TEST_P(Bm25ExecutorTest, b_param_can_be_overriden)
{
    test.getIndexEnv().getProperties().add(feature_name() + ".b", "0.9");
    setup();
    prepare_term(0, 0, 3, 20);
    scorer.b_param = 0.9;
    EXPECT_TRUE(execute(score(3.0, 20, idf(25))));
}

TEST_P(Bm25ExecutorTest, avg_field_length_can_be_overriden)
{
    test.getIndexEnv().getProperties().add(feature_name() + "." + GetParam().average_length_suffix(), "15");
    setup();
    prepare_term(0, 0, 3, 20);
    scorer.avg_field_length = 15;
    EXPECT_TRUE(execute(score(3.0, 20, idf(25))));
}

TEST_P(Bm25ExecutorTest, inverse_document_frequency_can_be_overriden_with_significance)
{
    test.getQueryEnv().getProperties().add("vespa.term.0.significance", "0.35");
    setup();
    prepare_term(0, 0, 3, 20);
    EXPECT_TRUE(execute(score(3.0, 20, 0.35)));
}

TEST_P(Bm25ExecutorTest, missing_interleaved_features_are_handled)
{
    setup();
    prepare_term(0, 0, 0, 0);
    EXPECT_TRUE(execute(score(GetParam()._elementwise ? 0.0 : 1.0, 10, idf(25))));
}

TEST_P(Bm25ExecutorTest, multiple_elements)
{
    setup();
    prepare_term(0, 0, 3, 20);
    append_term(0, 0, 7, 5, 2);
    if (!GetParam()._elementwise) {
        // flattened
        EXPECT_TRUE(execute(score(5, 25, idf(25))));
    } else {
        // One tensor cell for each matching element
        auto value = test.resolveObjectFeature();
        auto spec = spec_from_value(value.get());
        TensorSpec exp_spec(GetParam()._tensor_type_spec);
        exp_spec.add({{"x", "0"}}, score(3, 20, idf(25)));
        exp_spec.add({{"x", "7"}}, score(2, 5, idf(25)));
        exp_spec = exp_spec.normalize();
        EXPECT_EQ(exp_spec, spec);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()

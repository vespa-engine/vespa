// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/features/dotproductfeature.h>
#include <vespa/searchlib/test/imported_attribute_fixture.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/rankresult.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>

using namespace search;
using namespace search::attribute;
using namespace search::features;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::index;

using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;

template <typename T>
std::unique_ptr<fef::Anything> create_param(const std::string& param) {
    Properties props;
    props.add("foo", param);
    return std::make_unique<dotproduct::ArrayParam<T>>(props.lookup("foo"));
}

struct FixtureBase : ImportedAttributeFixture {

    BlueprintFactory _factory;
    FixtureBase() {
        DotProductBlueprint bp;
        _factory.addPrototype(bp.createInstance());
    }

    // Both array and wset attributes can have integer "key" types, so we let specific
    // sub-fixtures implement the mappings.
    virtual void setup_integer_mappings(BasicType int_type) = 0;

    void check_single_execution(feature_t expected,
                                const std::string& vector,
                                DocId doc_id,
                                std::unique_ptr<fef::Anything> pre_parsed = std::unique_ptr<fef::Anything>()) {
        RankResult result;
        result.addScore("dotProduct(" + imported_attr->getName() + ",vector)", expected);
        result.setEpsilon(0.00001);
        FtFeatureTest feature(_factory, result.getKeys());

        feature.getQueryEnv().getProperties().add("dotProduct.vector", vector);
        if (pre_parsed) {
            feature.getQueryEnv().getObjectStore().add("dotProduct.vector.vector", std::move(pre_parsed));
        }
        auto readGuard = imported_attr->makeReadGuard(false);
        const IAttributeVector *attr = readGuard->attribute();
        feature.getIndexEnv().getAttributeMap().add(std::move(readGuard));
        schema::CollectionType collection_type(
                (attr->getCollectionType() == attribute::CollectionType::ARRAY)
                ? schema::CollectionType::ARRAY : schema::CollectionType::WEIGHTEDSET);
        feature.getIndexEnv().getBuilder().addField(
                FieldType::ATTRIBUTE, collection_type, imported_attr->getName());
        ASSERT_TRUE(feature.setup());
        EXPECT_TRUE(feature.execute(result, doc_id));
    }

    template <typename BaseFullWidthType, typename PerTypeSetupFunctor>
    void check_executions(PerTypeSetupFunctor setup_func,
                          const std::vector<BasicType>& types,
                          feature_t expected,
                          const std::string& vector,
                          DocId doc_id,
                          const std::string& shared_param = "") {
        for (auto type : types) {
            setup_func(type);
            std::unique_ptr<fef::Anything> pre_parsed;
            if (!shared_param.empty()) {
                pre_parsed = create_param<BaseFullWidthType>(shared_param);
            }
            check_single_execution(expected, vector, doc_id, std::move(pre_parsed));
        }
    }

    void check_all_integer_executions(feature_t expected,
                                      const std::string& vector,
                                      DocId doc_id,
                                      const std::string& shared_param = "") {
        check_executions<int32_t>([this](auto int_type){ this->setup_integer_mappings(int_type); },
                                  {{BasicType::INT32}},
                                  expected, vector, doc_id, shared_param);
        check_executions<int64_t>([this](auto int_type){ this->setup_integer_mappings(int_type); },
                                  {{BasicType::INT64}},
                                  expected, vector, doc_id, shared_param);
    }
};

struct ArrayFixture : FixtureBase {

    ~ArrayFixture() override;
    template <typename T>
    void setup_integer_mappings_helper(BasicType int_type) {
        reset_with_array_value_reference_mappings<IntegerAttribute, T>(
                int_type,
                {{DocId(1), dummy_gid(3), DocId(3), {{2, 3, 5}}},
                 {DocId(3), dummy_gid(7), DocId(7), {{7, 11}}},
                 {DocId(5), dummy_gid(8), DocId(8), {{13, 17, 19, 23}}}});
    }
    void setup_integer_mappings(BasicType int_type) override {
        switch (int_type.type()) {
        case BasicType::INT32:
            setup_integer_mappings_helper<int32_t>(int_type);
            break;
        case BasicType::INT64:
            setup_integer_mappings_helper<int64_t>(int_type);
            break;
        default:
            FAIL() << "unexpected integer type";
        }
    }

    template <typename T>
    void setup_float_mappings_helper(BasicType float_type) {
        reset_with_array_value_reference_mappings<FloatingPointAttribute, T>(
                float_type,
                {{DocId(2), dummy_gid(4), DocId(4), {{2.2, 3.3, 5.5}}},
                 {DocId(4), dummy_gid(8), DocId(8), {{7.7, 11.11}}},
                 {DocId(6), dummy_gid(9), DocId(9), {{13.1, 17.2, 19.3, 23.4}}}});
    }
    void setup_float_mappings(BasicType float_type) {
        switch(float_type.type()) {
        case BasicType::FLOAT:
            setup_float_mappings_helper<float>(float_type);
            break;
        case BasicType::DOUBLE:
            setup_float_mappings_helper<double>(float_type);
            break;
        default:
            FAIL() << "unexpected float type";
        }
    }

    template <typename ExpectedType>
    void check_prepare_state_output(const vespalib::eval::Value & tensor, const ExpectedType & expected) {
        vespalib::nbostream os;
        encode_value(tensor, os);
        std::string input_vector(os.data(), os.size());
        check_prepare_state_output(".tensor", input_vector, expected);
    }

    template <typename ExpectedType>
    void check_prepare_state_output(const TensorSpec & spec, const ExpectedType & expected) {
        auto value = SimpleValue::from_spec(spec);
        check_prepare_state_output(*value, expected);
    }

    template <typename ExpectedType>
    void check_prepare_state_output(const std::string& input_vector, const ExpectedType & expected) {
        check_prepare_state_output("", input_vector, expected);
    }
    template <typename T>
    static void verify(const dotproduct::ArrayParam<T> & a, const dotproduct::ArrayParam<T> & b) {
        ASSERT_EQ(a.values.size(), b.values.size());
        for (size_t i(0); i < a.values.size(); i++) {
            if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
                EXPECT_FLOAT_EQ(a.values[i], b.values[i]);
            } else {
                EXPECT_EQ(a.values[i], b.values[i]);
            }
        }
    }
    template <typename ExpectedType>
    void check_prepare_state_output(const std::string & postfix, const std::string& input_vector, const ExpectedType & expected) {
        FtFeatureTest feature(_factory, "");
        DotProductBlueprint bp;
        DummyDependencyHandler dependency_handler(bp);
        ParameterList params({Parameter(ParameterType::ATTRIBUTE, imported_attr->getName()),
                              Parameter(ParameterType::STRING, "fancyvector")});

        feature.getIndexEnv().getAttributeMap().add(imported_attr->makeReadGuard(false));
        feature.getIndexEnv().getBuilder().addField(
                FieldType::ATTRIBUTE, schema::CollectionType::ARRAY, imported_attr->getName());

        bp.setup(feature.getIndexEnv(), params);
        feature.getQueryEnv().getProperties().add("dotProduct.fancyvector" + postfix, input_vector);
        auto& obj_store = feature.getQueryEnv().getObjectStore();
        bp.prepareSharedState(feature.getQueryEnv(), obj_store);
        // Resulting name is very implementation defined. But at least the tests will break if it changes.
        const auto* anything = obj_store.get("dotProduct.vector.fancyvector");
        ASSERT_TRUE(anything != nullptr);
        const auto* as_object = dynamic_cast<const ExpectedType*>(anything);
        ASSERT_TRUE(as_object != nullptr);
        verify(expected, *as_object);
    }

    void check_all_float_executions(feature_t expected, const std::string& vector,
                                    DocId doc_id, const std::string& shared_param = "")
    {
        check_executions<float>([this](auto float_type){ this->setup_float_mappings(float_type); },
                                 {{BasicType::FLOAT}},
                                 expected, vector, doc_id, shared_param);
        check_executions<double>([this](auto float_type){ this->setup_float_mappings(float_type); },
                                 {{BasicType::DOUBLE}},
                                 expected, vector, doc_id, shared_param);
    }
};

ArrayFixture::~ArrayFixture() = default;

TEST(ImportedDotProductTest, dense_i32_and_i64_array_dot_products_can_be_evaluated_with_string_parameter)
{
    ArrayFixture f;
    f.check_all_integer_executions(2*2 + 3*3 + 5*4, "[2 3 4]", DocId(1));
}

TEST(ImportedDotProductTest, dense_float_and_double_array_dot_products_can_be_evaluated_with_string_parameter)
{
    ArrayFixture f;
    f.check_all_float_executions(2.2*7.7 + 3.3*11.11 + 5.5*13.13, "[7.7 11.11 13.13]", DocId(2));
}

TEST(ImportedDotProductTest, zero_length_i32_and_i64_array_query_vector_evaluates_to_zero)
{
    ArrayFixture f;
    f.check_all_integer_executions(0, "[]", DocId(1));
}

TEST(ImportedDotProductTest, zero_length_float_and_double_array_query_vector_evaluates_to_zero)
{
    ArrayFixture f;
    f.check_all_float_executions(0, "[]", DocId(1));
}

TEST(ImportedDotProductTest, prepareSharedState_emits_i32_vector_for_i32_imported_attribute)
{
    ArrayFixture f;
    f.setup_integer_mappings(BasicType::INT32);
    f.check_prepare_state_output("[101 202 303]", dotproduct::ArrayParam<int32_t>({101, 202, 303}));
}

TEST(ImportedDotProductTest, prepareSharedState_emits_i64_vector_for_i64_imported_attribute)
{
    ArrayFixture f;
    f.setup_integer_mappings(BasicType::INT64);
    f.check_prepare_state_output("[101 202 303]", dotproduct::ArrayParam<int64_t>({101, 202, 303}));
}

TEST(ImportedDotProductTest, prepareSharedState_emits_float_vector_for_float_imported_attribute)
{
    ArrayFixture f;
    f.setup_float_mappings(BasicType::FLOAT);
    f.check_prepare_state_output("[10.1 20.2 30.3]", dotproduct::ArrayParam<float>({10.1, 20.2, 30.3}));
}

TEST(ImportedDotProductTest, prepareSharedState_emits_double_vector_for_double_imported_attribute)
{
    ArrayFixture f;
    f.setup_float_mappings(BasicType::DOUBLE);
    f.check_prepare_state_output("[10.1 20.2 30.3]", dotproduct::ArrayParam<double>({10.1, 20.2, 30.3}));
}

TEST(ImportedDotProductTest, prepareSharedState_handles_tensor_as_float_from_tensor_for_double_imported_attribute)
{
    ArrayFixture f;
    f.setup_float_mappings(BasicType::DOUBLE);
    auto tensor = TensorSpec::from_expr("tensor<float>(x[3]):[10.1,20.2,30.3]");
    f.check_prepare_state_output(tensor, dotproduct::ArrayParam<double>({10.1, 20.2, 30.3}));
}

TEST(ImportedDotProductTest, prepareSharedState_handles_tensor_as_double_from_tensor_for_double_imported_attribute)
{
    ArrayFixture f;
    f.setup_float_mappings(BasicType::DOUBLE);
    auto tensor = TensorSpec::from_expr("tensor(x[3]):[10.1,20.2,30.3]");
    f.check_prepare_state_output(tensor, dotproduct::ArrayParam<double>({10.1, 20.2, 30.3}));
}

TEST(ImportedDotProductTest, prepareSharedState_handles_tensor_as_float_from_tensor_for_float_imported_attribute)
{
    ArrayFixture f;
    f.setup_float_mappings(BasicType::FLOAT);
    auto tensor = TensorSpec::from_expr("tensor<float>(x[3]):[10.1,20.2,30.3]");
    f.check_prepare_state_output(tensor, dotproduct::ArrayParam<float>({10.1, 20.2, 30.3}));
}

TEST(ImportedDotProductTest, prepareSharedState_handles_tensor_as_double_from_tensor_for_float_imported_attribute)
{
    ArrayFixture f;
    f.setup_float_mappings(BasicType::FLOAT);
    auto tensor = TensorSpec::from_expr("tensor(x[3]):[10.1,20.2,30.3]");
    f.check_prepare_state_output(tensor, dotproduct::ArrayParam<float>({10.1, 20.2, 30.3}));
}

TEST(ImportedDotProductTest, dense_i32_and_i64_array_dot_product_can_be_evaluated_with_pre_parsed_object_parameter)
{
    ArrayFixture f;
    f.check_all_integer_executions(2*5 + 3*6 + 5*7, "[2 3 4]", DocId(1), "[5 6 7]"); // String input is ignored in favor of stored object
}

TEST(ImportedDotProductTest, dense_float_and_double_array_dot_product_can_be_evaluated_with_pre_parsed_object_parameter)
{
    ArrayFixture f;
    f.check_all_float_executions(2.2*7.7 + 3.3*11.11 + 5.5*13.13, "[2.0 3.0 4.0]", DocId(2), "[7.7 11.11 13.13]");
}

TEST(ImportedDotProductTest, sparse_i32_and_i64_array_dot_products_can_be_evaluated_with_string_parameter)
{
    ArrayFixture f;
    // Have an outlier index to prevent auto-flattening of sparse input
    f.check_all_integer_executions(2*13 + 4*23, "{0:2,3:4,50:100}", DocId(5));
}

TEST(ImportedDotProductTest, sparse_float_and_double_array_dot_products_can_be_evaluated_with_string_parameter)
{
    ArrayFixture f;
    f.check_all_float_executions(2.5*13.1 + 4.25*23.4, "{0:2.5,3:4.25,50:100.1}", DocId(6));
}

TEST(ImportedDotProductTest, sparse_i32_and_i64_array_dot_products_can_be_evaluated_with_pre_parsed_object_parameter)
{
    ArrayFixture f;
    // As before, we cheat a bit by having a different raw string vector than the pre-parsed vector.
    f.check_all_integer_executions(2*13 + 4*23, "[0 0 0]", DocId(5), "{0:2,3:4,50:100}");
}

TEST(ImportedDotProductTest, sparse_float_and_double_array_dot_products_can_be_evaluated_with_pre_parsed_object_parameter)
{
    ArrayFixture f;
    f.check_all_float_executions(2.5*13.1 + 4.25*23.4, "[0 0 0]", DocId(6), "{0:2.5,3:4.25,50:100.1}");
}

struct WsetFixture : FixtureBase {
    void setup_integer_mappings(BasicType int_type) override {
        const std::vector<WeightedInt> doc7_values({WeightedInt(200, 7), WeightedInt(300, 13)});
        reset_with_wset_value_reference_mappings<IntegerAttribute, WeightedInt>(
                int_type,
                {{DocId(3), dummy_gid(7), DocId(7), doc7_values}});
    }
    ~WsetFixture() override;
};

WsetFixture::~WsetFixture() = default;

TEST(ImportedDotProductTest, i32_and_i64_wset_dot_products_can_be_evaluated_with_string_parameter)
{
    WsetFixture f;
    f.check_all_integer_executions(21*7 + 19*13, "{200:21,300:19,999:1234}", DocId(3));
}

TEST(ImportedDotProductTest, string_wset_dot_products_can_be_evaluated_with_string_parameter)
{
    WsetFixture f;
    std::vector<WeightedString> doc7_values{{WeightedString("bar", 7), WeightedString("baz", 41)}};
    reset_with_wset_value_reference_mappings<StringAttribute, WeightedString>(
            f, BasicType::STRING,
            {{DocId(3), dummy_gid(7), DocId(7), doc7_values}});
    f.check_single_execution(5*7 + 3*41, "{bar:5,baz:3,nosuchkey:1234}", DocId(3));
}

TEST(ImportedDotProductTest, integer_enum_dot_products_can_be_evaluated_with_string_parameter)
{
    WsetFixture f;
    const std::vector<WeightedInt> doc7_values({WeightedInt(200, 7), WeightedInt(300, 13)});
    // We only check i32 here, since the enum (fast search) aspect is what matters here.
    reset_with_wset_value_reference_mappings<IntegerAttribute, WeightedInt>(
            f, BasicType::INT32,
            {{DocId(3), dummy_gid(7), DocId(7), doc7_values}},
            FastSearchConfig::ExplicitlyEnabled);
    f.check_single_execution(21*7 + 19*13, "{200:21,300:19,999:1234}", DocId(3));
}

// Observed TODOs out of scope for these tests:
// - pre-parsed vectors not currently implemented for weighted sets.
// - non-imported cases should also be tested for prepareSharedState.

GTEST_MAIN_RUN_ALL_TESTS()

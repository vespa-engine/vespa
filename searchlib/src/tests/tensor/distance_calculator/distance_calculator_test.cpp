// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/searchlib/test/attribute_builder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>
#include <iostream>

using namespace search::attribute::test;
using namespace search::attribute;
using namespace search::tensor;
using namespace vespalib::eval;

using search::AttributeVector;

using OptSubspace = std::optional<uint32_t>;

std::unique_ptr<Value> make_tensor(const vespalib::string& expr) {
    return SimpleValue::from_spec(TensorSpec::from_expr(expr));
}

class DistanceCalculatorTest : public testing::Test {
public:
    std::shared_ptr<AttributeVector> attr;

    DistanceCalculatorTest()
        : attr()
    {
    }

    void build_attribute(const vespalib::string& tensor_type,
                         const std::vector<vespalib::string>& tensor_values) {
        Config cfg(BasicType::TENSOR);
        cfg.setTensorType(ValueType::from_spec(tensor_type));
        cfg.set_distance_metric(DistanceMetric::Euclidean);
        attr = AttributeBuilder("doc_tensor", cfg).fill_tensor(tensor_values).get();
        ASSERT_TRUE(attr.get() != nullptr);
    }
    double calc_distance(uint32_t docid, const vespalib::string& query_tensor) {
        auto qt = make_tensor(query_tensor);
        auto calc = DistanceCalculator::make_with_validation(*attr, *qt);
        return calc->calc_with_limit(docid, std::numeric_limits<double>::max());
    }
    double calc_rawscore(uint32_t docid, const vespalib::string& query_tensor) {
        auto qt = make_tensor(query_tensor);
        auto calc = DistanceCalculator::make_with_validation(*attr, *qt);
        return calc->calc_raw_score(docid);
    }
    OptSubspace calc_closest_subspace(uint32_t docid, const vespalib::string& query_tensor) {
        auto qt = make_tensor(query_tensor);
        auto calc = DistanceCalculator::make_with_validation(*attr, *qt);
        return calc->calc_closest_subspace(attr->asTensorAttribute()->get_vectors(docid));
    }
    void make_calc_throws(const vespalib::string& query_tensor) {
        auto qt = make_tensor(query_tensor);
        DistanceCalculator::make_with_validation(*attr, *qt);
    }
};

constexpr double max_distance = std::numeric_limits<double>::max();

TEST_F(DistanceCalculatorTest, calculation_over_dense_tensor_attribute)
{
    build_attribute("tensor(y[2])", {"[3,10]", ""});
    vespalib::string qt = "tensor(y[2]):[7,10]";
    EXPECT_DOUBLE_EQ(16, calc_distance(1, qt));
    EXPECT_DOUBLE_EQ(max_distance, calc_distance(2, qt));
    EXPECT_EQ(OptSubspace(0), calc_closest_subspace(1, qt));

    EXPECT_DOUBLE_EQ(1.0/(1.0 + 4.0), calc_rawscore(1, qt));
    EXPECT_DOUBLE_EQ(0.0, calc_rawscore(2, qt));
    EXPECT_EQ(OptSubspace(), calc_closest_subspace(2, qt));
}

TEST_F(DistanceCalculatorTest, calculation_over_mixed_tensor_attribute)
{
    build_attribute("tensor(x{},y[2])",
                    {"{{x:\"a\",y:0}:3,{x:\"a\",y:1}:10,{x:\"b\",y:0}:5,{x:\"b\",y:1}:10}",
                     "{}", ""});
    vespalib::string qt_1 = "tensor(y[2]):[9,10]";
    vespalib::string qt_2 = "tensor(y[2]):[1,10]";
    EXPECT_DOUBLE_EQ(16, calc_distance(1, qt_1));
    EXPECT_DOUBLE_EQ(4, calc_distance(1, qt_2));
    EXPECT_EQ(OptSubspace(1), calc_closest_subspace(1, qt_1));
    EXPECT_EQ(OptSubspace(0), calc_closest_subspace(1, qt_2));
    EXPECT_DOUBLE_EQ(max_distance, calc_distance(2, qt_1));
    EXPECT_DOUBLE_EQ(max_distance, calc_distance(3, qt_1));
    EXPECT_EQ(OptSubspace(), calc_closest_subspace(2, qt_1));
    EXPECT_EQ(OptSubspace(), calc_closest_subspace(3, qt_1));

    EXPECT_DOUBLE_EQ(1.0/(1.0 + 4.0), calc_rawscore(1, qt_1));
    EXPECT_DOUBLE_EQ(1.0/(1.0 + 2.0), calc_rawscore(1, qt_2));
    EXPECT_DOUBLE_EQ(0.0, calc_rawscore(2, qt_1));
    EXPECT_DOUBLE_EQ(0.0, calc_rawscore(3, qt_1));
}

TEST_F(DistanceCalculatorTest, make_calculator_for_unsupported_types_throws)
{
    build_attribute("tensor(x{},y{})", {});
    EXPECT_THROW(make_calc_throws("tensor(y[2]):[9,10]"), vespalib::IllegalArgumentException);

    build_attribute("tensor(x{},y{},z[2])", {});
    EXPECT_THROW(make_calc_throws("tensor(z[2]):[9,10]"), vespalib::IllegalArgumentException);

    build_attribute("tensor(x{},y[2])", {});
    EXPECT_THROW(make_calc_throws("tensor(y{}):{{y:\"a\"}:9,{y:\"b\"}:10}"), vespalib::IllegalArgumentException);
    EXPECT_THROW(make_calc_throws("tensor(y[3]):[9,10]"), vespalib::IllegalArgumentException);
}

GTEST_MAIN_RUN_ALL_TESTS()


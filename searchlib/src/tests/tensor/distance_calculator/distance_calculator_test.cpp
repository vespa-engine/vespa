// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/searchlib/tensor/distance_function_factory.h>
#include <vespa/searchlib/test/attribute_builder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <iostream>

using namespace search::attribute::test;
using namespace search::attribute;
using namespace search::tensor;
using namespace vespalib::eval;

using search::AttributeVector;

class DistanceCalculatorTest : public testing::Test {
public:
    std::shared_ptr<AttributeVector> attr;
    const ITensorAttribute* attr_tensor;
    std::unique_ptr<DistanceFunction> func;

    DistanceCalculatorTest()
        : attr(),
          attr_tensor(),
          func(make_distance_function(DistanceMetric::Euclidean, CellType::DOUBLE))
    {
    }

    void build_attribute(const vespalib::string& tensor_type,
                         const std::vector<vespalib::string>& tensor_values) {
        Config cfg(BasicType::TENSOR);
        cfg.setTensorType(ValueType::from_spec(tensor_type));
        attr = AttributeBuilder("doc_tensor", cfg).fill_tensor(tensor_values).get();
        attr_tensor = dynamic_cast<const ITensorAttribute*>(attr.get());
        ASSERT_TRUE(attr_tensor != nullptr);
    }
    double calc_distance(uint32_t docid, const vespalib::string& query_tensor) {
        auto qv = SimpleValue::from_spec(TensorSpec::from_expr(query_tensor));
        DistanceCalculator calc(*attr_tensor, *qv, *func);
        return calc.calc_with_limit(docid, std::numeric_limits<double>::max());
    }
    double calc_rawscore(uint32_t docid, const vespalib::string& query_tensor) {
        auto qv = SimpleValue::from_spec(TensorSpec::from_expr(query_tensor));
        DistanceCalculator calc(*attr_tensor, *qv, *func);
        return calc.calc_raw_score(docid);
    }
};

constexpr double max_distance = std::numeric_limits<double>::max();

TEST_F(DistanceCalculatorTest, calculation_over_dense_tensor_attribute)
{
    build_attribute("tensor(y[2])", {"[3,10]", ""});
    vespalib::string qt = "tensor(y[2]):[7,10]";
    EXPECT_DOUBLE_EQ(16, calc_distance(1, qt));
    EXPECT_DOUBLE_EQ(max_distance, calc_distance(2, qt));

    EXPECT_DOUBLE_EQ(1.0/(1.0 + 4.0), calc_rawscore(1, qt));
    EXPECT_DOUBLE_EQ(0.0, calc_rawscore(2, qt));
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
    EXPECT_DOUBLE_EQ(max_distance, calc_distance(2, qt_1));
    EXPECT_DOUBLE_EQ(max_distance, calc_distance(3, qt_1));

    EXPECT_DOUBLE_EQ(1.0/(1.0 + 4.0), calc_rawscore(1, qt_1));
    EXPECT_DOUBLE_EQ(1.0/(1.0 + 2.0), calc_rawscore(1, qt_2));
    EXPECT_DOUBLE_EQ(0.0, calc_rawscore(2, qt_1));
    EXPECT_DOUBLE_EQ(0.0, calc_rawscore(3, qt_1));
}

GTEST_MAIN_RUN_ALL_TESTS()


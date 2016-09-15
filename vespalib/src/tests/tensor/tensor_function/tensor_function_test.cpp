// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/tensor/tensor_function.h>

using namespace vespalib::tensor;
using vespalib::eval::ValueType;

// Evaluation of tensor functions is tested in the 'tensor operations'
// test. This test checks type resolving and will be extended with
// inspectability of tensor functions when the implementation is
// extended to support it.

// Note: The 'tensor type' test verifies how tensor type dimensions
// may be combined. Specifically the fact that common dense dimensions
// must have the same size.

function::Node_UP invalid_value() {
    return function::input(ValueType::error_type(), 0);
}

function::Node_UP number_value() {
    return function::sum(function::input(ValueType::tensor_type({}), 0));
}

ValueType sparse_type(const std::vector<vespalib::string> &dimensions_in) {
    std::vector<ValueType::Dimension> dimensions;
    std::copy(dimensions_in.begin(), dimensions_in.end(), std::back_inserter(dimensions));
    return ValueType::tensor_type(dimensions);
}

ValueType dense_type(const std::vector<ValueType::Dimension> &dimensions_in) {
    return ValueType::tensor_type(dimensions_in);
}

function::Node_UP sparse_value(const std::vector<vespalib::string> &arg) {
    return function::input(sparse_type(arg), 0);
}

function::Node_UP dense_value(std::vector<ValueType::Dimension> arg) {
    return function::input(dense_type(arg), 0);
}

TensorAddress address(const TensorAddress::Elements &elems) {
    return TensorAddress(elems);
}


TEST("require that helper functions produce appropriate types") {
    EXPECT_TRUE(invalid_value()->type().is_error());
    EXPECT_EQUAL(number_value()->type(), ValueType::double_type());
    EXPECT_EQUAL(sparse_value({"x", "y"})->type(), sparse_type({"x", "y"}));
    EXPECT_EQUAL(dense_value({{"x", 10}})->type(), dense_type({{"x", 10}}));
}

TEST("require that input tensors preserves type") {
    EXPECT_EQUAL(sparse_type({"x", "y"}),
                 function::input(sparse_type({"x", "y"}), 0)->type());
    EXPECT_EQUAL(dense_type({{"x", 10}}),
                 function::input(dense_type({{"x", 10}}), 0)->type());
}

TEST("require that input tensors with non-tensor types are invalid") {
    EXPECT_TRUE(function::input(ValueType::error_type(), 0)->type().is_error());
}

TEST("require that sum of tensor gives number as result") {
    EXPECT_EQUAL(ValueType::double_type(), function::sum(sparse_value({}))->type());
    EXPECT_EQUAL(ValueType::double_type(), function::sum(dense_value({}))->type());
}

TEST("require that sum of number gives number as result") {
    EXPECT_EQUAL(ValueType::double_type(), function::sum(number_value())->type());
}

TEST("require that dimension sum removes the summed dimension") {
    EXPECT_EQUAL(sparse_type({"x", "y"}),
                 function::dimension_sum(sparse_value({"x", "y", "z"}), "z")->type());
    EXPECT_EQUAL(dense_type({{"y", 10}}),
                 function::dimension_sum(dense_value({{"x", 10}, {"y", 10}}), "x")->type());
}

TEST("require that dimension sum over non-existing dimension is invalid") {
    EXPECT_TRUE(function::dimension_sum(sparse_value({"x", "y", "z"}), "w")->type().is_error());
    EXPECT_TRUE(function::dimension_sum(dense_value({{"x", 10}, {"y", 10}}), "z")->type().is_error());
}

TEST("require that apply preserves tensor type") {
    EXPECT_EQUAL(sparse_type({"x", "y"}),
                 function::apply(sparse_value({"x", "y"}), 0)->type());
    EXPECT_EQUAL(dense_type({{"x", 10}}),
                 function::apply(dense_value({{"x", 10}}), 0)->type());
}

TEST("require that tensor add result has union of input dimensions") {   
    EXPECT_EQUAL(sparse_type({"x", "y", "z"}),
                 function::add(sparse_value({"x", "y"}),
                               sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(dense_type({{"x", 10}, {"y", 10}, {"z", 10}}),
                 function::add(dense_value({{"x", 10}, {"y", 10}}),
                               dense_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that tensor subtract result has union of input dimensions") {   
    EXPECT_EQUAL(sparse_type({"x", "y", "z"}),
                 function::subtract(sparse_value({"x", "y"}),
                                    sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(dense_type({{"x", 10}, {"y", 10}, {"z", 10}}),
                 function::subtract(dense_value({{"x", 10}, {"y", 10}}),
                                    dense_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that tensor multiply result has union of input dimensions") {   
    EXPECT_EQUAL(sparse_type({"x", "y", "z"}),
                 function::multiply(sparse_value({"x", "y"}),
                                    sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(dense_type({{"x", 10}, {"y", 10}, {"z", 10}}),
                 function::multiply(dense_value({{"x", 10}, {"y", 10}}),
                                    dense_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that tensor min result has union of input dimensions") {   
    EXPECT_EQUAL(sparse_type({"x", "y", "z"}),
                 function::min(sparse_value({"x", "y"}),
                               sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(dense_type({{"x", 10}, {"y", 10}, {"z", 10}}),
                 function::min(dense_value({{"x", 10}, {"y", 10}}),
                               dense_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that tensor max result has union of input dimensions") {   
    EXPECT_EQUAL(sparse_type({"x", "y", "z"}),
                 function::max(sparse_value({"x", "y"}),
                               sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(dense_type({{"x", 10}, {"y", 10}, {"z", 10}}),
                 function::max(dense_value({{"x", 10}, {"y", 10}}),
                               dense_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that tensor match result has intersection of input dimensions") {   
    EXPECT_EQUAL(sparse_type({"y"}),
                 function::match(sparse_value({"x", "y"}),
                                 sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(dense_type({{"y", 10}}),
                 function::match(dense_value({{"x", 10}, {"y", 10}}),
                                 dense_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that tensor operations on non-tensor types are invalid") {
    EXPECT_TRUE(function::sum(invalid_value())->type().is_error());
    EXPECT_TRUE(function::dimension_sum(invalid_value(), "x")->type().is_error());
    EXPECT_TRUE(function::dimension_sum(number_value(), "x")->type().is_error());
    EXPECT_TRUE(function::apply(invalid_value(), 0)->type().is_error());
    EXPECT_TRUE(function::apply(number_value(), 0)->type().is_error());
    EXPECT_TRUE(function::add(invalid_value(), invalid_value())->type().is_error());
    EXPECT_TRUE(function::add(number_value(), number_value())->type().is_error());
    EXPECT_TRUE(function::subtract(invalid_value(), invalid_value())->type().is_error());
    EXPECT_TRUE(function::subtract(number_value(), number_value())->type().is_error());
    EXPECT_TRUE(function::multiply(invalid_value(), invalid_value())->type().is_error());
    EXPECT_TRUE(function::multiply(number_value(), number_value())->type().is_error());
    EXPECT_TRUE(function::min(invalid_value(), invalid_value())->type().is_error());
    EXPECT_TRUE(function::min(number_value(), number_value())->type().is_error());
    EXPECT_TRUE(function::max(invalid_value(), invalid_value())->type().is_error());
    EXPECT_TRUE(function::max(number_value(), number_value())->type().is_error());
    EXPECT_TRUE(function::match(invalid_value(), invalid_value())->type().is_error());
    EXPECT_TRUE(function::match(number_value(), number_value())->type().is_error());
}

TEST_MAIN() { TEST_RUN_ALL(); }

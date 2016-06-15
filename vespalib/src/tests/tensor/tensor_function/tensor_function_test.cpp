// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/tensor/tensor_type.h>
#include <vespa/vespalib/tensor/tensor_function.h>

using namespace vespalib::tensor;

// Evaluation of tensor functions is tested in the 'tensor operations'
// test. This test checks type resolving and will be extended with
// inspectability of tensor functions when the implementation is
// extended to support it.

// Note: The 'tensor type' test verifies how tensor type dimensions
// may be combined. Specifically the fact that common dense dimensions
// must have the same size.

function::Node_UP invalid_value() {
    return function::input(TensorType::invalid(), 0);
}

function::Node_UP number_value() {
    return function::sum(function::input(TensorType::sparse({}), 0));
}

function::Node_UP sparse_value(const std::vector<vespalib::string> &arg) {
    return function::input(TensorType::sparse(arg), 0);
}

function::Node_UP dense_value(std::vector<TensorType::Dimension> arg) {
    return function::input(TensorType::dense(std::move(arg)), 0);
}

TensorAddress address(const TensorAddress::Elements &elems) {
    return TensorAddress(elems);
}

TEST("require that helper functions produce appropriate types") {
    EXPECT_TRUE(!invalid_value()->type().is_valid());
    EXPECT_EQUAL(number_value()->type(), TensorType::number());
    EXPECT_EQUAL(sparse_value({"x", "y"})->type(), TensorType::sparse({"x", "y"}));
    EXPECT_EQUAL(dense_value({{"x", 10}})->type(), TensorType::dense({{"x", 10}}));
}

TEST("require that input tensors preserves type") {
    EXPECT_EQUAL(TensorType::sparse({"x", "y"}),
                 function::input(TensorType::sparse({"x", "y"}), 0)->type());
    EXPECT_EQUAL(TensorType::dense({{"x", 10}}),
                 function::input(TensorType::dense({{"x", 10}}), 0)->type());
}

TEST("require that input tensors with non-tensor types are invalid") {
    EXPECT_TRUE(!function::input(TensorType::invalid(), 0)->type().is_valid());
    EXPECT_TRUE(!function::input(TensorType::number(), 0)->type().is_valid());
}

TEST("require that sum of tensor gives number as result") {
    EXPECT_EQUAL(TensorType::number(), function::sum(sparse_value({}))->type());
    EXPECT_EQUAL(TensorType::number(), function::sum(dense_value({}))->type());
}

TEST("require that dimension sum removes the summed dimension") {
    EXPECT_EQUAL(TensorType::sparse({"x", "y"}),
                 function::dimension_sum(sparse_value({"x", "y", "z"}), "z")->type());
    EXPECT_EQUAL(TensorType::dense({{"y", 10}}),
                 function::dimension_sum(dense_value({{"x", 10}, {"y", 10}}), "x")->type());
}

TEST("require that dimension sum over non-existing dimension is invalid") {
    EXPECT_TRUE(!function::dimension_sum(sparse_value({"x", "y", "z"}), "w")->type().is_valid());
    EXPECT_TRUE(!function::dimension_sum(dense_value({{"x", 10}, {"y", 10}}), "z")->type().is_valid());
}

TEST("require that apply preserves tensor type") {
    EXPECT_EQUAL(TensorType::sparse({"x", "y"}),
                 function::apply(sparse_value({"x", "y"}), 0)->type());
    EXPECT_EQUAL(TensorType::dense({{"x", 10}}),
                 function::apply(dense_value({{"x", 10}}), 0)->type());
}

TEST("require that tensor add result has union of input dimensions") {   
    EXPECT_EQUAL(TensorType::sparse({"x", "y", "z"}),
                 function::add(sparse_value({"x", "y"}),
                               sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(TensorType::sparse({{"x", 10}, {"y", 10}, {"z", 10}}),
                 function::add(sparse_value({{"x", 10}, {"y", 10}}),
                               sparse_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that tensor subtract result has union of input dimensions") {   
    EXPECT_EQUAL(TensorType::sparse({"x", "y", "z"}),
                 function::subtract(sparse_value({"x", "y"}),
                                    sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(TensorType::sparse({{"x", 10}, {"y", 10}, {"z", 10}}),
                 function::subtract(sparse_value({{"x", 10}, {"y", 10}}),
                                    sparse_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that tensor multiply result has union of input dimensions") {   
    EXPECT_EQUAL(TensorType::sparse({"x", "y", "z"}),
                 function::multiply(sparse_value({"x", "y"}),
                                    sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(TensorType::sparse({{"x", 10}, {"y", 10}, {"z", 10}}),
                 function::multiply(sparse_value({{"x", 10}, {"y", 10}}),
                                    sparse_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that tensor min result has union of input dimensions") {   
    EXPECT_EQUAL(TensorType::sparse({"x", "y", "z"}),
                 function::min(sparse_value({"x", "y"}),
                               sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(TensorType::sparse({{"x", 10}, {"y", 10}, {"z", 10}}),
                 function::min(sparse_value({{"x", 10}, {"y", 10}}),
                               sparse_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that tensor max result has union of input dimensions") {   
    EXPECT_EQUAL(TensorType::sparse({"x", "y", "z"}),
                 function::max(sparse_value({"x", "y"}),
                               sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(TensorType::sparse({{"x", 10}, {"y", 10}, {"z", 10}}),
                 function::max(sparse_value({{"x", 10}, {"y", 10}}),
                               sparse_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that tensor match result has intersection of input dimensions") {   
    EXPECT_EQUAL(TensorType::sparse({"y"}),
                 function::match(sparse_value({"x", "y"}),
                                 sparse_value({"y", "z"}))->type());
    EXPECT_EQUAL(TensorType::sparse({{"y", 10}}),
                 function::match(sparse_value({{"x", 10}, {"y", 10}}),
                                 sparse_value({{"y", 10}, {"z", 10}}))->type());
}

TEST("require that sparse and dense tensors cannot be directly combined") {
    EXPECT_TRUE(!function::add(sparse_value({}), dense_value({}))->type().is_valid());
    EXPECT_TRUE(!function::add(sparse_value({}), dense_value({}))->type().is_valid());
    EXPECT_TRUE(!function::subtract(sparse_value({}), dense_value({}))->type().is_valid());
    EXPECT_TRUE(!function::subtract(sparse_value({}), dense_value({}))->type().is_valid());
    EXPECT_TRUE(!function::multiply(sparse_value({}), dense_value({}))->type().is_valid());
    EXPECT_TRUE(!function::multiply(sparse_value({}), dense_value({}))->type().is_valid());
    EXPECT_TRUE(!function::min(sparse_value({}), dense_value({}))->type().is_valid());
    EXPECT_TRUE(!function::min(sparse_value({}), dense_value({}))->type().is_valid());
    EXPECT_TRUE(!function::max(sparse_value({}), dense_value({}))->type().is_valid());
    EXPECT_TRUE(!function::max(sparse_value({}), dense_value({}))->type().is_valid());
    EXPECT_TRUE(!function::match(sparse_value({}), dense_value({}))->type().is_valid());
    EXPECT_TRUE(!function::match(sparse_value({}), dense_value({}))->type().is_valid());
}

TEST("require that tensor operations on non-tensor types are invalid") {
    EXPECT_TRUE(!function::sum(invalid_value())->type().is_valid());
    EXPECT_TRUE(!function::sum(number_value())->type().is_valid());
    EXPECT_TRUE(!function::dimension_sum(invalid_value(), "x")->type().is_valid());
    EXPECT_TRUE(!function::dimension_sum(number_value(), "x")->type().is_valid());
    EXPECT_TRUE(!function::apply(invalid_value(), 0)->type().is_valid());
    EXPECT_TRUE(!function::apply(number_value(), 0)->type().is_valid());
    EXPECT_TRUE(!function::add(invalid_value(), invalid_value())->type().is_valid());
    EXPECT_TRUE(!function::add(number_value(), number_value())->type().is_valid());
    EXPECT_TRUE(!function::subtract(invalid_value(), invalid_value())->type().is_valid());
    EXPECT_TRUE(!function::subtract(number_value(), number_value())->type().is_valid());
    EXPECT_TRUE(!function::multiply(invalid_value(), invalid_value())->type().is_valid());
    EXPECT_TRUE(!function::multiply(number_value(), number_value())->type().is_valid());
    EXPECT_TRUE(!function::min(invalid_value(), invalid_value())->type().is_valid());
    EXPECT_TRUE(!function::min(number_value(), number_value())->type().is_valid());
    EXPECT_TRUE(!function::max(invalid_value(), invalid_value())->type().is_valid());
    EXPECT_TRUE(!function::max(number_value(), number_value())->type().is_valid());
    EXPECT_TRUE(!function::match(invalid_value(), invalid_value())->type().is_valid());
    EXPECT_TRUE(!function::match(number_value(), number_value())->type().is_valid());
}

TEST_MAIN() { TEST_RUN_ALL(); }

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::Slime;
using vespalib::eval::TensorSpec;

auto my_nan = std::numeric_limits<double>::quiet_NaN();
auto my_neg_inf = (-1.0/0.0);
auto my_inf = (1.0/0.0);

TEST(TensorSpecTest, require_that_a_tensor_spec_can_be_converted_to_and_from_slime)
{
    TensorSpec spec("tensor(x[2],y{})");
    spec.add({{"x", 0}, {"y", "xxx"}}, 1.0)
        .add({{"x", 0}, {"y", "yyy"}}, 2.0)
        .add({{"x", 1}, {"y", "xxx"}}, 3.0)
        .add({{"x", 1}, {"y", "yyy"}}, 4.0);
    Slime slime;
    spec.to_slime(slime.setObject());
    fprintf(stderr, "tensor spec as slime: \n%s\n", slime.get().toString().c_str());
    EXPECT_EQ(TensorSpec::from_slime(slime.get()), spec);
}

TEST(TensorSpecTest, require_that_a_tensor_spec_can_be_converted_to_and_from_an_expression)
{
    TensorSpec spec("tensor<float>(x[2],y{})");
    spec.add({{"x", 0}, {"y", "xxx"}}, 1.0)
        .add({{"x", 0}, {"y", "yyy"}}, 2.0)
        .add({{"x", 1}, {"y", "xxx"}}, 3.0)
        .add({{"x", 1}, {"y", "yyy"}}, 4.0);
    std::string expr = spec.to_expr();
    fprintf(stderr, "expr: \n%s\n", expr.c_str());
    EXPECT_EQ(TensorSpec::from_expr(expr), spec);
}

TEST(TensorSpecTest, require_that_nan_inf_neginf_cells_get_converted_to_valid_expressions)
{
    TensorSpec spec("tensor<float>(x[2],y{})");
    spec.add({{"x", 0}, {"y", "xxx"}}, my_nan)
        .add({{"x", 0}, {"y", "yyy"}}, my_nan)
        .add({{"x", 1}, {"y", "xxx"}}, my_neg_inf)
        .add({{"x", 1}, {"y", "yyy"}}, my_inf);
    std::string expr = spec.to_expr();
    fprintf(stderr, "expr: \n%s\n", expr.c_str());
    EXPECT_EQ(TensorSpec::from_expr(expr), spec);
}

TEST(TensorSpecTest, require_that_tensor_specs_can_be_diffed)
{
    TensorSpec expect("tensor(x[2],y{})");
    expect.add({{"x", 0}, {"y", "xxx"}}, 1.5)
        .add({{"x", 0}, {"y", "yyy"}}, 2.0)
        .add({{"x", 1}, {"y", "yyy"}}, 4.0);
    TensorSpec actual("tensor<float>(x[2],y{})");
    actual.add({{"x", 0}, {"y", "xxx"}}, 1.0)
        .add({{"x", 0}, {"y", "yyy"}}, 2.0)
        .add({{"x", 1}, {"y", "xxx"}}, 3.0);
    EXPECT_TRUE(!(expect == actual));
    fprintf(stderr, "tensor spec diff:\n%s", TensorSpec::diff(expect, "expect", actual, "actual").c_str());
}

GTEST_MAIN_RUN_ALL_TESTS()

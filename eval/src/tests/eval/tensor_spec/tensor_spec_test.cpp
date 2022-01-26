// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/vespalib/data/slime/slime.h>

using vespalib::Slime;
using vespalib::eval::TensorSpec;

auto my_nan = std::numeric_limits<double>::quiet_NaN();
auto my_neg_inf = (-1.0/0.0);
auto my_inf = (1.0/0.0);

TEST("require that a tensor spec can be converted to and from slime") {
    TensorSpec spec("tensor(x[2],y{})");
    spec.add({{"x", 0}, {"y", "xxx"}}, 1.0)
        .add({{"x", 0}, {"y", "yyy"}}, 2.0)
        .add({{"x", 1}, {"y", "xxx"}}, 3.0)
        .add({{"x", 1}, {"y", "yyy"}}, 4.0);
    Slime slime;
    spec.to_slime(slime.setObject());
    fprintf(stderr, "tensor spec as slime: \n%s\n", slime.get().toString().c_str());
    EXPECT_EQUAL(TensorSpec::from_slime(slime.get()), spec);
}

TEST("require that a tensor spec can be converted to and from an expression") {
    TensorSpec spec("tensor<float>(x[2],y{})");
    spec.add({{"x", 0}, {"y", "xxx"}}, 1.0)
        .add({{"x", 0}, {"y", "yyy"}}, 2.0)
        .add({{"x", 1}, {"y", "xxx"}}, 3.0)
        .add({{"x", 1}, {"y", "yyy"}}, 4.0);
    vespalib::string expr = spec.to_expr();
    fprintf(stderr, "expr: \n%s\n", expr.c_str());
    EXPECT_EQUAL(TensorSpec::from_expr(expr), spec);
}

TEST("require that nan/inf/-inf cells get converted to valid expressions") {
    TensorSpec spec("tensor<float>(x[2],y{})");
    spec.add({{"x", 0}, {"y", "xxx"}}, my_nan)
        .add({{"x", 0}, {"y", "yyy"}}, my_nan)
        .add({{"x", 1}, {"y", "xxx"}}, my_neg_inf)
        .add({{"x", 1}, {"y", "yyy"}}, my_inf);
    vespalib::string expr = spec.to_expr();
    fprintf(stderr, "expr: \n%s\n", expr.c_str());
    EXPECT_EQUAL(TensorSpec::from_expr(expr), spec);
}

TEST("require that tensor specs can be diffed") {
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

TEST_MAIN() { TEST_RUN_ALL(); }

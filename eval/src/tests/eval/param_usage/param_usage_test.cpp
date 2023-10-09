// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/param_usage.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/vespalib/test/insertion_operators.h>

using vespalib::approx_equal;
using namespace vespalib::eval;

struct List {
    std::vector<double> list;
    List(std::vector<double> list_in) : list(std::move(list_in)) {}
    bool operator==(const List &rhs) const {
        if (list.size() != rhs.list.size()) {
            return false;
        }
        for (size_t i = 0; i < list.size(); ++i) {
            if (!approx_equal(list[i], rhs.list[i])) {
                return false;
            }
        }
        return true;
    }
};

std::ostream &operator<<(std::ostream &out, const List &list) {
    return out << list.list;
}

TEST("require that simple expression has appropriate parameter usage") {
    std::vector<vespalib::string> params({"x", "y", "z"});
    auto function = Function::parse(params, "(x+y)*y");
    EXPECT_EQUAL(List(count_param_usage(*function)), List({1.0, 2.0, 0.0}));
    EXPECT_EQUAL(List(check_param_usage(*function)), List({1.0, 1.0, 0.0}));
}

TEST("require that if children have 50% probability each by default") {
    std::vector<vespalib::string> params({"x", "y", "z", "w"});
    auto function = Function::parse(params, "if(w,(x+y)*y,(y+z)*z)");
    EXPECT_EQUAL(List(count_param_usage(*function)), List({0.5, 1.5, 1.0, 1.0}));
    EXPECT_EQUAL(List(check_param_usage(*function)), List({0.5, 1.0, 0.5, 1.0}));
}

TEST("require that if children probability can be adjusted") {
    std::vector<vespalib::string> params({"x", "y", "z"});
    auto function = Function::parse(params, "if(z,x*x,y*y,0.8)");
    EXPECT_EQUAL(List(count_param_usage(*function)), List({1.6, 0.4, 1.0}));
    EXPECT_EQUAL(List(check_param_usage(*function)), List({0.8, 0.2, 1.0}));
}

TEST("require that chained if statements are combined correctly") {
    std::vector<vespalib::string> params({"x", "y", "z", "w"});
    auto function = Function::parse(params, "if(z,x,y)+if(w,y,x)");
    EXPECT_EQUAL(List(count_param_usage(*function)), List({1.0, 1.0, 1.0, 1.0}));
    EXPECT_EQUAL(List(check_param_usage(*function)), List({0.75, 0.75, 1.0, 1.0}));
}

TEST("require that multi-level if statements are combined correctly") {
    std::vector<vespalib::string> params({"x", "y", "z", "w"});
    auto function = Function::parse(params, "if(z,if(w,y*x,x*x),if(w,y*x,x*x))");
    EXPECT_EQUAL(List(count_param_usage(*function)), List({1.5, 0.5, 1.0, 1.0}));
    EXPECT_EQUAL(List(check_param_usage(*function)), List({1.0, 0.5, 1.0, 1.0}));
}

TEST("require that lazy parameters are suggested for functions with parameters that might not be used") {
    auto function = Function::parse("if(z,x,y)+if(w,y,x)");
    EXPECT_TRUE(CompiledFunction::should_use_lazy_params(*function));
}

TEST("require that lazy parameters are not suggested for functions where all parameters are always used") {
    auto function = Function::parse("a*b*c");
    EXPECT_TRUE(!CompiledFunction::should_use_lazy_params(*function));
}

TEST_MAIN() { TEST_RUN_ALL(); }

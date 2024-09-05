// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/param_usage.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/vespalib/gtest/gtest.h>

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

void PrintTo(const List& list, std::ostream* os) {
    *os << ::testing::PrintToString(list.list);
}

TEST(ParamUsageTest, require_that_simple_expression_has_appropriate_parameter_usage)
{
    std::vector<std::string> params({"x", "y", "z"});
    auto function = Function::parse(params, "(x+y)*y");
    EXPECT_EQ(List(count_param_usage(*function)), List({1.0, 2.0, 0.0}));
    EXPECT_EQ(List(check_param_usage(*function)), List({1.0, 1.0, 0.0}));
}

TEST(ParamUsageTest, require_that_if_children_have_50_percent_probability_each_by_default)
{
    std::vector<std::string> params({"x", "y", "z", "w"});
    auto function = Function::parse(params, "if(w,(x+y)*y,(y+z)*z)");
    EXPECT_EQ(List(count_param_usage(*function)), List({0.5, 1.5, 1.0, 1.0}));
    EXPECT_EQ(List(check_param_usage(*function)), List({0.5, 1.0, 0.5, 1.0}));
}

TEST(ParamUsageTest, require_that_if_children_probability_can_be_adjusted)
{
    std::vector<std::string> params({"x", "y", "z"});
    auto function = Function::parse(params, "if(z,x*x,y*y,0.8)");
    EXPECT_EQ(List(count_param_usage(*function)), List({1.6, 0.4, 1.0}));
    EXPECT_EQ(List(check_param_usage(*function)), List({0.8, 0.2, 1.0}));
}

TEST(ParamUsageTest, require_that_chained_if_statements_are_combined_correctly)
{
    std::vector<std::string> params({"x", "y", "z", "w"});
    auto function = Function::parse(params, "if(z,x,y)+if(w,y,x)");
    EXPECT_EQ(List(count_param_usage(*function)), List({1.0, 1.0, 1.0, 1.0}));
    EXPECT_EQ(List(check_param_usage(*function)), List({0.75, 0.75, 1.0, 1.0}));
}

TEST(ParamUsageTest, require_that_multi_level_if_statements_are_combined_correctly)
{
    std::vector<std::string> params({"x", "y", "z", "w"});
    auto function = Function::parse(params, "if(z,if(w,y*x,x*x),if(w,y*x,x*x))");
    EXPECT_EQ(List(count_param_usage(*function)), List({1.5, 0.5, 1.0, 1.0}));
    EXPECT_EQ(List(check_param_usage(*function)), List({1.0, 0.5, 1.0, 1.0}));
}

TEST(ParamUsageTest, require_that_lazy_parameters_are_suggested_for_functions_with_parameters_that_might_not_be_used)
{
    auto function = Function::parse("if(z,x,y)+if(w,y,x)");
    EXPECT_TRUE(CompiledFunction::should_use_lazy_params(*function));
}

TEST(ParamUsageTest, require_that_lazy_parameters_are_not_suggested_for_functions_where_all_parameters_are_always_used)
{
    auto function = Function::parse("a*b*c");
    EXPECT_TRUE(!CompiledFunction::should_use_lazy_params(*function));
}

GTEST_MAIN_RUN_ALL_TESTS()

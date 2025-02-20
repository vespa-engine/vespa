// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/node_tools.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;

auto make_copy(const Function &fun) {
    std::vector<std::string> params;
    for (size_t i = 0; i < fun.num_params(); ++i) {
        params.push_back(fun.param_name(i));
    }
    return Function::create(NodeTools::copy(fun.root()), params);
}

void verify_copy(const std::string &expr, const std::string &expect) {
    SCOPED_TRACE(expr);
    auto fun = Function::parse(expr);
    auto fun_copy = make_copy(*fun);
    EXPECT_EQ(fun_copy->dump(), expect);
}
void verify_copy(const std::string &expr) { verify_copy(expr, expr); }

TEST(NodeToolsTest, require_that_required_parameter_count_can_be_detected)
{
    auto function = Function::parse({"a","b","c"}, "(c+a)+(b+1)");
    const auto &root = function->root();
    ASSERT_EQ(root.num_children(), 2u);
    const auto &n_c_a = root.get_child(0);
    const auto &n_b_1 = root.get_child(1);
    ASSERT_EQ(n_c_a.num_children(), 2u);
    const auto &n_c = n_c_a.get_child(0);
    const auto &n_a = n_c_a.get_child(1);
    ASSERT_EQ(n_b_1.num_children(), 2u);
    const auto &n_b = n_b_1.get_child(0);
    const auto &n_1 = n_b_1.get_child(1);
    EXPECT_EQ(NodeTools::min_num_params(root), 3u);
    EXPECT_EQ(NodeTools::min_num_params(n_c_a), 3u);
    EXPECT_EQ(NodeTools::min_num_params(n_b_1), 2u);
    EXPECT_EQ(NodeTools::min_num_params(n_c), 3u);
    EXPECT_EQ(NodeTools::min_num_params(n_a), 1u);
    EXPECT_EQ(NodeTools::min_num_params(n_b), 2u);
    EXPECT_EQ(NodeTools::min_num_params(n_1), 0u);
}

TEST(NodeToolsTest, require_that_basic_node_types_can_be_copied)
{
    verify_copy("123");
    verify_copy("foo");
    verify_copy("\"string value\"");
    verify_copy("(a in [1,\"2\",3])");
    verify_copy("(-a)");
    verify_copy("(!a)");
    verify_copy("if(a,b,c)");
    verify_copy("if(a,b,c,0.7)");
    verify_copy("#", "[]...[missing value]...[#]");
}

TEST(NodeToolsTest, require_that_operator_node_types_can_be_copied)
{
    verify_copy("(a+b)");
    verify_copy("(a-b)");
    verify_copy("(a*b)");
    verify_copy("(a/b)");
    verify_copy("(a%b)");
    verify_copy("(a^b)");
    verify_copy("(a==b)");
    verify_copy("(a!=b)");
    verify_copy("(a~=b)");
    verify_copy("(a<b)");
    verify_copy("(a<=b)");
    verify_copy("(a>b)");
    verify_copy("(a>=b)");
    verify_copy("(a&&b)");
    verify_copy("(a||b)");
}

TEST(NodeToolsTest, require_that_call_node_types_can_be_copied)
{
    verify_copy("cos(a)");
    verify_copy("sin(a)");
    verify_copy("tan(a)");
    verify_copy("cosh(a)");
    verify_copy("sinh(a)");
    verify_copy("tanh(a)");
    verify_copy("acos(a)");
    verify_copy("asin(a)");
    verify_copy("atan(a)");
    verify_copy("exp(a)");
    verify_copy("log10(a)");
    verify_copy("log(a)");
    verify_copy("sqrt(a)");
    verify_copy("ceil(a)");
    verify_copy("fabs(a)");
    verify_copy("floor(a)");
    verify_copy("atan2(a,b)");
    verify_copy("ldexp(a,b)");
    verify_copy("pow(a,b)");
    verify_copy("fmod(a,b)");
    verify_copy("min(a,b)");
    verify_copy("max(a,b)");
    verify_copy("isNan(a)");
    verify_copy("relu(a)");
    verify_copy("sigmoid(a)");
    verify_copy("elu(a)");
    verify_copy("erf(a)");
    verify_copy("bit(a,b)");
    verify_copy("hamming(a,b)");
}

TEST(NodeToolsTest, require_that_tensor_node_types_can_NOT_be_copied_yet)
{
    verify_copy("map(a,f(x)(x))", "not implemented");
    verify_copy("join(a,b,f(x,y)(x*y))", "not implemented");
    verify_copy("merge(a,b,f(x,y)(y))", "not implemented");
    verify_copy("reduce(a,sum)", "not implemented");
    verify_copy("rename(a,x,y)", "not implemented");
    verify_copy("concat(a,b,x)", "not implemented");
    verify_copy("tensor(x[3]):[1,2,3]", "not implemented");
    verify_copy("tensor(x[3])(x)", "not implemented");
    verify_copy("a{x:0}", "not implemented");
}

TEST(NodeToolsTest, require_that_nested_expressions_can_be_copied)
{
    verify_copy("min(a,if(((b+3)==7),(!c),(d+7)))");
}

GTEST_MAIN_RUN_ALL_TESTS()

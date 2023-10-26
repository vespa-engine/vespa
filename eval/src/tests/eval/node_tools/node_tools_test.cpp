// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/node_tools.h>

using namespace vespalib::eval;

auto make_copy(const Function &fun) {
    std::vector<vespalib::string> params;
    for (size_t i = 0; i < fun.num_params(); ++i) {
        params.push_back(fun.param_name(i));
    }
    return Function::create(NodeTools::copy(fun.root()), params);
}

void verify_copy(const vespalib::string &expr, const vespalib::string &expect) {   
    auto fun = Function::parse(expr);
    auto fun_copy = make_copy(*fun);
    EXPECT_EQUAL(fun_copy->dump(), expect);
}
void verify_copy(const vespalib::string &expr) { verify_copy(expr, expr); }

TEST("require that required parameter count can be detected") {
    auto function = Function::parse({"a","b","c"}, "(c+a)+(b+1)");
    const auto &root = function->root();
    ASSERT_EQUAL(root.num_children(), 2u);
    const auto &n_c_a = root.get_child(0);
    const auto &n_b_1 = root.get_child(1);
    ASSERT_EQUAL(n_c_a.num_children(), 2u);
    const auto &n_c = n_c_a.get_child(0);
    const auto &n_a = n_c_a.get_child(1);
    ASSERT_EQUAL(n_b_1.num_children(), 2u);
    const auto &n_b = n_b_1.get_child(0);
    const auto &n_1 = n_b_1.get_child(1);
    EXPECT_EQUAL(NodeTools::min_num_params(root), 3u);
    EXPECT_EQUAL(NodeTools::min_num_params(n_c_a), 3u);
    EXPECT_EQUAL(NodeTools::min_num_params(n_b_1), 2u);
    EXPECT_EQUAL(NodeTools::min_num_params(n_c), 3u);
    EXPECT_EQUAL(NodeTools::min_num_params(n_a), 1u);
    EXPECT_EQUAL(NodeTools::min_num_params(n_b), 2u);
    EXPECT_EQUAL(NodeTools::min_num_params(n_1), 0u);
}

TEST("require that basic node types can be copied") {
    TEST_DO(verify_copy("123"));
    TEST_DO(verify_copy("foo"));
    TEST_DO(verify_copy("\"string value\""));
    TEST_DO(verify_copy("(a in [1,\"2\",3])"));
    TEST_DO(verify_copy("(-a)"));
    TEST_DO(verify_copy("(!a)"));
    TEST_DO(verify_copy("if(a,b,c)"));
    TEST_DO(verify_copy("if(a,b,c,0.7)"));
    TEST_DO(verify_copy("#", "[]...[missing value]...[#]"));
}

TEST("require that operator node types can be copied") {
    TEST_DO(verify_copy("(a+b)"));
    TEST_DO(verify_copy("(a-b)"));
    TEST_DO(verify_copy("(a*b)"));
    TEST_DO(verify_copy("(a/b)"));
    TEST_DO(verify_copy("(a%b)"));
    TEST_DO(verify_copy("(a^b)"));
    TEST_DO(verify_copy("(a==b)"));
    TEST_DO(verify_copy("(a!=b)"));
    TEST_DO(verify_copy("(a~=b)"));
    TEST_DO(verify_copy("(a<b)"));
    TEST_DO(verify_copy("(a<=b)"));
    TEST_DO(verify_copy("(a>b)"));
    TEST_DO(verify_copy("(a>=b)"));
    TEST_DO(verify_copy("(a&&b)"));
    TEST_DO(verify_copy("(a||b)"));
}

TEST("require that call node types can be copied") {
    TEST_DO(verify_copy("cos(a)"));
    TEST_DO(verify_copy("sin(a)"));
    TEST_DO(verify_copy("tan(a)"));
    TEST_DO(verify_copy("cosh(a)"));
    TEST_DO(verify_copy("sinh(a)"));
    TEST_DO(verify_copy("tanh(a)"));
    TEST_DO(verify_copy("acos(a)"));
    TEST_DO(verify_copy("asin(a)"));
    TEST_DO(verify_copy("atan(a)"));
    TEST_DO(verify_copy("exp(a)"));
    TEST_DO(verify_copy("log10(a)"));
    TEST_DO(verify_copy("log(a)"));
    TEST_DO(verify_copy("sqrt(a)"));
    TEST_DO(verify_copy("ceil(a)"));
    TEST_DO(verify_copy("fabs(a)"));
    TEST_DO(verify_copy("floor(a)"));
    TEST_DO(verify_copy("atan2(a,b)"));
    TEST_DO(verify_copy("ldexp(a,b)"));
    TEST_DO(verify_copy("pow(a,b)"));
    TEST_DO(verify_copy("fmod(a,b)"));
    TEST_DO(verify_copy("min(a,b)"));
    TEST_DO(verify_copy("max(a,b)"));
    TEST_DO(verify_copy("isNan(a)"));
    TEST_DO(verify_copy("relu(a)"));
    TEST_DO(verify_copy("sigmoid(a)"));
    TEST_DO(verify_copy("elu(a)"));
    TEST_DO(verify_copy("erf(a)"));
    TEST_DO(verify_copy("bit(a,b)"));
    TEST_DO(verify_copy("hamming(a,b)"));
}

TEST("require that tensor node types can NOT be copied (yet)") {
    TEST_DO(verify_copy("map(a,f(x)(x))", "not implemented"));
    TEST_DO(verify_copy("join(a,b,f(x,y)(x*y))", "not implemented"));
    TEST_DO(verify_copy("merge(a,b,f(x,y)(y))", "not implemented"));
    TEST_DO(verify_copy("reduce(a,sum)", "not implemented"));
    TEST_DO(verify_copy("rename(a,x,y)", "not implemented"));
    TEST_DO(verify_copy("concat(a,b,x)", "not implemented"));
    TEST_DO(verify_copy("tensor(x[3]):[1,2,3]", "not implemented"));
    TEST_DO(verify_copy("tensor(x[3])(x)", "not implemented"));
    TEST_DO(verify_copy("a{x:0}", "not implemented"));
}

TEST("require that nested expressions can be copied") {
    TEST_DO(verify_copy("min(a,if(((b+3)==7),(!c),(d+7)))"));
}

TEST_MAIN() { TEST_RUN_ALL(); }

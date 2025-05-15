// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/node_types.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "vespa/eval/eval/tensor_nodes.h"

using namespace vespalib::eval;

struct TypeSpecExtractor : public vespalib::eval::SymbolExtractor {
    void extract_symbol(const char *pos_in, const char *end_in,
                        const char *&pos_out, std::string &symbol_out) const override
    {
        ValueType type = value_type::parse_spec(pos_in, end_in, pos_out);
        if (pos_out != nullptr) {
            symbol_out = type.to_spec();
        }
    }
};

void print_errors(const NodeTypes &types) {
    if (!types.errors().empty()) {
        for (const auto &msg: types.errors()) {
            fprintf(stderr, "type error: %s\n", msg.c_str());
        }
    }
}

using ext_check_t = std::function<void(const Function &fun, const NodeTypes &types)>;
struct NopExtCheck {
    void operator()(const Function &, const NodeTypes &) noexcept {}
};
void verify(const std::string &type_expr, const std::string &type_spec, ext_check_t ext_check = NopExtCheck())
{
    SCOPED_TRACE(type_expr);
    auto function = Function::parse(type_expr, TypeSpecExtractor());
    ASSERT_TRUE(!function->has_error()) << "parse error: " << function->get_error();
    std::vector<ValueType> input_types;
    for (size_t i = 0; i < function->num_params(); ++i) {
        input_types.push_back(ValueType::from_spec(function->param_name(i)));
    }
    NodeTypes types(*function, input_types);
    print_errors(types);
    ValueType expected_type = ValueType::from_spec(type_spec);
    ValueType actual_type = types.get_type(function->root());
    EXPECT_EQ(expected_type, actual_type);
    ext_check(*function, types);
}

TEST(NodeTypesTest, require_that_error_nodes_have_error_type)
{
    auto function = Function::parse("1 2 3 4 5", TypeSpecExtractor());
    EXPECT_TRUE(function->has_error());
    NodeTypes types(*function, std::vector<ValueType>());
    ValueType expected_type = ValueType::from_spec("error");
    ValueType actual_type = types.get_type(function->root());
    EXPECT_EQ(expected_type, actual_type);
}

TEST(NodeTypesTest, require_that_leaf_constants_have_appropriate_type)
{
    verify("123", "double");
    verify("\"string values are hashed\"", "double");
}

TEST(NodeTypesTest, require_that_input_parameters_preserve_their_type)
{
    verify("error", "error");
    verify("double", "double");
    verify("tensor()", "double");
    verify("tensor(x{},y[10],z[5])", "tensor(x{},y[10],z[5])");
    verify("tensor<float>(x{},y[10],z[5])", "tensor<float>(x{},y[10],z[5])");
    verify("tensor<bfloat16>(x{},y[10],z[5])", "tensor<bfloat16>(x{},y[10],z[5])");
    verify("tensor<int8>(x{},y[10],z[5])", "tensor<int8>(x{},y[10],z[5])");
}

TEST(NodeTypesTest, require_that_if_resolves_to_the_appropriate_type)
{
    verify("if(error,1,2)", "error");
    verify("if(1,error,2)", "error");
    verify("if(1,2,error)", "error");
    verify("if(double,1,2)", "double");
    verify("if(tensor(x[10]),1,2)", "double");
    verify("if(double,tensor(a{}),tensor(a{}))", "tensor(a{})");
    verify("if(double,tensor(a[2]),tensor(a[2]))", "tensor(a[2])");
    verify("if(double,tensor<float>(a[2]),tensor<float>(a[2]))", "tensor<float>(a[2])");
    verify("if(double,tensor<bfloat16>(a[2]),tensor<bfloat16>(a[2]))", "tensor<bfloat16>(a[2])");
    verify("if(double,tensor<int8>(a[2]),tensor<int8>(a[2]))", "tensor<int8>(a[2])");
    verify("if(double,tensor(a[2]),tensor<float>(a[2]))", "error");
    verify("if(double,tensor<float>(a[2]),tensor<bfloat16>(a[2]))", "error");
    verify("if(double,tensor<float>(a[2]),tensor<int8>(a[2]))", "error");
    verify("if(double,tensor<bfloat16>(a[2]),tensor<int8>(a[2]))", "error");
    verify("if(double,tensor(a[2]),tensor(a[3]))", "error");
    verify("if(double,tensor(a[2]),tensor(a{}))", "error");
    verify("if(double,tensor(a{}),tensor(b{}))", "error");
    verify("if(double,tensor(a{}),double)", "error");
}

TEST(NodeTypesTest, require_that_reduce_resolves_correct_type)
{
    verify("reduce(error,sum)", "error");
    verify("reduce(tensor(x{}),sum)", "double");
    verify("reduce(double,sum)", "double");
    verify("reduce(error,sum,x)", "error");
    verify("reduce(double,sum,x)", "error");
    verify("reduce(tensor(x{},y{},z{}),sum,y)", "tensor(x{},z{})");
    verify("reduce(tensor(x{},y{},z{}),sum,x,z)", "tensor(y{})");
    verify("reduce(tensor(x{},y{},z{}),sum,y,z,x)", "double");
    verify("reduce(tensor(x{},y{},z{}),sum,w)", "error");
    verify("reduce(tensor(x{},y{},z{}),sum,a,b,c)", "error");
    verify("reduce(tensor(x{}),sum,x)", "double");
    verify("reduce(tensor<float>(x{},y{},z{}),sum,x,z)", "tensor<float>(y{})");
    verify("reduce(tensor<bfloat16>(x{},y{},z{}),sum,x,z)", "tensor<float>(y{})");
    verify("reduce(tensor<int8>(x{},y{},z{}),sum,x,z)", "tensor<float>(y{})");
    verify("reduce(tensor<float>(x{}),sum,x)", "double");
    verify("reduce(tensor<float>(x{}),sum)", "double");
    verify("reduce(tensor<bfloat16>(x{}),sum,x)", "double");
    verify("reduce(tensor<bfloat16>(x{}),sum)", "double");
    verify("reduce(tensor<int8>(x{}),sum,x)", "double");
    verify("reduce(tensor<int8>(x{}),sum)", "double");
}

TEST(NodeTypesTest, require_that_rename_resolves_correct_type)
{
    verify("rename(error,x,y)", "error");
    verify("rename(double,x,y)", "error");
    verify("rename(tensor(x{},y[1],z[5]),a,b)", "error");
    verify("rename(tensor(x{},y[1],z[5]),x,y)", "error");
    verify("rename(tensor(x{},y[1],z[5]),x,x)", "tensor(x{},y[1],z[5])");
    verify("rename(tensor(x{},y[1],z[5]),x,w)", "tensor(w{},y[1],z[5])");
    verify("rename(tensor(x{},y[1],z[5]),y,w)", "tensor(x{},w[1],z[5])");
    verify("rename(tensor(x{},y[1],z[5]),z,w)", "tensor(x{},y[1],w[5])");
    verify("rename(tensor(x{},y[1],z[5]),(x,y,z),(z,y,x))", "tensor(z{},y[1],x[5])");
    verify("rename(tensor(x{},y[1],z[5]),(x,z),(z,x))", "tensor(z{},y[1],x[5])");
    verify("rename(tensor(x{},y[1],z[5]),(x,y,z),(a,b,c))", "tensor(a{},b[1],c[5])");
    verify("rename(tensor<float>(x{},y[1],z[5]),(x,y,z),(a,b,c))", "tensor<float>(a{},b[1],c[5])");
    verify("rename(tensor<bfloat16>(x{},y[1],z[5]),(x,y,z),(a,b,c))", "tensor<bfloat16>(a{},b[1],c[5])");
    verify("rename(tensor<int8>(x{},y[1],z[5]),(x,y,z),(a,b,c))", "tensor<int8>(a{},b[1],c[5])");
}

std::string strfmt(const char *pattern, const char *a) {
    return vespalib::make_string(pattern, a);
}

std::string strfmt(const char *pattern, const char *a, const char *b) {
    return vespalib::make_string(pattern, a, b);
}

void verify_op1(const char *pattern) {
    SCOPED_TRACE(strfmt("verify_op1(\"%s\")", pattern));
    verify(strfmt(pattern, "error"), "error");
    verify(strfmt(pattern, "double"), "double");
    verify(strfmt(pattern, "tensor(x{},y[10],z[1])"), "tensor(x{},y[10],z[1])");
    verify(strfmt(pattern, "tensor<float>(x{},y[10],z[1])"), "tensor<float>(x{},y[10],z[1])");
    verify(strfmt(pattern, "tensor<bfloat16>(x{},y[10],z[1])"), "tensor<float>(x{},y[10],z[1])");
    verify(strfmt(pattern, "tensor<int8>(x{},y[10],z[1])"), "tensor<float>(x{},y[10],z[1])");
}

void verify_op2(const char *pattern) {
    SCOPED_TRACE(strfmt("verify_op2(\"%s\")", pattern));
    verify(strfmt(pattern, "error", "error"), "error");
    verify(strfmt(pattern, "double", "error"), "error");
    verify(strfmt(pattern, "error", "double"), "error");
    verify(strfmt(pattern, "tensor(x{})", "error"), "error");
    verify(strfmt(pattern, "error", "tensor(x{})"), "error");
    verify(strfmt(pattern, "double", "double"), "double");
    verify(strfmt(pattern, "tensor(x{})", "double"), "tensor(x{})");
    verify(strfmt(pattern, "double", "tensor(x{})"), "tensor(x{})");
    verify(strfmt(pattern, "tensor(x{})", "tensor(x{})"), "tensor(x{})");
    verify(strfmt(pattern, "tensor(x{})", "tensor(y{})"), "tensor(x{},y{})");
    verify(strfmt(pattern, "tensor(x[5])", "tensor(x[5])"), "tensor(x[5])");
    verify(strfmt(pattern, "tensor(x[3])", "tensor(x[5])"), "error");
    verify(strfmt(pattern, "tensor(x[5])", "tensor(x[3])"), "error");
    verify(strfmt(pattern, "tensor(x{})", "tensor(x[5])"), "error");
    verify(strfmt(pattern, "tensor<float>(x[5])", "tensor<float>(x[5])"), "tensor<float>(x[5])");
    verify(strfmt(pattern, "tensor<float>(x[5])", "tensor(x[5])"), "tensor(x[5])");
    verify(strfmt(pattern, "tensor<float>(x[5])", "double"), "tensor<float>(x[5])");
    verify(strfmt(pattern, "tensor<bfloat16>(x[5])", "tensor<bfloat16>(x[5])"), "tensor<float>(x[5])");
    verify(strfmt(pattern, "tensor<bfloat16>(x[5])", "tensor<float>(x[5])"), "tensor<float>(x[5])");
    verify(strfmt(pattern, "tensor<bfloat16>(x[5])", "tensor(x[5])"), "tensor(x[5])");
    verify(strfmt(pattern, "tensor<bfloat16>(x[5])", "double"), "tensor<float>(x[5])");
    verify(strfmt(pattern, "tensor<int8>(x[5])",     "tensor<int8>(x[5])"), "tensor<float>(x[5])");
    verify(strfmt(pattern, "tensor<bfloat16>(x[5])", "tensor<int8>(x[5])"), "tensor<float>(x[5])");
    verify(strfmt(pattern, "tensor<float>(x[5])",    "tensor<int8>(x[5])"), "tensor<float>(x[5])");
    verify(strfmt(pattern, "tensor(x[5])",           "tensor<int8>(x[5])"), "tensor(x[5])");
    verify(strfmt(pattern, "double",                 "tensor<int8>(x[5])"), "tensor<float>(x[5])");
}

TEST(NodeTypesTest, require_that_various_operations_resolve_appropriate_type)
{
    verify_op1("-%s");          // Neg
    verify_op1("!%s");          // Not
    verify_op2("%s+%s");        // Add
    verify_op2("%s-%s");        // Sub
    verify_op2("%s*%s");        // Mul
    verify_op2("%s/%s");        // Div
    verify_op2("%s^%s");        // Pow
    verify_op2("%s==%s");       // Equal
    verify_op2("%s!=%s");       // NotEqual
    verify_op2("%s~=%s");       // Approx
    verify_op2("%s<%s");        // Less
    verify_op2("%s<=%s");       // LessEqual
    verify_op2("%s>%s");        // Greater
    verify_op2("%s>=%s");       // GreaterEqual
    verify_op2("%s&&%s");       // And
    verify_op2("%s||%s");       // Or
    verify_op1("cos(%s)");      // Cos
    verify_op1("sin(%s)");      // Sin
    verify_op1("tan(%s)");      // Tan
    verify_op1("cosh(%s)");     // Cosh
    verify_op1("sinh(%s)");     // Sinh
    verify_op1("tanh(%s)");     // Tanh
    verify_op1("acos(%s)");     // Acos
    verify_op1("asin(%s)");     // Asin
    verify_op1("atan(%s)");     // Atan
    verify_op1("exp(%s)");      // Exp
    verify_op1("log10(%s)");    // Log10
    verify_op1("log(%s)");      // Log
    verify_op1("sqrt(%s)");     // Sqrt
    verify_op1("ceil(%s)");     // Ceil
    verify_op1("fabs(%s)");     // Fabs
    verify_op1("floor(%s)");    // Floor
    verify_op2("atan2(%s,%s)"); // Atan2
    verify_op2("ldexp(%s,%s)"); // Ldexp
    verify_op2("pow(%s,%s)");   // Pow2
    verify_op2("fmod(%s,%s)");  // Fmod
    verify_op2("min(%s,%s)");   // min
    verify_op2("max(%s,%s)");   // max
    verify_op1("isNan(%s)");    // IsNan
    verify_op1("relu(%s)");     // Relu
    verify_op1("sigmoid(%s)");  // Sigmoid
    verify_op1("elu(%s)");      // Elu
    verify_op1("erf(%s)");      // Erf
    verify_op2("bit(%s,%s)");   // Bit
    verify_op2("hamming(%s,%s)"); // Hamming
}

TEST(NodeTypesTest, require_that_map_resolves_correct_type)
{
    verify_op1("map(%s,f(x)(sin(x)))");
}

TEST(NodeTypesTest, require_that_set_membership_resolves_correct_type)
{
    verify_op1("%s in [1,2,3]");
}

TEST(NodeTypesTest, require_that_join_resolves_correct_type)
{
    verify_op2("join(%s,%s,f(x,y)(x+y))");
}

TEST(NodeTypesTest, require_that_merge_resolves_to_the_appropriate_type)
{
    const char *pattern = "merge(%s,%s,f(x,y)(x+y))";
    verify(strfmt(pattern, "error", "error"), "error");
    verify(strfmt(pattern, "double", "error"), "error");
    verify(strfmt(pattern, "error", "double"), "error");
    verify(strfmt(pattern, "tensor(x{})", "error"), "error");
    verify(strfmt(pattern, "error", "tensor(x{})"), "error");
    verify(strfmt(pattern, "double", "double"), "double");
    verify(strfmt(pattern, "tensor(x{})", "double"), "error");
    verify(strfmt(pattern, "double", "tensor(x{})"), "error");
    verify(strfmt(pattern, "tensor(x{})", "tensor(x{})"), "tensor(x{})");
    verify(strfmt(pattern, "tensor(x{})", "tensor(y{})"), "error");
    verify(strfmt(pattern, "tensor(x[5])", "tensor(x[5])"), "tensor(x[5])");
    verify(strfmt(pattern, "tensor(x[3])", "tensor(x[5])"), "error");
    verify(strfmt(pattern, "tensor(x[5])", "tensor(x[3])"), "error");
    verify(strfmt(pattern, "tensor(x{})", "tensor(x[5])"), "error");
    verify(strfmt(pattern, "tensor<float>(x[5])", "tensor<float>(x[5])"), "tensor<float>(x[5])");
    verify(strfmt(pattern, "tensor<bfloat16>(x[5])", "tensor<bfloat16>(x[5])"), "tensor<float>(x[5])");
    verify(strfmt(pattern, "tensor<int8>(x[5])", "tensor<int8>(x[5])"), "tensor<float>(x[5])");
    verify(strfmt(pattern, "tensor<float>(x[5])", "tensor(x[5])"), "tensor(x[5])");
    verify(strfmt(pattern, "tensor<bfloat16>(x[5])", "tensor(x[5])"), "tensor(x[5])");
    verify(strfmt(pattern, "tensor<int8>(x[5])", "tensor(x[5])"), "tensor(x[5])");
    verify(strfmt(pattern, "tensor(x[5])", "tensor<float>(x[5])"), "tensor(x[5])");
    verify(strfmt(pattern, "tensor<float>(x[5])", "double"), "error");
}

TEST(NodeTypesTest, require_that_static_tensor_lambda_resolves_correct_type)
{
    verify("tensor(x[5])(1.0)", "tensor(x[5])");
    verify("tensor(x[5],y[10])(1.0)", "tensor(x[5],y[10])");
    verify("tensor(x[5],y[10],z[15])(1.0)", "tensor(x[5],y[10],z[15])");
    verify("tensor<double>(x[5],y[10],z[15])(1.0)", "tensor(x[5],y[10],z[15])");
    verify("tensor<float>(x[5],y[10],z[15])(1.0)", "tensor<float>(x[5],y[10],z[15])");
    verify("tensor<bfloat16>(x[5],y[10],z[15])(1.0)", "tensor<bfloat16>(x[5],y[10],z[15])");
    verify("tensor<int8>(x[5],y[10],z[15])(1.0)", "tensor<int8>(x[5],y[10],z[15])");
}

TEST(NodeTypesTest, require_that_tensor_create_resolves_correct_type)
{
    verify("tensor(x[3]):{{x:0}:double,{x:1}:double,{x:2}:double}", "tensor(x[3])");
    verify("tensor(x{}):{{x:a}:double,{x:b}:double,{x:c}:double}", "tensor(x{})");
    verify("tensor(x{},y[2]):{{x:a,y:0}:double,{x:a,y:1}:double}", "tensor(x{},y[2])");
    verify("tensor<float>(x[3]):{{x:0}:double,{x:1}:double,{x:2}:double}", "tensor<float>(x[3])");
    verify("tensor<bfloat16>(x[3]):{{x:0}:double,{x:1}:double,{x:2}:double}", "tensor<bfloat16>(x[3])");
    verify("tensor<int8>(x[3]):{{x:0}:double,{x:1}:double,{x:2}:double}", "tensor<int8>(x[3])");
    verify("tensor(x[3]):{{x:0}:double+double,{x:1}:double-double,{x:2}:double/double}", "tensor(x[3])");
    verify("tensor(x[3]):{{x:0}:double,{x:1}:reduce(tensor(x[2]),sum),{x:2}:double}", "tensor(x[3])");
    verify("tensor(x[3]):{{x:0}:double,{x:1}:tensor(x[2]),{x:2}:double}", "error");
    verify("tensor(x[3]):{{x:0}:double,{x:1}:error,{x:2}:double}", "error");
}

TEST(NodeTypesTest, require_that_dynamic_tensor_lambda_resolves_correct_type)
{
    verify("tensor(x[3])(error)", "error");
    verify("tensor(x[3])(double)", "tensor(x[3])");
    verify("tensor<float>(x[3])(double)", "tensor<float>(x[3])");
    verify("tensor<bfloat16>(x[3])(double)", "tensor<bfloat16>(x[3])");
    verify("tensor<int8>(x[3])(double)", "tensor<int8>(x[3])");
    verify("tensor(x[3])(tensor(x[2]))", "error");
    verify("tensor(x[3])(reduce(tensor(x[2])+tensor(x[4]),sum))", "error");
}

TEST(NodeTypesTest, require_that_tensor_peek_resolves_correct_type)
{
    verify("tensor(x[3]){x:1}", "double");
    verify("tensor(x[3]){x:double}", "error");
    verify("tensor(x[3]){x:(double)}", "double");
    verify("tensor(x[3]){x:3}", "error");
    verify("tensor(x{}){x:1}", "double");
    verify("tensor(x{}){x:foo}", "double");
    verify("tensor(x{}){x:(double)}", "double");
    verify("tensor(x{}){x:(tensor(x[3]))}", "error");
    verify("tensor(x{},y[3]){x:foo,y:2}", "double");
    verify("tensor(x{},y[3]){x:foo}", "tensor(y[3])");
    verify("tensor(x{},y[3]){y:2}", "tensor(x{})");
    verify("tensor<float>(x[3]){x:1}", "double");
    verify("tensor<float>(x[3]){x:double}", "error");
    verify("tensor<float>(x[3]){x:(double)}", "double");
    verify("tensor<float>(x[3]){x:3}", "error");
    verify("tensor<float>(x{}){x:1}", "double");
    verify("tensor<float>(x{}){x:foo}", "double");
    verify("tensor<bfloat16>(x{}){x:foo}", "double");
    verify("tensor<int8>(x{}){x:foo}", "double");
    verify("tensor<float>(x{}){x:(double)}", "double");
    verify("tensor<float>(x{}){x:(tensor(x[3]))}", "error");
    verify("tensor<float>(x{},y[3]){x:foo,y:2}", "double");
    verify("tensor<float>(x{},y[3]){x:foo}", "tensor<float>(y[3])");
    verify("tensor<float>(x{},y[3]){y:2}", "tensor<float>(x{})");
    verify("tensor<bfloat16>(x{},y[3]){y:2}", "tensor<bfloat16>(x{})");
    verify("tensor<int8>(x{},y[3]){y:2}", "tensor<int8>(x{})");
}

TEST(NodeTypesTest, require_that_tensor_concat_resolves_correct_type)
{
    verify("concat(double,double,x)", "tensor(x[2])");
    verify("concat(tensor(x[2]),tensor(x[3]),x)", "tensor(x[5])");
    verify("concat(tensor(x[2]),tensor(x[2]),y)", "tensor(x[2],y[2])");
    verify("concat(tensor(x[2]),tensor(x[3]),y)", "error");
    verify("concat(tensor(x[2]),tensor(x{}),x)", "error");
    verify("concat(tensor(x[2]),tensor(y{}),x)", "tensor(x[3],y{})");
    verify("concat(tensor<float>(x[2]),tensor<float>(x[3]),x)", "tensor<float>(x[5])");
    verify("concat(tensor<float>(x[2]),tensor(x[3]),x)", "tensor(x[5])");
    verify("concat(tensor<float>(x[2]),double,x)", "tensor<float>(x[3])");
    verify("concat(tensor<bfloat16>(x[2]),tensor<bfloat16>(x[3]),x)", "tensor<bfloat16>(x[5])");
    verify("concat(tensor<bfloat16>(x[2]),tensor<float>(x[3]),x)", "tensor<float>(x[5])");
    verify("concat(tensor<bfloat16>(x[2]),tensor(x[3]),x)", "tensor(x[5])");
    verify("concat(tensor<bfloat16>(x[2]),double,x)", "tensor<bfloat16>(x[3])");
    verify("concat(tensor<int8>(x[3]),tensor<int8>(x[2]),x)", "tensor<int8>(x[5])");
    verify("concat(tensor<bfloat16>(x[3]),tensor<int8>(x[2]),x)", "tensor<float>(x[5])");
    verify("concat(tensor<float>(x[3]),tensor<int8>(x[2]),x)", "tensor<float>(x[5])");
    verify("concat(tensor(x[3]),tensor<int8>(x[2]),x)", "tensor(x[5])");
    verify("concat(double,tensor<int8>(x[2]),x)", "tensor<int8>(x[3])");
}

TEST(NodeTypesTest, require_that_tensor_cell_cast_resolves_correct_type)
{
    verify("cell_cast(double,double)", "double");
    verify("cell_cast(double,float)", "error");
    verify("cell_cast(tensor<double>(x{},y[5]),float)", "tensor<float>(x{},y[5])");
    verify("cell_cast(tensor<float>(x{},y[5]),double)", "tensor<double>(x{},y[5])");
    verify("cell_cast(tensor<float>(x{},y[5]),float)", "tensor<float>(x{},y[5])");
    verify("cell_cast(tensor<float>(x{},y[5]),bfloat16)", "tensor<bfloat16>(x{},y[5])");
    verify("cell_cast(tensor<float>(x{},y[5]),int8)", "tensor<int8>(x{},y[5])");
}

TEST(NodeTypesTest, require_that_tensor_cell_order_resolves_correct_type)
{
    verify("cell_order(error,max)", "error");
    verify("cell_order(double,max)", "double");
    verify("cell_order(tensor<int8>(x{},y[3]),max)", "tensor<float>(x{},y[3])");
    verify("cell_order(tensor<bfloat16>(x{},y[3]),max)", "tensor<float>(x{},y[3])");
    verify("cell_order(tensor<float>(x{},y[3]),max)", "tensor<float>(x{},y[3])");
    verify("cell_order(tensor<double>(x{},y[3]),max)", "tensor<double>(x{},y[3])");
}

TEST(NodeTypesTest, require_that_tensor_map_subspace_resolves_correct_type)
{
    // double input
    verify("map_subspaces(double, f(a)(a))", "double");
    verify("map_subspaces(double, f(a)(tensor<int8>(y[2]):[a,a]))", "tensor<int8>(y[2])");

    // sparse input
    verify("map_subspaces(tensor<float>(x{}), f(a)(a))", "tensor<float>(x{})");
    verify("map_subspaces(tensor<int8>(x{}), f(a)(a))", "tensor<float>(x{})"); // NB: decay
    verify("map_subspaces(tensor<float>(x{}), f(a)(tensor<int8>(y[2]):[a,a]))", "tensor<int8>(x{},y[2])");

    // dense input
    verify("map_subspaces(tensor<float>(y[10]), f(a)(a))", "tensor<float>(y[10])");
    verify("map_subspaces(tensor<int8>(y[10]), f(a)(a))", "tensor<int8>(y[10])"); // NB: no decay
    verify("map_subspaces(tensor<float>(y[10]), f(a)(reduce(a,sum)))", "double");
    verify("map_subspaces(tensor<float>(y[10]), f(a)(cell_cast(a,int8)))", "tensor<int8>(y[10])");
    verify("map_subspaces(tensor<int8>(y[10]), f(a)(a*tensor<int8>(z[2]):[a{y:0},a{y:1}]))", "tensor<float>(y[10],z[2])");

    // mixed input
    verify("map_subspaces(tensor<float>(x{},y[10]), f(a)(a))", "tensor<float>(x{},y[10])");
    verify("map_subspaces(tensor<int8>(x{},y[10]), f(a)(a))", "tensor<int8>(x{},y[10])");
    verify("map_subspaces(tensor<int8>(x{},y[10]), f(a)(map_subspaces(a, f(b)(b))))", "tensor<int8>(x{},y[10])");
    verify("map_subspaces(tensor<int8>(x{},y[10]), f(a)(map(a, f(b)(b))))", "tensor<float>(x{},y[10])");
    verify("map_subspaces(tensor<float>(x{},y[10]), f(y)(cell_cast(y,int8)))", "tensor<int8>(x{},y[10])");
    verify("map_subspaces(tensor<float>(x{},y[10]), f(y)(reduce(y,sum)))", "tensor<float>(x{})");
    verify("map_subspaces(tensor<int8>(x{},y[10]), f(y)(reduce(y,sum)))", "tensor<float>(x{})");
    verify("map_subspaces(tensor<float>(x{},y[10]), f(y)(concat(concat(y,y,y),y,y)))", "tensor<float>(x{},y[30])");
    verify("map_subspaces(tensor<float>(x{},y[10]), f(y)(y*tensor<float>(z[5])(z+3)))", "tensor<float>(x{},y[10],z[5])");

    // error cases
    verify("map_subspaces(error, f(a)(a))", "error");
    verify("map_subspaces(double, f(a)(tensor(x[5])(x)+tensor(x[7])(x)))", "error");
    verify("map_subspaces(tensor<float>(x{}), f(a)(tensor(y{}):{a:3}))", "error");
    verify("map_subspaces(tensor<float>(y[10]), f(a)(a+tensor(y[7])(y)))", "error");
    verify("map_subspaces(tensor<float>(x{},y[10]), f(y)(y*tensor<float>(x[5])(x+3)))", "error");
}

TEST(NodeTypesTest, require_that_tensor_filter_subspace_resolves_correct_type)
{
    struct CheckFilterType {
        ValueType expect_type;
        CheckFilterType(const std::string &type_spec) : expect_type(ValueType::from_spec(type_spec)) {}
        void operator()(const Function &fun, const NodeTypes &types) const {
            auto filter = nodes::as<nodes::TensorFilterSubspaces>(fun.root());
            ASSERT_TRUE(filter != nullptr);
            // with the identity filter function, the result is the same type as the input
            // this will also test that lambda types are exported into outer function types
            const ValueType &filter_type = types.get_type(filter->lambda().root());
            EXPECT_FALSE(expect_type.is_error());
            EXPECT_EQ(expect_type, filter_type);
        }
    };
    verify("filter_subspaces(error, f(a)(a))", "error");
    verify("filter_subspaces(double, f(a)(a))", "error");
    verify("filter_subspaces(tensor<float>(y[3]), f(a)(a))", "error");
    verify("filter_subspaces(tensor<int8>(x{}), f(a)(a))", "tensor<int8>(x{})", CheckFilterType("double"));
    verify("filter_subspaces(tensor<bfloat16>(x{}), f(a)(a))", "tensor<bfloat16>(x{})", CheckFilterType("double"));
    verify("filter_subspaces(tensor<float>(x{}), f(a)(a))", "tensor<float>(x{})", CheckFilterType("double"));
    verify("filter_subspaces(tensor<double>(x{}), f(a)(a))", "tensor<double>(x{})", CheckFilterType("double"));
    verify("filter_subspaces(tensor<int8>(x{},y[3]), f(a)(a))", "tensor<int8>(x{},y[3])", CheckFilterType("tensor<int8>(y[3])"));
    verify("filter_subspaces(tensor<bfloat16>(x{},y[3]), f(a)(a))", "tensor<bfloat16>(x{},y[3])", CheckFilterType("tensor<bfloat16>(y[3])"));
    verify("filter_subspaces(tensor<float>(x{},y[3]), f(a)(a))", "tensor<float>(x{},y[3])", CheckFilterType("tensor<float>(y[3])"));
    verify("filter_subspaces(tensor<double>(x{},y[3]), f(a)(a))", "tensor<double>(x{},y[3])", CheckFilterType("tensor<double>(y[3])"));
}

TEST(NodeTypesTest, require_that_double_only_expressions_can_be_detected)
{
    auto plain_fun = Function::parse("1+2");
    auto complex_fun = Function::parse("reduce(a,sum)");
    NodeTypes plain_types(*plain_fun, {});
    NodeTypes complex_types(*complex_fun, {ValueType::make_type(CellType::DOUBLE, {{"x"}})});
    EXPECT_TRUE(plain_types.get_type(plain_fun->root()).is_double());
    EXPECT_TRUE(complex_types.get_type(complex_fun->root()).is_double());
    EXPECT_TRUE(plain_types.all_types_are_double());
    EXPECT_FALSE(complex_types.all_types_are_double());
}

TEST(NodeTypesTest, require_that_empty_type_repo_works_as_expected)
{
    NodeTypes types;
    auto function = Function::parse("1+2");
    EXPECT_FALSE(function->has_error());
    EXPECT_TRUE(types.get_type(function->root()).is_error());
    EXPECT_FALSE(types.all_types_are_double());
}

TEST(NodeTypesTest, require_that_types_for_a_subtree_can_be_exported)
{
    auto function = Function::parse("(1+2)+3");
    const auto &root = function->root();
    ASSERT_EQ(root.num_children(), 2u);
    const auto &n_1_2 = root.get_child(0);
    const auto &n_3 = root.get_child(1);
    ASSERT_EQ(n_1_2.num_children(), 2u);
    const auto &n_1 = n_1_2.get_child(0);
    const auto &n_2 = n_1_2.get_child(1);
    NodeTypes all_types(*function, {});
    NodeTypes some_types = all_types.export_types(n_1_2);
    EXPECT_EQ(all_types.errors().size(), 0u);
    EXPECT_EQ(some_types.errors().size(), 0u);
    for (const auto node: {&root, &n_3}) {
        EXPECT_TRUE(all_types.get_type(*node).is_double());
        EXPECT_TRUE(some_types.get_type(*node).is_error());
    }
    for (const auto node: {&n_1_2, &n_1, &n_2}) {
        EXPECT_TRUE(all_types.get_type(*node).is_double());
        EXPECT_TRUE(some_types.get_type(*node).is_double());
    }
}

TEST(NodeTypesTest, require_that_export_types_produces_an_error_for_missing_types)
{
    auto fun1 = Function::parse("1+2");
    auto fun2 = Function::parse("1+2");
    NodeTypes fun1_types(*fun1, {});
    NodeTypes bad_export = fun1_types.export_types(fun2->root());
    EXPECT_EQ(bad_export.errors().size(), 1u);
    print_errors(bad_export);
    EXPECT_TRUE(fun1_types.get_type(fun1->root()).is_double());
    EXPECT_TRUE(bad_export.get_type(fun2->root()).is_error());
}

GTEST_MAIN_RUN_ALL_TESTS()

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/eval/eval/node_types.h>

using namespace vespalib::eval;

/**
 * Hack to avoid parse-conflict between tensor type expressions and
 * lambda-generated tensors. This will patch leading identifier 'T' to
 * 't' directly in the input stream after we have concluded that this
 * is not a lambda-generated tensor in order to parse it out as a
 * valid tensor type. This may be reverted later if we add support for
 * parser rollback when we fail to parse a lambda-generated tensor.
 **/
void tensor_type_hack(const char *pos_in, const char *end_in) {
    if ((pos_in < end_in) && (*pos_in == 'T')) {
        const_cast<char *>(pos_in)[0] = 't';
    }
}

struct TypeSpecExtractor : public vespalib::eval::SymbolExtractor {
    void extract_symbol(const char *pos_in, const char *end_in,
                        const char *&pos_out, vespalib::string &symbol_out) const override
    {
        tensor_type_hack(pos_in, end_in);
        ValueType type = value_type::parse_spec(pos_in, end_in, pos_out);
        if (pos_out != nullptr) {
            symbol_out = type.to_spec();
        }
    }
};

void verify(const vespalib::string &type_expr_in, const vespalib::string &type_spec, bool replace_first = true) {
    vespalib::string type_expr = type_expr_in;
    // replace 'tensor' with 'Tensor' in type expression, see hack above
    size_t tensor_cnt = 0;
    for (size_t idx = type_expr.find("tensor");
         idx != type_expr.npos;
         idx = type_expr.find("tensor", idx + 1))
    {
        // setting 'replace_first' to false will avoid replacing the
        // first 'tensor' instance to let the parser handle it as an
        // actual tensor generator.
        if ((tensor_cnt++ > 0) || replace_first) {
            type_expr[idx] = 'T';
        }
    }
    Function function = Function::parse(type_expr, TypeSpecExtractor());
    if (!EXPECT_TRUE(!function.has_error())) {
        fprintf(stderr, "parse error: %s\n", function.get_error().c_str());
        return;
    }
    std::vector<ValueType> input_types;
    for (size_t i = 0; i < function.num_params(); ++i) {
        input_types.push_back(ValueType::from_spec(function.param_name(i)));
    }
    NodeTypes types(function, input_types);
    ValueType expected_type = ValueType::from_spec(type_spec);
    ValueType actual_type = types.get_type(function.root());
    EXPECT_EQUAL(expected_type, actual_type);
}

TEST("require that error nodes have error type") {
    Function function = Function::parse("1 2 3 4 5", TypeSpecExtractor());
    EXPECT_TRUE(function.has_error());
    NodeTypes types(function, std::vector<ValueType>());
    ValueType expected_type = ValueType::from_spec("error");
    ValueType actual_type = types.get_type(function.root());
    EXPECT_EQUAL(expected_type, actual_type);
}

TEST("require that leaf constants have appropriate type") {
    TEST_DO(verify("123", "double"));
    TEST_DO(verify("\"string values are hashed\"", "double"));
}

TEST("require that input parameters preserve their type") {
    TEST_DO(verify("error", "error"));
    TEST_DO(verify("double", "double"));
    TEST_DO(verify("tensor()", "double"));
    TEST_DO(verify("tensor(x{},y[10],z[5])", "tensor(x{},y[10],z[5])"));
    TEST_DO(verify("tensor<float>(x{},y[10],z[5])", "tensor<float>(x{},y[10],z[5])"));
}

TEST("require that if resolves to the appropriate type") {
    TEST_DO(verify("if(error,1,2)", "error"));
    TEST_DO(verify("if(1,error,2)", "error"));
    TEST_DO(verify("if(1,2,error)", "error"));
    TEST_DO(verify("if(double,1,2)", "double"));
    TEST_DO(verify("if(tensor(x[10]),1,2)", "double"));
    TEST_DO(verify("if(double,tensor(a{}),tensor(a{}))", "tensor(a{})"));
    TEST_DO(verify("if(double,tensor(a[2]),tensor(a[2]))", "tensor(a[2])"));
    TEST_DO(verify("if(double,tensor<float>(a[2]),tensor<float>(a[2]))", "tensor<float>(a[2])"));
    TEST_DO(verify("if(double,tensor(a[2]),tensor<float>(a[2]))", "error"));
    TEST_DO(verify("if(double,tensor(a[2]),tensor(a[3]))", "error"));
    TEST_DO(verify("if(double,tensor(a[2]),tensor(a{}))", "error"));
    TEST_DO(verify("if(double,tensor(a{}),tensor(b{}))", "error"));
    TEST_DO(verify("if(double,tensor(a{}),double)", "error"));
}

TEST("require that reduce resolves correct type") {
    TEST_DO(verify("reduce(error,sum)", "error"));
    TEST_DO(verify("reduce(tensor(x{}),sum)", "double"));
    TEST_DO(verify("reduce(double,sum)", "double"));
    TEST_DO(verify("reduce(error,sum,x)", "error"));
    TEST_DO(verify("reduce(double,sum,x)", "error"));
    TEST_DO(verify("reduce(tensor(x{},y{},z{}),sum,y)", "tensor(x{},z{})"));
    TEST_DO(verify("reduce(tensor(x{},y{},z{}),sum,x,z)", "tensor(y{})"));
    TEST_DO(verify("reduce(tensor(x{},y{},z{}),sum,y,z,x)", "double"));
    TEST_DO(verify("reduce(tensor(x{},y{},z{}),sum,w)", "error"));
    TEST_DO(verify("reduce(tensor(x{}),sum,x)", "double"));
    TEST_DO(verify("reduce(tensor<float>(x{},y{},z{}),sum,x,z)", "tensor<float>(y{})"));
    TEST_DO(verify("reduce(tensor<float>(x{}),sum,x)", "double"));
    TEST_DO(verify("reduce(tensor<float>(x{}),sum)", "double"));
}

TEST("require that rename resolves correct type") {
    TEST_DO(verify("rename(error,x,y)", "error"));
    TEST_DO(verify("rename(double,x,y)", "error"));
    TEST_DO(verify("rename(tensor(x{},y[1],z[5]),a,b)", "error"));
    TEST_DO(verify("rename(tensor(x{},y[1],z[5]),x,y)", "error"));
    TEST_DO(verify("rename(tensor(x{},y[1],z[5]),x,x)", "tensor(x{},y[1],z[5])"));
    TEST_DO(verify("rename(tensor(x{},y[1],z[5]),x,w)", "tensor(w{},y[1],z[5])"));
    TEST_DO(verify("rename(tensor(x{},y[1],z[5]),y,w)", "tensor(x{},w[1],z[5])"));
    TEST_DO(verify("rename(tensor(x{},y[1],z[5]),z,w)", "tensor(x{},y[1],w[5])"));
    TEST_DO(verify("rename(tensor(x{},y[1],z[5]),(x,y,z),(z,y,x))", "tensor(z{},y[1],x[5])"));
    TEST_DO(verify("rename(tensor(x{},y[1],z[5]),(x,z),(z,x))", "tensor(z{},y[1],x[5])"));
    TEST_DO(verify("rename(tensor(x{},y[1],z[5]),(x,y,z),(a,b,c))", "tensor(a{},b[1],c[5])"));
    TEST_DO(verify("rename(tensor<float>(x{},y[1],z[5]),(x,y,z),(a,b,c))", "tensor<float>(a{},b[1],c[5])"));
}

vespalib::string strfmt(const char *pattern, const char *a) {
    return vespalib::make_string(pattern, a);
}

vespalib::string strfmt(const char *pattern, const char *a, const char *b) {
    return vespalib::make_string(pattern, a, b);
}

void verify_op1(const char *pattern) {
    TEST_DO(verify(strfmt(pattern, "error"), "error"));
    TEST_DO(verify(strfmt(pattern, "double"), "double"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{},y[10],z[1])"), "tensor(x{},y[10],z[1])"));
    TEST_DO(verify(strfmt(pattern, "tensor<float>(x{},y[10],z[1])"), "tensor<float>(x{},y[10],z[1])"));
}

void verify_op2(const char *pattern) {
    TEST_DO(verify(strfmt(pattern, "error", "error"), "error"));
    TEST_DO(verify(strfmt(pattern, "double", "error"), "error"));
    TEST_DO(verify(strfmt(pattern, "error", "double"), "error"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{})", "error"), "error"));
    TEST_DO(verify(strfmt(pattern, "error", "tensor(x{})"), "error"));
    TEST_DO(verify(strfmt(pattern, "double", "double"), "double"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{})", "double"), "tensor(x{})"));
    TEST_DO(verify(strfmt(pattern, "double", "tensor(x{})"), "tensor(x{})"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{})", "tensor(x{})"), "tensor(x{})"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{})", "tensor(y{})"), "tensor(x{},y{})"));
    TEST_DO(verify(strfmt(pattern, "tensor(x[5])", "tensor(x[5])"), "tensor(x[5])"));
    TEST_DO(verify(strfmt(pattern, "tensor(x[3])", "tensor(x[5])"), "error"));
    TEST_DO(verify(strfmt(pattern, "tensor(x[5])", "tensor(x[3])"), "error"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{})", "tensor(x[5])"), "error"));
    TEST_DO(verify(strfmt(pattern, "tensor<float>(x[5])", "tensor<float>(x[5])"), "tensor<float>(x[5])"));
    TEST_DO(verify(strfmt(pattern, "tensor<float>(x[5])", "tensor(x[5])"), "tensor(x[5])"));
    TEST_DO(verify(strfmt(pattern, "tensor<float>(x[5])", "double"), "tensor<float>(x[5])"));
}

TEST("require that various operations resolve appropriate type") {
    TEST_DO(verify_op1("-%s"));          // Neg
    TEST_DO(verify_op1("!%s"));          // Not
    TEST_DO(verify_op2("%s+%s"));        // Add
    TEST_DO(verify_op2("%s-%s"));        // Sub
    TEST_DO(verify_op2("%s*%s"));        // Mul
    TEST_DO(verify_op2("%s/%s"));        // Div
    TEST_DO(verify_op2("%s^%s"));        // Pow
    TEST_DO(verify_op2("%s==%s"));       // Equal
    TEST_DO(verify_op2("%s!=%s"));       // NotEqual
    TEST_DO(verify_op2("%s~=%s"));       // Approx
    TEST_DO(verify_op2("%s<%s"));        // Less
    TEST_DO(verify_op2("%s<=%s"));       // LessEqual
    TEST_DO(verify_op2("%s>%s"));        // Greater
    TEST_DO(verify_op2("%s>=%s"));       // GreaterEqual
    TEST_DO(verify_op2("%s&&%s"));       // And
    TEST_DO(verify_op2("%s||%s"));       // Or
    TEST_DO(verify_op1("cos(%s)"));      // Cos
    TEST_DO(verify_op1("sin(%s)"));      // Sin
    TEST_DO(verify_op1("tan(%s)"));      // Tan
    TEST_DO(verify_op1("cosh(%s)"));     // Cosh
    TEST_DO(verify_op1("sinh(%s)"));     // Sinh
    TEST_DO(verify_op1("tanh(%s)"));     // Tanh
    TEST_DO(verify_op1("acos(%s)"));     // Acos
    TEST_DO(verify_op1("asin(%s)"));     // Asin
    TEST_DO(verify_op1("atan(%s)"));     // Atan
    TEST_DO(verify_op1("exp(%s)"));      // Exp
    TEST_DO(verify_op1("log10(%s)"));    // Log10
    TEST_DO(verify_op1("log(%s)"));      // Log
    TEST_DO(verify_op1("sqrt(%s)"));     // Sqrt
    TEST_DO(verify_op1("ceil(%s)"));     // Ceil
    TEST_DO(verify_op1("fabs(%s)"));     // Fabs
    TEST_DO(verify_op1("floor(%s)"));    // Floor
    TEST_DO(verify_op2("atan2(%s,%s)")); // Atan2
    TEST_DO(verify_op2("ldexp(%s,%s)")); // Ldexp
    TEST_DO(verify_op2("pow(%s,%s)"));   // Pow2
    TEST_DO(verify_op2("fmod(%s,%s)"));  // Fmod
    TEST_DO(verify_op2("min(%s,%s)"));   // min
    TEST_DO(verify_op2("max(%s,%s)"));   // max
    TEST_DO(verify_op1("isNan(%s)"));    // IsNan
    TEST_DO(verify_op1("relu(%s)"));     // Relu
    TEST_DO(verify_op1("sigmoid(%s)"));  // Sigmoid
    TEST_DO(verify_op1("elu(%s)"));      // Elu
}

TEST("require that map resolves correct type") {
    TEST_DO(verify_op1("map(%s,f(x)(sin(x)))"));
}

TEST("require that set membership resolves correct type") {
    TEST_DO(verify_op1("%s in [1,2,3]"));
}

TEST("require that join resolves correct type") {
    TEST_DO(verify_op2("join(%s,%s,f(x,y)(x+y))"));
}

TEST("require that lambda tensor resolves correct type") {
    TEST_DO(verify("tensor(x[5])(1.0)", "tensor(x[5])", false));
    TEST_DO(verify("tensor(x[5],y[10])(1.0)", "tensor(x[5],y[10])", false));
    TEST_DO(verify("tensor(x[5],y[10],z[15])(1.0)", "tensor(x[5],y[10],z[15])", false));
    TEST_DO(verify("tensor<double>(x[5],y[10],z[15])(1.0)", "tensor(x[5],y[10],z[15])", false));
    TEST_DO(verify("tensor<float>(x[5],y[10],z[15])(1.0)", "tensor<float>(x[5],y[10],z[15])", false));
}

TEST("require that tensor create resolves correct type") {
    TEST_DO(verify("tensor(x[3]):{{x:0}:double,{x:1}:double,{x:2}:double}", "tensor(x[3])", false));
    TEST_DO(verify("tensor(x{}):{{x:a}:double,{x:b}:double,{x:c}:double}", "tensor(x{})", false));
    TEST_DO(verify("tensor(x{},y[2]):{{x:a,y:0}:double,{x:a,y:1}:double}", "tensor(x{},y[2])", false));
    TEST_DO(verify("tensor<float>(x[3]):{{x:0}:double,{x:1}:double,{x:2}:double}", "tensor<float>(x[3])", false));
    TEST_DO(verify("tensor(x[3]):{{x:0}:double+double,{x:1}:double-double,{x:2}:double/double}", "tensor(x[3])", false));
    TEST_DO(verify("tensor(x[3]):{{x:0}:double,{x:1}:reduce(tensor(x[2]),sum),{x:2}:double}", "tensor(x[3])", false));
    TEST_DO(verify("tensor(x[3]):{{x:0}:double,{x:1}:tensor(x[2]),{x:2}:double}", "error", false));
    TEST_DO(verify("tensor(x[3]):{{x:0}:double,{x:1}:error,{x:2}:double}", "error", false));
}

TEST("require that tensor concat resolves correct type") {
    TEST_DO(verify("concat(double,double,x)", "tensor(x[2])"));
    TEST_DO(verify("concat(tensor(x[2]),tensor(x[3]),x)", "tensor(x[5])"));
    TEST_DO(verify("concat(tensor(x[2]),tensor(x[2]),y)", "tensor(x[2],y[2])"));
    TEST_DO(verify("concat(tensor(x[2]),tensor(x[3]),y)", "error"));
    TEST_DO(verify("concat(tensor(x[2]),tensor(x{}),x)", "error"));
    TEST_DO(verify("concat(tensor(x[2]),tensor(y{}),x)", "tensor(x[3],y{})"));
    TEST_DO(verify("concat(tensor<float>(x[2]),tensor<float>(x[3]),x)", "tensor<float>(x[5])"));
    TEST_DO(verify("concat(tensor<float>(x[2]),tensor(x[3]),x)", "tensor(x[5])"));
    TEST_DO(verify("concat(tensor<float>(x[2]),double,x)", "tensor<float>(x[3])"));
}

TEST("require that double only expressions can be detected") {
    Function plain_fun = Function::parse("1+2");
    Function complex_fun = Function::parse("reduce(a,sum)");
    NodeTypes plain_types(plain_fun, {});
    NodeTypes complex_types(complex_fun, {ValueType::tensor_type({{"x"}})});
    EXPECT_TRUE(plain_types.get_type(plain_fun.root()).is_double());
    EXPECT_TRUE(complex_types.get_type(complex_fun.root()).is_double());
    EXPECT_TRUE(plain_types.all_types_are_double());
    EXPECT_FALSE(complex_types.all_types_are_double());
}

TEST("require that empty type repo works as expected") {
    NodeTypes types;
    Function function = Function::parse("1+2");
    EXPECT_FALSE(function.has_error());
    EXPECT_TRUE(types.get_type(function.root()).is_error());
    EXPECT_FALSE(types.all_types_are_double());
}

TEST_MAIN() { TEST_RUN_ALL(); }

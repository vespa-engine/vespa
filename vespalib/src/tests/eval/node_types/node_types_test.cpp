// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/eval/function.h>
#include <vespa/vespalib/eval/value_type.h>
#include <vespa/vespalib/eval/value_type_spec.h>
#include <vespa/vespalib/eval/node_types.h>

using namespace vespalib::eval;

struct TypeSpecExtractor : public vespalib::eval::SymbolExtractor {
    void extract_symbol(const char *pos_in, const char *end_in,
                        const char *&pos_out, vespalib::string &symbol_out) const override
    {
        ValueType type = value_type::parse_spec(pos_in, end_in, pos_out);
        if (pos_out != nullptr) {
            symbol_out = type.to_spec();
        }
    }
};

void verify(const vespalib::string &type_expr, const vespalib::string &type_spec) {
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
    TEST_DO(verify("{{x:1,y:2}:3}", "tensor"));
}

TEST("require that input parameters preserve their type") {
    TEST_DO(verify("any", "any"));
    TEST_DO(verify("error", "error"));
    TEST_DO(verify("double", "double"));
    TEST_DO(verify("tensor", "tensor"));
    TEST_DO(verify("tensor(x{},y[10],z[])", "tensor(x{},y[10],z[])"));
}

TEST("require that arrays are double (size) unless they contain an error") {
    TEST_DO(verify("[1,2,3]", "double"));
    TEST_DO(verify("[any,tensor,double]", "double"));
    TEST_DO(verify("[1,error,3]", "error"));
}

TEST("require that if resolves to the appropriate type") {
    TEST_DO(verify("if(error,1,2)", "error"));
    TEST_DO(verify("if(1,error,2)", "error"));
    TEST_DO(verify("if(1,2,error)", "error"));
    TEST_DO(verify("if(any,1,2)", "double"));
    TEST_DO(verify("if(double,1,2)", "double"));
    TEST_DO(verify("if(tensor,1,2)", "double"));
    TEST_DO(verify("if(double,tensor,tensor)", "tensor"));
    TEST_DO(verify("if(double,any,any)", "any"));
    TEST_DO(verify("if(double,tensor(a{}),tensor(a{}))", "tensor(a{})"));
    TEST_DO(verify("if(double,tensor(a{}),tensor(b{}))", "tensor"));
    TEST_DO(verify("if(double,tensor(a{}),tensor)", "tensor"));
    TEST_DO(verify("if(double,tensor,tensor(a{}))", "tensor"));
    TEST_DO(verify("if(double,tensor,any)", "any"));
    TEST_DO(verify("if(double,any,tensor)", "any"));
    TEST_DO(verify("if(double,tensor,double)", "any"));
    TEST_DO(verify("if(double,double,tensor)", "any"));
    TEST_DO(verify("if(double,double,any)", "any"));
    TEST_DO(verify("if(double,any,double)", "any"));
}

TEST("require that let expressions propagate type correctly") {
    TEST_DO(verify("let(a,10,a)", "double"));
    TEST_DO(verify("let(a,double,a)", "double"));
    TEST_DO(verify("let(a,any,a)", "any"));
    TEST_DO(verify("let(a,error,a)", "error"));
    TEST_DO(verify("let(a,tensor,let(b,double,a))", "tensor"));
    TEST_DO(verify("let(a,tensor,let(b,double,b))", "double"));
    TEST_DO(verify("let(a,tensor,let(b,a,b))", "tensor"));
}

TEST("require that set membership resolves to double unless error") {
    TEST_DO(verify("1 in [1,2,3]", "double"));
    TEST_DO(verify("1 in [tensor,tensor,tensor]", "double"));
    TEST_DO(verify("1 in tensor", "double"));
    TEST_DO(verify("tensor in 1", "double"));
    TEST_DO(verify("tensor in [1,2,any]", "double"));
    TEST_DO(verify("any in [1,tensor,any]", "double"));
    TEST_DO(verify("error in [1,tensor,any]", "error"));
    TEST_DO(verify("any in [tensor,error,any]", "error"));
}

TEST("require that sum resolves correct type") {
    TEST_DO(verify("sum(error)", "error"));
    TEST_DO(verify("sum(tensor)", "double"));
    TEST_DO(verify("sum(double)", "double"));
    TEST_DO(verify("sum(any)", "any"));
}

TEST("require that dimension sum resolves correct type") {
    TEST_DO(verify("sum(error,x)", "error"));
    TEST_DO(verify("sum(tensor,x)", "any"));
    TEST_DO(verify("sum(any,x)", "any"));
    TEST_DO(verify("sum(double,x)", "error"));
    TEST_DO(verify("sum(tensor(x{},y{},z{}),y)", "tensor(x{},z{})"));
    TEST_DO(verify("sum(tensor(x{},y{},z{}),w)", "error"));
    TEST_DO(verify("sum(tensor(x{}),x)", "double"));
}

TEST("require that tensor match resolves correct type") {
    TEST_DO(verify("match(error,tensor)", "error"));
    TEST_DO(verify("match(tensor,error)", "error"));
    TEST_DO(verify("match(any,any)", "any"));
    TEST_DO(verify("match(any,tensor)", "any"));
    TEST_DO(verify("match(tensor,any)", "any"));
    TEST_DO(verify("match(tensor,tensor)", "any"));
    TEST_DO(verify("match(double,double)", "double"));
    TEST_DO(verify("match(tensor,double)", "error"));
    TEST_DO(verify("match(double,tensor)", "error"));
    TEST_DO(verify("match(double,any)", "any"));
    TEST_DO(verify("match(any,double)", "any"));
    TEST_DO(verify("match(tensor(x{},y{}),tensor(x{},y{}))", "tensor(x{},y{})"));
    TEST_DO(verify("match(tensor(x{},y{}),tensor(x{},y[]))", "error"));
    TEST_DO(verify("match(tensor(x{},y{}),tensor(x{}))", "error"));
    TEST_DO(verify("match(tensor(x{}),tensor(y{}))", "error"));
    TEST_DO(verify("match(tensor,tensor(x{},y{}))", "any"));
    TEST_DO(verify("match(tensor(x{},y{}),tensor)", "any"));
}

vespalib::string strfmt(const char *pattern, const char *a) {
    return vespalib::make_string(pattern, a);
}

vespalib::string strfmt(const char *pattern, const char *a, const char *b) {
    return vespalib::make_string(pattern, a, b);
}

void verify_op1(const char *pattern) {
    TEST_DO(verify(strfmt(pattern, "error"), "error"));
    TEST_DO(verify(strfmt(pattern, "any"), "any"));
    TEST_DO(verify(strfmt(pattern, "double"), "double"));
    TEST_DO(verify(strfmt(pattern, "tensor"), "tensor"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{},y[10],z[])"), "tensor(x{},y[10],z[])"));
}

void verify_op2_common(const char *pattern) {
    TEST_DO(verify(strfmt(pattern, "error", "error"), "error"));
    TEST_DO(verify(strfmt(pattern, "any", "error"), "error"));
    TEST_DO(verify(strfmt(pattern, "error", "any"), "error"));
    TEST_DO(verify(strfmt(pattern, "double", "error"), "error"));
    TEST_DO(verify(strfmt(pattern, "error", "double"), "error"));
    TEST_DO(verify(strfmt(pattern, "tensor", "error"), "error"));
    TEST_DO(verify(strfmt(pattern, "error", "tensor"), "error"));
    TEST_DO(verify(strfmt(pattern, "any", "any"), "any"));
    TEST_DO(verify(strfmt(pattern, "any", "double"), "any"));
    TEST_DO(verify(strfmt(pattern, "double", "any"), "any"));
    TEST_DO(verify(strfmt(pattern, "any", "tensor"), "any"));
    TEST_DO(verify(strfmt(pattern, "tensor", "any"), "any"));
    TEST_DO(verify(strfmt(pattern, "double", "double"), "double"));
    TEST_DO(verify(strfmt(pattern, "tensor", "double"), "tensor"));
    TEST_DO(verify(strfmt(pattern, "double", "tensor"), "tensor"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{})", "double"), "tensor(x{})"));
    TEST_DO(verify(strfmt(pattern, "double", "tensor(x{})"), "tensor(x{})"));
}

void verify_op2_default(const char *pattern) {
    TEST_DO(verify_op2_common(pattern));
    TEST_DO(verify(strfmt(pattern, "tensor", "tensor"), "error"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{})", "tensor(x{})"), "error"));
}

void verify_op2_union(const char *pattern) {
    TEST_DO(verify_op2_common(pattern));
    TEST_DO(verify(strfmt(pattern, "tensor", "tensor"), "any"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{})", "tensor(x{})"), "tensor(x{})"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{})", "tensor(y{})"), "tensor(x{},y{})"));
    TEST_DO(verify(strfmt(pattern, "tensor(x[3])", "tensor(x[5])"), "tensor(x[3])"));
    TEST_DO(verify(strfmt(pattern, "tensor(x[])", "tensor(x[5])"), "tensor(x[])"));
    TEST_DO(verify(strfmt(pattern, "tensor(x[5])", "tensor(x[3])"), "tensor(x[3])"));
    TEST_DO(verify(strfmt(pattern, "tensor(x[5])", "tensor(x[])"), "tensor(x[])"));
    TEST_DO(verify(strfmt(pattern, "tensor(x{})", "tensor(x[5])"), "error"));
}

TEST("require that various operations resolve appropriate type") {
    TEST_DO(verify_op1("-%s"));                  // Neg
    TEST_DO(verify_op1("!%s"));                  // Not
    TEST_DO(verify_op2_union("%s+%s"));          // Add
    TEST_DO(verify_op2_union("%s-%s"));          // Sub
    TEST_DO(verify_op2_union("%s*%s"));          // Mul
    TEST_DO(verify_op2_default("%s/%s"));        // Div
    TEST_DO(verify_op2_default("%s^%s"));        // Pow
    TEST_DO(verify_op2_default("%s==%s"));       // Equal
    TEST_DO(verify_op2_default("%s!=%s"));       // NotEqual
    TEST_DO(verify_op2_default("%s~=%s"));       // Approx
    TEST_DO(verify_op2_default("%s<%s"));        // Less
    TEST_DO(verify_op2_default("%s<=%s"));       // LessEqual
    TEST_DO(verify_op2_default("%s>%s"));        // Greater
    TEST_DO(verify_op2_default("%s>=%s"));       // GreaterEqual
    TEST_DO(verify_op2_default("%s&&%s"));       // And
    TEST_DO(verify_op2_default("%s||%s"));       // Or
    TEST_DO(verify_op1("cos(%s)"));              // Cos
    TEST_DO(verify_op1("sin(%s)"));              // Sin
    TEST_DO(verify_op1("tan(%s)"));              // Tan
    TEST_DO(verify_op1("cosh(%s)"));             // Cosh
    TEST_DO(verify_op1("sinh(%s)"));             // Sinh
    TEST_DO(verify_op1("tanh(%s)"));             // Tanh
    TEST_DO(verify_op1("acos(%s)"));             // Acos
    TEST_DO(verify_op1("asin(%s)"));             // Asin
    TEST_DO(verify_op1("atan(%s)"));             // Atan
    TEST_DO(verify_op1("exp(%s)"));              // Exp
    TEST_DO(verify_op1("log10(%s)"));            // Log10
    TEST_DO(verify_op1("log(%s)"));              // Log
    TEST_DO(verify_op1("sqrt(%s)"));             // Sqrt
    TEST_DO(verify_op1("ceil(%s)"));             // Ceil
    TEST_DO(verify_op1("fabs(%s)"));             // Fabs
    TEST_DO(verify_op1("floor(%s)"));            // Floor
    TEST_DO(verify_op2_default("atan2(%s,%s)")); // Atan2
    TEST_DO(verify_op2_default("ldexp(%s,%s)")); // Ldexp
    TEST_DO(verify_op2_default("pow(%s,%s)"));   // Pow2
    TEST_DO(verify_op2_default("fmod(%s,%s)"));  // Fmod
    TEST_DO(verify_op2_union("min(%s,%s)"));     // min
    TEST_DO(verify_op2_union("max(%s,%s)"));     // max
    TEST_DO(verify_op1("isNan(%s)"));            // IsNan
    TEST_DO(verify_op1("relu(%s)"));             // Relu
    TEST_DO(verify_op1("sigmoid(%s)"));          // Sigmoid
}

TEST_MAIN() { TEST_RUN_ALL(); }

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/eval_spec.h>
#include <vespa/eval/eval/basic_nodes.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <iostream>

using namespace vespalib::eval;
using vespalib::Stash;
using vespalib::tensor::DefaultTensorEngine;

//-----------------------------------------------------------------------------

struct MyEvalTest : test::EvalSpec::EvalTest {
    size_t pass_cnt = 0;
    size_t fail_cnt = 0;
    bool print_pass = false;
    bool print_fail = false;

    virtual void next_expression(const std::vector<vespalib::string> &param_names,
                                 const vespalib::string &expression) override
    {
        Function function = Function::parse(param_names, expression);
        ASSERT_TRUE(!function.has_error());
        bool is_supported = true;
        bool has_issues = InterpretedFunction::detect_issues(function);
        if (is_supported == has_issues) {
            const char *supported_str = is_supported ? "supported" : "not supported";
            const char *issues_str = has_issues ? "has issues" : "does not have issues";
            print_fail && fprintf(stderr, "expression %s is %s, but %s\n",
                                  expression.c_str(), supported_str, issues_str);
            ++fail_cnt;
        }
    }

    virtual void handle_case(const std::vector<vespalib::string> &param_names,
                             const std::vector<double> &param_values,
                             const vespalib::string &expression,
                             double expected_result) override
    {
        Function function = Function::parse(param_names, expression);
        ASSERT_TRUE(!function.has_error());
        bool is_supported = true;
        bool has_issues = InterpretedFunction::detect_issues(function);
        if (is_supported && !has_issues) {
            vespalib::string desc = as_string(param_names, param_values, expression);
            SimpleParams params(param_values);
            verify_result(SimpleTensorEngine::ref(), function, false,    "[untyped simple] "+desc, params, expected_result);
            verify_result(DefaultTensorEngine::ref(), function, false,   "[untyped prod]   "+desc, params, expected_result);
            verify_result(DefaultTensorEngine::ref(), function, true,    "[typed prod]     "+desc, params, expected_result);
            verify_tensor_function(DefaultTensorEngine::ref(), function, "[tensor function]"+desc, params, expected_result);
        }
    }

    void report_result(bool is_double, double result, double expect, const vespalib::string &desc)
    {
        if (is_double && is_same(expect, result)) {
            print_pass && fprintf(stderr, "verifying: %s -> %g ... PASS\n",
                                  desc.c_str(), expect);
            ++pass_cnt;
        } else {
            print_fail && fprintf(stderr, "verifying: %s -> %g ... FAIL: got %g\n",
                                  desc.c_str(), expect, result);
            ++fail_cnt;
        }
    }

    void verify_result(const TensorEngine &engine,
                       const Function &function,
                       bool typed,
                       const vespalib::string &description,
                       const SimpleParams &params,
                       double expected_result)
    {
        NodeTypes node_types = typed
                               ? NodeTypes(function, std::vector<ValueType>(params.params.size(), ValueType::double_type()))
                               : NodeTypes();
        InterpretedFunction ifun(engine, function, node_types);
        ASSERT_EQUAL(ifun.num_params(), params.params.size());
        InterpretedFunction::Context ictx(ifun);
        const Value &result_value = ifun.eval(ictx, params);
        report_result(result_value.is_double(), result_value.as_double(), expected_result, description);
    }

    void verify_tensor_function(const TensorEngine &engine,
                                const Function &function,
                                const vespalib::string &description,
                                const SimpleParams &params,
                                double expected_result)
    {
        Stash stash;
        NodeTypes node_types = NodeTypes(function, std::vector<ValueType>(params.params.size(), ValueType::double_type()));
        const auto &tfun = make_tensor_function(engine, function.root(), node_types, stash);
        const Value &result_value = tfun.eval(params, stash);
        report_result(result_value.is_double(), result_value.as_double(), expected_result, description);
    }
};

TEST_FF("require that compiled evaluation passes all conformance tests", MyEvalTest(), test::EvalSpec()) {
    f1.print_fail = true;
    f2.add_all_cases();
    f2.each_case(f1);
    EXPECT_GREATER(f1.pass_cnt, 1000u);
    EXPECT_EQUAL(0u, f1.fail_cnt);
}

//-----------------------------------------------------------------------------

TEST("require that invalid function evaluates to a error") {
    std::vector<vespalib::string> params({"x", "y", "z", "w"});
    Function function = Function::parse(params, "x & y");
    EXPECT_TRUE(function.has_error());
    InterpretedFunction ifun(SimpleTensorEngine::ref(), function, NodeTypes());
    InterpretedFunction::Context ctx(ifun);
    SimpleParams my_params({1,2,3,4});
    const Value &result = ifun.eval(ctx, my_params);
    EXPECT_TRUE(result.is_error());
    EXPECT_EQUAL(error_value, result.as_double());
}

//-----------------------------------------------------------------------------

size_t count_ifs(const vespalib::string &expr, std::initializer_list<double> params_in) {
    Function fun = Function::parse(expr);
    InterpretedFunction ifun(SimpleTensorEngine::ref(), fun, NodeTypes());
    InterpretedFunction::Context ctx(ifun);
    SimpleParams params(params_in);
    ifun.eval(ctx, params);
    return ctx.if_cnt();
}

TEST("require that if_cnt in eval context is updated correctly") {
    EXPECT_EQUAL(0u, count_ifs("1", {}));
    EXPECT_EQUAL(1u, count_ifs("if(a<10,if(a<9,if(a<8,if(a<7,5,4),3),2),1)", {10}));
    EXPECT_EQUAL(2u, count_ifs("if(a<10,if(a<9,if(a<8,if(a<7,5,4),3),2),1)", {9}));
    EXPECT_EQUAL(3u, count_ifs("if(a<10,if(a<9,if(a<8,if(a<7,5,4),3),2),1)", {8}));
    EXPECT_EQUAL(4u, count_ifs("if(a<10,if(a<9,if(a<8,if(a<7,5,4),3),2),1)", {7}));
    EXPECT_EQUAL(4u, count_ifs("if(a<10,if(a<9,if(a<8,if(a<7,5,4),3),2),1)", {6}));
}

//-----------------------------------------------------------------------------

TEST("require that interpreted function instructions have expected size") {
    EXPECT_EQUAL(sizeof(InterpretedFunction::Instruction), 16u);
}

TEST("require that function pointers can be passed as instruction parameters") {
    EXPECT_EQUAL(sizeof(&operation::Add::f), sizeof(uint64_t));
}

TEST("require that basic addition works") {
    Function function = Function::parse("a+10");
    InterpretedFunction interpreted(SimpleTensorEngine::ref(), function, NodeTypes());
    InterpretedFunction::Context ctx(interpreted);
    SimpleParams params_20({20});
    SimpleParams params_40({40});
    EXPECT_EQUAL(interpreted.eval(ctx, params_20).as_double(), 30.0);
    EXPECT_EQUAL(interpreted.eval(ctx, params_40).as_double(), 50.0);
}

//-----------------------------------------------------------------------------

struct InnerProduct {
    const TensorEngine &engine;
    Function            function;
    TensorSpec          a;
    TensorSpec          b;
    TensorSpec          expect;
    NodeTypes           types;
    InterpretedFunction interpreted;
    ~InnerProduct() {}
    InnerProduct(const vespalib::string &expr)
        : engine(SimpleTensorEngine::ref()),
          function(Function::parse({"a", "b"}, expr)),
          a("null"), b("null"), expect("null"),
          types(),
          interpreted(engine, function, types) {}
    InnerProduct(const vespalib::string &expr,
              TensorSpec a_in,
              TensorSpec b_in,
              TensorSpec expect_in)
        : engine(SimpleTensorEngine::ref()),
          function(Function::parse(expr)),
          a(a_in), b(b_in), expect(expect_in),
          types(function, {ValueType::from_spec(a.type()), ValueType::from_spec(a.type())}),
          interpreted(engine, function, types) {}
    void verify_optimized() const {
        EXPECT_EQUAL(1u, interpreted.program_size());
        InterpretedFunction::Context ctx(interpreted);
        Value::UP va = engine.from_spec(a);
        Value::UP vb = engine.from_spec(b);
        SimpleObjectParams params({*va,*vb});
        const Value &result = interpreted.eval(ctx, params);
        EXPECT_EQUAL(engine.to_spec(result), expect);
    }
    void verify_not_optimized() const {
        EXPECT_EQUAL(4u, interpreted.program_size());
    }
};

struct UntypedIP : InnerProduct {
    UntypedIP(const vespalib::string &expr) : InnerProduct(expr) {
        a = TensorSpec("double").add({}, 2.0);
        b = TensorSpec("double").add({}, 3.0);
        expect = TensorSpec("double").add({}, 6.0);
    }
};

struct DotProduct : InnerProduct {
    DotProduct(const vespalib::string &expr)
        : InnerProduct(expr,
                       TensorSpec("tensor(x[3])")
                       .add({{"x", 0}}, 5.0)
                       .add({{"x", 1}}, 3.0)
                       .add({{"x", 2}}, 2.0),
                       TensorSpec("tensor(x[3])")
                       .add({{"x", 0}}, 7.0)
                       .add({{"x", 1}}, 11.0)
                       .add({{"x", 2}}, 13.0),
                       TensorSpec("double")
                       .add({}, (5.0 * 7.0) + (3.0 * 11.0) + (2.0 * 13.0))) {}
};

struct XW : InnerProduct {
    XW(const vespalib::string &expr)
        : InnerProduct(expr,
                       TensorSpec("tensor(x[2])")
                       .add({{"x", 0}},  1.0)
                       .add({{"x", 1}},  2.0),
                       TensorSpec("tensor(x[2],y[3])")
                       .add({{"y", 0},{"x", 0}},  3.0)
                       .add({{"y", 0},{"x", 1}},  5.0)
                       .add({{"y", 1},{"x", 0}},  7.0)
                       .add({{"y", 1},{"x", 1}}, 11.0)
                       .add({{"y", 2},{"x", 0}}, 13.0)
                       .add({{"y", 2},{"x", 1}}, 17.0),
                       TensorSpec("tensor(y[3])")
                       .add({{"y", 0}}, (1.0 *  3.0) + (2.0 *  5.0))
                       .add({{"y", 1}}, (1.0 *  7.0) + (2.0 * 11.0))
                       .add({{"y", 2}}, (1.0 * 13.0) + (2.0 * 17.0))) {}
};

struct MatMul : InnerProduct {
    MatMul(const vespalib::string &expr)
        : InnerProduct(expr,
                       TensorSpec("tensor(x[2],y[2])")
                       .add({{"x", 0},{"y", 0}},  1.0)
                       .add({{"x", 0},{"y", 1}},  2.0)
                       .add({{"x", 1},{"y", 0}},  3.0)
                       .add({{"x", 1},{"y", 1}},  5.0),
                       TensorSpec("tensor(y[2],z[2])")
                       .add({{"y", 0},{"z", 0}},  7.0)
                       .add({{"y", 0},{"z", 1}}, 11.0)
                       .add({{"y", 1},{"z", 0}}, 13.0)
                       .add({{"y", 1},{"z", 1}}, 17.0),
                       TensorSpec("tensor(x[2],z[2])")
                       .add({{"x", 0},{"z", 0}}, (1.0 *  7.0) + (2.0 * 13.0))
                       .add({{"x", 0},{"z", 1}}, (1.0 * 11.0) + (2.0 * 17.0))
                       .add({{"x", 1},{"z", 0}}, (3.0 *  7.0) + (5.0 * 13.0))
                       .add({{"x", 1},{"z", 1}}, (3.0 * 11.0) + (5.0 * 17.0))) {}
};

TEST("require that inner product is not optimized for unknown types") {
    TEST_DO(UntypedIP("reduce(a*b,sum)").verify_not_optimized());
    TEST_DO(UntypedIP("reduce(join(a,b,f(x,y)(x*y)),sum)").verify_not_optimized());
}

TEST("require that dot product works with tensor function") {
    TEST_DO(DotProduct("reduce(a*b,sum)").verify_optimized());
    TEST_DO(DotProduct("reduce(join(a,b,f(x,y)(x*y)),sum)").verify_optimized());
    TEST_DO(DotProduct("reduce(b*a,sum)").verify_optimized());
    TEST_DO(DotProduct("reduce(join(b,a,f(x,y)(x*y)),sum)").verify_optimized());
    TEST_DO(DotProduct("reduce(join(a,b,f(x,y)(y*x)),sum)").verify_optimized());
    TEST_DO(DotProduct("reduce(join(b,a,f(x,y)(y*x)),sum)").verify_optimized());
    TEST_DO(DotProduct("reduce(a*b,sum,x)").verify_optimized());
    TEST_DO(DotProduct("reduce(join(a,b,f(x,y)(x*y)),sum,x)").verify_optimized());
    TEST_DO(DotProduct("reduce(b*a,sum,x)").verify_optimized());
    TEST_DO(DotProduct("reduce(join(b,a,f(x,y)(x*y)),sum,x)").verify_optimized());
    TEST_DO(DotProduct("reduce(join(a,b,f(x,y)(y*x)),sum,x)").verify_optimized());
    TEST_DO(DotProduct("reduce(join(b,a,f(x,y)(y*x)),sum,x)").verify_optimized());
}

TEST("require that vector matrix multiplication works with tensor function") {
    TEST_DO(XW("reduce(a*b,sum,x)").verify_optimized());
    TEST_DO(XW("reduce(join(a,b,f(x,y)(x*y)),sum,x)").verify_optimized());
    TEST_DO(XW("reduce(b*a,sum,x)").verify_optimized());
    TEST_DO(XW("reduce(join(b,a,f(x,y)(x*y)),sum,x)").verify_optimized());
    TEST_DO(XW("reduce(join(a,b,f(x,y)(y*x)),sum,x)").verify_optimized());
    TEST_DO(XW("reduce(join(b,a,f(x,y)(y*x)),sum,x)").verify_optimized());
}

TEST("require that matrix multiplication works with tensor function") {
    TEST_DO(MatMul("reduce(a*b,sum,y)").verify_optimized());
    TEST_DO(MatMul("reduce(join(a,b,f(x,y)(x*y)),sum,y)").verify_optimized());
    TEST_DO(MatMul("reduce(b*a,sum,y)").verify_optimized());
    TEST_DO(MatMul("reduce(join(b,a,f(x,y)(x*y)),sum,y)").verify_optimized());
    TEST_DO(MatMul("reduce(join(a,b,f(x,y)(y*x)),sum,y)").verify_optimized());
    TEST_DO(MatMul("reduce(join(b,a,f(x,y)(y*x)),sum,y)").verify_optimized());
}

TEST("require that expressions similar to inner product are not optimized") {
    TEST_DO(DotProduct("reduce(a*b,prod)").verify_not_optimized());
    TEST_DO(DotProduct("reduce(a*b,max)").verify_not_optimized());
    TEST_DO(DotProduct("reduce(a+b,sum)").verify_not_optimized());
    TEST_DO(DotProduct("reduce(join(a,b,f(x,y)(x+y)),sum)").verify_not_optimized());
    TEST_DO(DotProduct("reduce(join(a,b,f(x,y)(x*x)),sum)").verify_not_optimized());
    TEST_DO(DotProduct("reduce(join(a,b,f(x,y)(y*y)),sum)").verify_not_optimized());
    TEST_DO(DotProduct("reduce(join(a,b,f(x,y)(x*y*1)),sum)").verify_not_optimized());
}

//-----------------------------------------------------------------------------

TEST("require that functions with non-compilable lambdas cannot be interpreted") {
    auto good_map = Function::parse("map(a,f(x)(x+1))");
    auto good_join = Function::parse("join(a,b,f(x,y)(x+y))");
    auto good_tensor = Function::parse("tensor(a[10],b[10])(a+b)");
    auto bad_map = Function::parse("map(a,f(x)(map(x,f(i)(i+1))))");
    auto bad_join = Function::parse("join(a,b,f(x,y)(join(x,y,f(i,j)(i+j))))");
    auto bad_tensor = Function::parse("tensor(a[10],b[10])(join(a,b,f(i,j)(i+j)))");
    for (const Function *good: {&good_map, &good_join, &good_tensor}) {
        if (!EXPECT_TRUE(!good->has_error())) {
            fprintf(stderr, "parse error: %s\n", good->get_error().c_str());
        }
        EXPECT_TRUE(!InterpretedFunction::detect_issues(*good));
    }
    for (const Function *bad: {&bad_map, &bad_join, &bad_tensor}) {
        if (!EXPECT_TRUE(!bad->has_error())) {
            fprintf(stderr, "parse error: %s\n", bad->get_error().c_str());
        }
        EXPECT_TRUE(InterpretedFunction::detect_issues(*bad));
    }
    std::cerr << "Example function issues:" << std::endl
              << InterpretedFunction::detect_issues(bad_tensor).list
              << std::endl;
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }

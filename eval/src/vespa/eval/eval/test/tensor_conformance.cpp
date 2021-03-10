// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_conformance.h"
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/aggr.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include "tensor_model.h"
#include "test_io.h"
#include "reference_evaluation.h"

using vespalib::make_string_short::fmt;

namespace vespalib::eval::test {

namespace {

using slime::Cursor;
using slime::Inspector;
using slime::JsonFormat;

//-----------------------------------------------------------------------------

TensorSpec ref_eval(const vespalib::string &expr, const std::vector<TensorSpec> &params) {
    TensorSpec result = ReferenceEvaluation::eval(*Function::parse(expr), params);
    EXPECT_FALSE(ValueType::from_spec(result.type()).is_error());
    return result;
}

TensorSpec eval(const ValueBuilderFactory &factory, const vespalib::string &expr, const std::vector<TensorSpec> &params) {
    auto fun = Function::parse(expr);
    std::vector<ValueType> param_types;
    std::vector<Value::UP> param_values;
    std::vector<Value::CREF> param_refs;
    for (const auto &param: params) {
        param_types.push_back(ValueType::from_spec(param.type()));
        param_values.push_back(value_from_spec(param, factory));
        param_refs.emplace_back(*param_values.back());
    }
    NodeTypes types(*fun, param_types);
    const auto &expect_type = types.get_type(fun->root());
    ASSERT_FALSE(expect_type.is_error());
    InterpretedFunction ifun(factory, *fun, types);
    InterpretedFunction::Context ctx(ifun);
    const Value &result = ifun.eval(ctx, SimpleObjectParams{param_refs});
    EXPECT_EQUAL(result.type(), expect_type);
    return spec_from_value(result);
}

void verify_result(const ValueBuilderFactory &factory, const vespalib::string &expr, const std::vector<TensorSpec> &params, const TensorSpec &expect) {
    auto actual = eval(factory, expr, params);
    EXPECT_EQUAL(actual, expect);
}

void verify_result(const ValueBuilderFactory &factory, const vespalib::string &expr, const std::vector<TensorSpec> &params) {
    TEST_DO(verify_result(factory, expr, params, ref_eval(expr, params)));
}

//-----------------------------------------------------------------------------

// NaN value
const double my_nan = std::numeric_limits<double>::quiet_NaN();

uint8_t unhex(char c) {
    if (c >= '0' && c <= '9') {
        return (c - '0');
    }
    if (c >= 'A' && c <= 'F') {
        return ((c - 'A') + 10);
    }
    TEST_ERROR("bad hex char");
    return 0;
}

nbostream extract_data(const Memory &hex_dump) {
    nbostream data;
    if ((hex_dump.size > 2) && (hex_dump.data[0] == '0') && (hex_dump.data[1] == 'x')) {
        for (size_t i = 2; i < (hex_dump.size - 1); i += 2) {
            data << uint8_t((unhex(hex_dump.data[i]) << 4) | unhex(hex_dump.data[i + 1]));
        }
    }
    return data;
}

bool is_same(const nbostream &a, const nbostream &b) {
    return (Memory(a.peek(), a.size()) == Memory(b.peek(), b.size()));
}

// Test wrapper to avoid passing global test parameters around
struct TestContext {

    vespalib::string module_path;
    const ValueBuilderFactory &factory;

    TestContext(const vespalib::string &module_path_in, const ValueBuilderFactory &factory_in)
        : module_path(module_path_in), factory(factory_in) {}

    //-------------------------------------------------------------------------

    void verify_create_type(const vespalib::string &type_spec) {
        Value::UP value = value_from_spec(TensorSpec(type_spec), factory);
        EXPECT_EQUAL(type_spec, value->type().to_spec());
    }

    void test_tensor_create_type() {
        TEST_DO(verify_create_type("double"));
        TEST_DO(verify_create_type("tensor(x{})"));
        TEST_DO(verify_create_type("tensor(x{},y{})"));
        TEST_DO(verify_create_type("tensor<float>(x{},y{})"));
        TEST_DO(verify_create_type("tensor(x[5])"));
        TEST_DO(verify_create_type("tensor(x[5],y[10])"));
        TEST_DO(verify_create_type("tensor<float>(x[5],y[10])"));
        TEST_DO(verify_create_type("tensor(x{},y[10])"));
        TEST_DO(verify_create_type("tensor(x[5],y{})")); 
        TEST_DO(verify_create_type("tensor<float>(x[5],y{})"));
    }

    //-------------------------------------------------------------------------

    void test_reduce_op(Aggr aggr, const Sequence &seq) {
        std::vector<Layout> layouts = {
            {x(3)},
            {x(3),y(5)},
            {x(3),y(5),z(7)},
            float_cells({x(3),y(5),z(7)}),
            {x({"a","b","c"})},
            {x({"a","b","c"}),y({"foo","bar"})},
            {x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})},
            float_cells({x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}),
            {x(3),y({"foo", "bar"}),z(7)},
            {x({"a","b","c"}),y(5),z({"i","j","k","l"})},
            float_cells({x({"a","b","c"}),y(5),z({"i","j","k","l"})})
        };
        for (const Layout &layout: layouts) {
            TensorSpec input = spec(layout, seq);
            for (const Domain &domain: layout) {
                TEST_STATE(fmt("shape: %s, reduce dimension: %s",
                               infer_type(layout).c_str(), domain.name().c_str()).c_str());
                vespalib::string expr = fmt("reduce(a,%s,%s)",
                                            AggrNames::name_of(aggr)->c_str(), domain.name().c_str());
                TEST_DO(verify_result(factory, expr, {input}));
            }
            {
                TEST_STATE(fmt("shape: %s, reduce all dimensions",
                               infer_type(layout).c_str()).c_str());
                vespalib::string expr = fmt("reduce(a,%s)", AggrNames::name_of(aggr)->c_str());
                TEST_DO(verify_result(factory, expr, {input}));
            }
        }
    }

    void test_tensor_reduce() {
        TEST_DO(test_reduce_op(Aggr::AVG, N()));
        TEST_DO(test_reduce_op(Aggr::COUNT, N()));
        TEST_DO(test_reduce_op(Aggr::PROD, SigmoidF(N())));
        TEST_DO(test_reduce_op(Aggr::SUM, N()));
        TEST_DO(test_reduce_op(Aggr::MAX, N()));
        TEST_DO(test_reduce_op(Aggr::MEDIAN, N()));
        TEST_DO(test_reduce_op(Aggr::MIN, N()));
    }

    //-------------------------------------------------------------------------

    void test_map_op_inner(const vespalib::string &expr, map_fun_t ref_op, const Sequence &seq) {
        std::vector<Layout> layouts = {
            {},
            {x(3)},
            {x(3),y(5)},
            {x(3),y(5),z(7)},
            float_cells({x(3),y(5),z(7)}),
            {x({"a","b","c"})},
            {x({"a","b","c"}),y({"foo","bar"})},
            {x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})},
            float_cells({x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}),
            {x(3),y({"foo", "bar"}),z(7)},
            {x({"a","b","c"}),y(5),z({"i","j","k","l"})},
            float_cells({x({"a","b","c"}),y(5),z({"i","j","k","l"})})
        };
        for (const Layout &layout: layouts) {
            TEST_DO(verify_result(factory, expr, {spec(layout, seq)}, spec(layout, OpSeq(seq, ref_op))));
        }
    }

    void test_map_op(const vespalib::string &expr, map_fun_t op, const Sequence &seq) {
        TEST_DO(test_map_op_inner(expr, op, seq));
        TEST_DO(test_map_op_inner(fmt("map(x,f(a)(%s))", expr.c_str()), op, seq));
    }

    void test_tensor_map() {
        TEST_DO(test_map_op("-a", operation::Neg::f, Sub2(Div16(N()))));
        TEST_DO(test_map_op("!a", operation::Not::f, Seq({0.0, 1.0, 1.0})));
        TEST_DO(test_map_op("cos(a)", operation::Cos::f, Div16(N())));
        TEST_DO(test_map_op("sin(a)", operation::Sin::f, Div16(N())));
        TEST_DO(test_map_op("tan(a)", operation::Tan::f, Div16(N())));
        TEST_DO(test_map_op("cosh(a)", operation::Cosh::f, Div16(N())));
        TEST_DO(test_map_op("sinh(a)", operation::Sinh::f, Div16(N())));
        TEST_DO(test_map_op("tanh(a)", operation::Tanh::f, Div16(N())));
        TEST_DO(test_map_op("acos(a)", operation::Acos::f, SigmoidF(Div16(N()))));
        TEST_DO(test_map_op("asin(a)", operation::Asin::f, SigmoidF(Div16(N()))));
        TEST_DO(test_map_op("atan(a)", operation::Atan::f, Div16(N())));
        TEST_DO(test_map_op("exp(a)", operation::Exp::f, Div16(N())));
        TEST_DO(test_map_op("log10(a)", operation::Log10::f, Div16(N())));
        TEST_DO(test_map_op("log(a)", operation::Log::f, Div16(N())));
        TEST_DO(test_map_op("sqrt(a)", operation::Sqrt::f, Div16(N())));
        TEST_DO(test_map_op("ceil(a)", operation::Ceil::f, Div16(N())));
        TEST_DO(test_map_op("fabs(a)", operation::Fabs::f, Div16(N())));
        TEST_DO(test_map_op("floor(a)", operation::Floor::f, Div16(N())));
        TEST_DO(test_map_op("isNan(a)", operation::IsNan::f, Seq({my_nan, 1.0, 1.0})));
        TEST_DO(test_map_op("relu(a)", operation::Relu::f, Sub2(Div16(N()))));
        TEST_DO(test_map_op("sigmoid(a)", operation::Sigmoid::f, Sub2(Div16(N()))));
        TEST_DO(test_map_op("elu(a)", operation::Elu::f, Sub2(Div16(N()))));
        TEST_DO(test_map_op("erf(a)", operation::Erf::f, Sub2(Div16(N()))));
        TEST_DO(test_map_op("a in [1,5,7,13,42]", MyIn::f, N()));
        TEST_DO(test_map_op("(a+1)*2", MyOp::f, Div16(N())));
    }

    //-------------------------------------------------------------------------

    void test_apply_op(const vespalib::string &expr,
                       const TensorSpec &expect,
                       const TensorSpec &lhs,
                       const TensorSpec &rhs) {
        TEST_DO(verify_result(factory, expr, {lhs, rhs}, expect));
    }

    void test_fixed_sparse_cases_apply_op(const vespalib::string &expr,
                                          join_fun_t op)
    {
        TEST_DO(test_apply_op(expr,
                              spec("x{}", {}),
                              spec("x{}", { { {{"x","1"}}, 3 } }),
                              spec("x{}", { { {{"x","2"}}, 5 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{}", { { {{"x","1"}}, op(3,5) } }),
                              spec("x{}", { { {{"x","1"}}, 3 } }),
                              spec("x{}", { { {{"x","1"}}, 5 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{}", { { {{"x","1"}}, op(3,-5) } }),
                              spec("x{}", { { {{"x","1"}},  3 } }),
                              spec("x{}", { { {{"x","1"}}, -5 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},y{},z{}",
                                   {   { {{"x","-"},{"y","2"},{"z","-"}},
                                               op(5,7) },
                                       { {{"x","1"},{"y","-"},{"z","3"}},
                                               op(3,11) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},  5 },
                                       { {{"x","1"},{"y","-"}},  3 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},y{},z{}",
                                   {   { {{"x","-"},{"y","2"},{"z","-"}},
                                               op(7,5) },
                                       { {{"x","1"},{"y","-"},{"z","3"}},
                                               op(11,3) } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},  5 },
                                       { {{"x","1"},{"y","-"}},  3 } })));
        TEST_DO(test_apply_op(expr,
                              spec("y{},z{}",
                                   {   { {{"y","2"},{"z","-"}},
                                               op(5,7) } }),
                              spec("y{}", { { {{"y","2"}}, 5 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } })));
        TEST_DO(test_apply_op(expr,
                              spec("y{},z{}",
                                   {   { {{"y","2"},{"z","-"}},
                                               op(7,5) } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","3"}}, 11 },
                                       { {{"y","2"},{"z","-"}},  7 } }),
                              spec("y{}", { { {{"y","2"}}, 5 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},
                                               op(5,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}}, 5 },
                                       { {{"x","1"},{"y","-"}}, 3 } }),
                              spec("y{}", { { {{"y","2"}}, 7 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}},
                                               op(7,5) } }),
                              spec("y{}", { { {{"y","2"}}, 7 } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","2"}}, 5 },
                                       { {{"x","1"},{"y","-"}}, 3 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},z{}",
                                   {   { {{"x","1"},{"z","3"}},
                                               op(3,11) } }),
                              spec("x{}", { { {{"x","1"}},  3 } }),
                              spec("z{}", { { {{"z","3"}}, 11 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},z{}",
                                   {   { {{"x","1"},{"z","3"}},
                                               op(11,3) } }),
                              spec("z{}",{ { {{"z","3"}}, 11 } }),
                              spec("x{}",{ { {{"x","1"}},  3 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},y{}",
                                   {   { {{"x","1"},{"y","1"}},
                                               op(3,5) },
                                       { {{"x","2"},{"y","1"}},
                                               op(7,5) } }),
                              spec("x{}",
                                   {   { {{"x","1"}}, 3 },
                                       { {{"x","2"}}, 7 } }),
                              spec("y{}",
                                   {   { {{"y","1"}}, 5 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},y{},z{}",
                                   {   { {{"x","1"},{"y","1"},{"z","1"}},
                                               op(1,7) },
                                       { {{"x","1"},{"y","1"},{"z","2"}},
                                               op(1,13) },
                                       { {{"x","1"},{"y","2"},{"z","1"}},
                                               op(5,11) },
                                       { {{"x","2"},{"y","1"},{"z","1"}},
                                               op(3,7) },
                                       { {{"x","2"},{"y","1"},{"z","2"}},
                                               op(3,13) } }),
                              spec("x{},y{}",
                                   {   { {{"x","1"},{"y","1"}},  1 },
                                       { {{"x","1"},{"y","2"}},  5 },
                                       { {{"x","2"},{"y","1"}},  3 } }),
                              spec("y{},z{}",
                                   {   { {{"y","1"},{"z","1"}},  7 },
                                       { {{"y","1"},{"z","2"}}, 13 },
                                       { {{"y","2"},{"z","1"}}, 11 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},y{},z{}",
                                   {   { {{"x","1"},{"y","1"},{"z","1"}},
                                               op(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","1"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","1"},{"z","1"}},  7 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},y{},z{}",
                                   {   { {{"x","1"},{"y","-"},{"z","1"}},
                                               op(5,11) },
                                       { {{"x","1"},{"y","1"},{"z","1"}},
                                               op(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","1"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","1"}}, 11 },
                                       { {{"y","1"},{"z","1"}},  7 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},y{},z{}",
                                   {   { {{"x","1"},{"y","1"},{"z","1"}},
                                               op(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","1"},{"z","1"}},  7 } })));
        TEST_DO(test_apply_op(expr,
                              spec("x{},y{},z{}",
                                   {   { {{"x","-"},{"y","-"},{"z", "-"}},
                                               op(5,11) },
                                       { {{"x","1"},{"y","1"},{"z","1"}},
                                               op(1,7) } }),
                              spec("x{},y{}",
                                   {   { {{"x","-"},{"y","-"}},  5 },
                                       { {{"x","1"},{"y","1"}},  1 } }),
                              spec("y{},z{}",
                                   {   { {{"y","-"},{"z","-"}}, 11 },
                                       { {{"y","1"},{"z","1"}},  7 } })));
    }

    void test_fixed_dense_cases_apply_op(const vespalib::string &expr,
                                         join_fun_t op)
    {
        TEST_DO(test_apply_op(expr,
                              spec(op(0.1,0.2)), spec(0.1), spec(0.2)));
        TEST_DO(test_apply_op(expr,
                              spec(x(1), Seq({ op(3,5) })),
                              spec(x(1), Seq({ 3 })),
                              spec(x(1), Seq({ 5 }))));
        TEST_DO(test_apply_op(expr,
                              spec(x(1), Seq({ op(3,-5) })),
                              spec(x(1), Seq({ 3 })),
                              spec(x(1), Seq({ -5 }))));
        TEST_DO(test_apply_op(expr,
                              spec(x(2), Seq({ op(3,7), op(5,11) })),
                              spec(x(2), Seq({ 3, 5 })),
                              spec(x(2), Seq({ 7, 11 }))));
        TEST_DO(test_apply_op(expr,
                              spec({x(1),y(1)}, Seq({ op(3,5) })),
                              spec({x(1),y(1)}, Seq({ 3 })),
                              spec({x(1),y(1)}, Seq({ 5 }))));
        TEST_DO(test_apply_op(expr,
                              spec({x(2),y(2),z(2)},
                                   Seq({        op(1,  7), op(1, 11),
                                                op(2, 13), op(2, 17),
                                                op(3,  7), op(3, 11),
                                                op(5, 13), op(5, 17)
                                                })),
                              spec({x(2),y(2)},
                                   Seq({         1,  2,
                                                 3,  5 })),
                              spec({y(2),z(2)},
                                   Seq({         7, 11,
                                                13, 17 }))));
    }

    void test_apply_op_inner(const vespalib::string &expr, join_fun_t op, const Sequence &seq) {
        std::vector<Layout> layouts = {
            {},                                                 {},
            {x(5)},                                             {x(5)},
            {x(5)},                                             {y(5)},
            {x(5)},                                             {x(5),y(5)},
            {y(3)},                                             {x(2),z(3)},
            {x(3),y(5)},                                        {y(5),z(7)},
            float_cells({x(3),y(5)}),                           {y(5),z(7)},
            {x(3),y(5)},                                        float_cells({y(5),z(7)}),
            float_cells({x(3),y(5)}),                           float_cells({y(5),z(7)}),
            {x({"a","b","c"})},                                 {x({"a","b","c"})},
            {x({"a","b","c"})},                                 {x({"a","b"})},
            {x({"a","b","c"})},                                 {y({"foo","bar","baz"})},
            {x({"a","b","c"})},                                 {x({"a","b","c"}),y({"foo","bar","baz"})},
            {x({"a","b"}),y({"foo","bar","baz"})},              {x({"a","b","c"}),y({"foo","bar"})},
            {x({"a","b"}),y({"foo","bar","baz"})},              {y({"foo","bar"}),z({"i","j","k","l"})},
            float_cells({x({"a","b"}),y({"foo","bar","baz"})}), {y({"foo","bar"}),z({"i","j","k","l"})},
            {x({"a","b"}),y({"foo","bar","baz"})},              float_cells({y({"foo","bar"}),z({"i","j","k","l"})}),
            float_cells({x({"a","b"}),y({"foo","bar","baz"})}), float_cells({y({"foo","bar"}),z({"i","j","k","l"})}),
            {x(3),y({"foo", "bar"})},                           {y({"foo", "bar"}),z(7)},
            {x({"a","b","c"}),y(5)},                            {y(5),z({"i","j","k","l"})},
            float_cells({x({"a","b","c"}),y(5)}),               {y(5),z({"i","j","k","l"})},
            {x({"a","b","c"}),y(5)},                            float_cells({y(5),z({"i","j","k","l"})}),
            float_cells({x({"a","b","c"}),y(5)}),               float_cells({y(5),z({"i","j","k","l"})})
        };
        ASSERT_TRUE((layouts.size() % 2) == 0);
        for (size_t i = 0; i < layouts.size(); i += 2) {
            TensorSpec lhs_input = spec(layouts[i], seq);
            TensorSpec rhs_input = spec(layouts[i + 1], seq);
            TEST_STATE(fmt("lhs shape: %s, rhs shape: %s",
                           lhs_input.type().c_str(),
                           rhs_input.type().c_str()).c_str());
            TEST_DO(verify_result(factory, expr, {lhs_input, rhs_input}));
        }
        TEST_DO(test_fixed_sparse_cases_apply_op(expr, op));
        TEST_DO(test_fixed_dense_cases_apply_op(expr, op));
    }

    void test_apply_op(const vespalib::string &expr, join_fun_t op, const Sequence &seq) {
        TEST_DO(test_apply_op_inner(expr, op, seq));
        TEST_DO(test_apply_op_inner(fmt("join(x,y,f(a,b)(%s))", expr.c_str()), op, seq));
    }

    void test_tensor_apply() {
        TEST_DO(test_apply_op("a+b", operation::Add::f, Div16(N())));
        TEST_DO(test_apply_op("a-b", operation::Sub::f, Div16(N())));
        TEST_DO(test_apply_op("a*b", operation::Mul::f, Div16(N())));
        TEST_DO(test_apply_op("a/b", operation::Div::f, Div16(N())));
        TEST_DO(test_apply_op("a%b", operation::Mod::f, Div16(N())));
        TEST_DO(test_apply_op("a^b", operation::Pow::f, Div16(N())));
        TEST_DO(test_apply_op("pow(a,b)", operation::Pow::f, Div16(N())));
        TEST_DO(test_apply_op("a==b", operation::Equal::f, Div16(N())));
        TEST_DO(test_apply_op("a!=b", operation::NotEqual::f, Div16(N())));
        TEST_DO(test_apply_op("a~=b", operation::Approx::f, Div16(N())));
        TEST_DO(test_apply_op("a<b", operation::Less::f, Div16(N())));
        TEST_DO(test_apply_op("a<=b", operation::LessEqual::f, Div16(N())));
        TEST_DO(test_apply_op("a>b", operation::Greater::f, Div16(N())));
        TEST_DO(test_apply_op("a>=b", operation::GreaterEqual::f, Div16(N())));
        TEST_DO(test_apply_op("a&&b", operation::And::f, Seq({0.0, 1.0, 1.0})));
        TEST_DO(test_apply_op("a||b", operation::Or::f, Seq({0.0, 1.0, 1.0})));
        TEST_DO(test_apply_op("atan2(a,b)", operation::Atan2::f, Div16(N())));
        TEST_DO(test_apply_op("ldexp(a,b)", operation::Ldexp::f, Div16(N())));
        TEST_DO(test_apply_op("fmod(a,b)", operation::Mod::f, Div16(N())));
        TEST_DO(test_apply_op("min(a,b)", operation::Min::f, Div16(N())));
        TEST_DO(test_apply_op("max(a,b)", operation::Max::f, Div16(N())));
    }

    //-------------------------------------------------------------------------

    void test_dot_product(double expect,
                          const TensorSpec &lhs,
                          const TensorSpec &rhs)
    {
        vespalib::string expr("reduce(a*b,sum)");
        TEST_DO(verify_result(factory, expr, {lhs, rhs}, spec(expect)));
    }

    void test_dot_product(double expect,
                          const Layout &lhs, const Sequence &lhs_seq,
                          const Layout &rhs, const Sequence &rhs_seq)
    {
        TEST_DO(test_dot_product(expect, spec(lhs, lhs_seq), spec(rhs, rhs_seq)));
        TEST_DO(test_dot_product(expect, spec(float_cells(lhs), lhs_seq), spec(rhs, rhs_seq)));
        TEST_DO(test_dot_product(expect, spec(lhs, lhs_seq), spec(float_cells(rhs), rhs_seq)));
        TEST_DO(test_dot_product(expect, spec(float_cells(lhs), lhs_seq), spec(float_cells(rhs), rhs_seq)));
    }

    void test_dot_product() {
        TEST_DO(test_dot_product(((2 * 7) + (3 * 11) + (5 * 13)),
                                 {x(3)}, Seq({ 2, 3, 5 }),
                                 {x(3)}, Seq({ 7, 11, 13 })));
    }

    //-------------------------------------------------------------------------

    void test_concat(const TensorSpec &a,
                     const TensorSpec &b,
                     const vespalib::string &dimension,
                     const TensorSpec &expect)
    {
        vespalib::string expr = fmt("concat(a,b,%s)", dimension.c_str());
        TEST_DO(verify_result(factory, expr, {a, b}, expect));
    }

    void test_concat() {
        TEST_DO(test_concat(spec(10.0), spec(20.0), "x", spec(x(2), Seq({10.0, 20.0}))));
        TEST_DO(test_concat(spec(x(1), Seq({10.0})), spec(20.0), "x", spec(x(2), Seq({10.0, 20.0}))));
        TEST_DO(test_concat(spec(10.0), spec(x(1), Seq({20.0})), "x", spec(x(2), Seq({10.0, 20.0}))));
        TEST_DO(test_concat(spec(x(3), Seq({1.0, 2.0, 3.0})), spec(x(2), Seq({4.0, 5.0})), "x",
                            spec(x(5), Seq({1.0, 2.0, 3.0, 4.0, 5.0}))));
        TEST_DO(test_concat(spec({x(2),y(2)}, Seq({1.0, 2.0, 3.0, 4.0})), spec(y(2), Seq({5.0, 6.0})), "y",
                            spec({x(2),y(4)}, Seq({1.0, 2.0, 5.0, 6.0, 3.0, 4.0, 5.0, 6.0}))));
        TEST_DO(test_concat(spec({x(2),y(2)}, Seq({1.0, 2.0, 3.0, 4.0})), spec(x(2), Seq({5.0, 6.0})), "x",
                            spec({x(4),y(2)}, Seq({1.0, 2.0, 3.0, 4.0, 5.0, 5.0, 6.0, 6.0}))));
        TEST_DO(test_concat(spec(z(3), Seq({1.0, 2.0, 3.0})), spec(y(2), Seq({4.0, 5.0})), "x",
                            spec({x(2),y(2),z(3)}, Seq({1.0, 2.0, 3.0, 1.0, 2.0, 3.0, 4.0, 4.0, 4.0, 5.0, 5.0, 5.0}))));
        TEST_DO(test_concat(spec(y(2), Seq({1.0, 2.0})), spec(y(2), Seq({4.0, 5.0})), "x",
                            spec({x(2), y(2)}, Seq({1.0, 2.0, 4.0, 5.0}))));

        TEST_DO(test_concat(spec(float_cells({x(1)}), Seq({10.0})), spec(20.0), "x", spec(float_cells({x(2)}), Seq({10.0, 20.0}))));
        TEST_DO(test_concat(spec(10.0), spec(float_cells({x(1)}), Seq({20.0})), "x", spec(float_cells({x(2)}), Seq({10.0, 20.0}))));

        TEST_DO(test_concat(spec(float_cells({x(3)}), Seq({1.0, 2.0, 3.0})), spec(x(2), Seq({4.0, 5.0})), "x",
                            spec(x(5), Seq({1.0, 2.0, 3.0, 4.0, 5.0}))));
        TEST_DO(test_concat(spec(x(3), Seq({1.0, 2.0, 3.0})), spec(float_cells({x(2)}), Seq({4.0, 5.0})), "x",
                            spec(x(5), Seq({1.0, 2.0, 3.0, 4.0, 5.0}))));
        TEST_DO(test_concat(spec(float_cells({x(3)}), Seq({1.0, 2.0, 3.0})), spec(float_cells({x(2)}), Seq({4.0, 5.0})), "x",
                            spec(float_cells({x(5)}), Seq({1.0, 2.0, 3.0, 4.0, 5.0}))));
    }

    //-------------------------------------------------------------------------

    void test_cell_cast(const GenSpec &a) {
        for (CellType cell_type: CellTypeUtils::list_types()) {
            auto expect = a.cpy().cells(cell_type);
            if (expect.bad_scalar()) continue;
            vespalib::string expr = fmt("cell_cast(a,%s)", value_type::cell_type_to_name(cell_type).c_str());
            TEST_DO(verify_result(factory, expr, {a}, expect));
        }
    }

    void test_cell_cast() {
        std::vector<GenSpec> gen_list;
        for (CellType cell_type: CellTypeUtils::list_types()) {
            gen_list.push_back(GenSpec(-3).cells(cell_type));
        }
        TEST_DO(test_cell_cast(GenSpec(42)));
        for (const auto &gen: gen_list) {
            TEST_DO(test_cell_cast(gen.cpy().idx("x", 10)));
            TEST_DO(test_cell_cast(gen.cpy().map("x", 10, 1)));
            TEST_DO(test_cell_cast(gen.cpy().map("x", 4, 1).idx("y", 4)));
        }
    }

    //-------------------------------------------------------------------------

    void test_rename(const vespalib::string &expr,
                     const TensorSpec &input,
                     const TensorSpec &expect)
    {
        TEST_DO(verify_result(factory, expr, {input}, expect));
    }

    void test_rename() {
        TEST_DO(test_rename("rename(a,x,y)", spec(x(5), N()), spec(y(5), N())));
        TEST_DO(test_rename("rename(a,y,x)", spec({y(5),z(5)}, N()), spec({x(5),z(5)}, N())));
        TEST_DO(test_rename("rename(a,y,x)", spec(float_cells({y(5),z(5)}), N()), spec(float_cells({x(5),z(5)}), N())));
        TEST_DO(test_rename("rename(a,z,x)", spec({y(5),z(5)}, N()), spec({y(5),x(5)}, N())));
        TEST_DO(test_rename("rename(a,x,z)", spec({x(5),y(5)}, N()), spec({z(5),y(5)}, N())));
        TEST_DO(test_rename("rename(a,y,z)", spec({x(5),y(5)}, N()), spec({x(5),z(5)}, N())));
        TEST_DO(test_rename("rename(a,(x,y),(y,x))", spec({x(5),y(5)}, N()), spec({y(5),x(5)}, N())));
    }

    //-------------------------------------------------------------------------

    void test_tensor_lambda(const vespalib::string &expr, const TensorSpec &expect) {
        TEST_DO(verify_result(factory, expr, {}, expect));
    }

    void test_tensor_lambda() {
        TEST_DO(test_tensor_lambda("tensor(x[10])(x+1)", spec(x(10), N())));
        TEST_DO(test_tensor_lambda("tensor<float>(x[10])(x+1)", spec(float_cells({x(10)}), N())));
        TEST_DO(test_tensor_lambda("tensor(x[5],y[4])(x*4+(y+1))", spec({x(5),y(4)}, N())));
        TEST_DO(test_tensor_lambda("tensor(x[5],y[4])(x==y)", spec({x(5),y(4)},
                                Seq({           1.0, 0.0, 0.0, 0.0,
                                                0.0, 1.0, 0.0, 0.0,
                                                0.0, 0.0, 1.0, 0.0,
                                                0.0, 0.0, 0.0, 1.0,
                                                0.0, 0.0, 0.0, 0.0}))));
    }

    //-------------------------------------------------------------------------

    void test_tensor_create(const vespalib::string &expr, double a, double b, const TensorSpec &expect) {
        TEST_DO(verify_result(factory, expr, {spec(a), spec(b)}, expect));
    }

    void test_tensor_create() {
        TEST_DO(test_tensor_create("tensor(x[3]):{{x:0}:a,{x:1}:b,{x:2}:3}", 1, 2, spec(x(3), N())));
        TEST_DO(test_tensor_create("tensor<float>(x[3]):{{x:0}:a,{x:1}:b,{x:2}:3}", 1, 2, spec(float_cells({x(3)}), N())));
        TEST_DO(test_tensor_create("tensor(x{}):{{x:a}:a,{x:b}:b,{x:c}:3}", 1, 2, spec(x({"a", "b", "c"}), N())));
        TEST_DO(test_tensor_create("tensor(x{},y[2]):{{x:a,y:0}:a,{x:a,y:1}:b}", 1, 2, spec({x({"a"}),y(2)}, N())));
    }

    //-------------------------------------------------------------------------

    void test_tensor_peek(const vespalib::string &expr, const TensorSpec &param, const TensorSpec &expect) {
        TEST_DO(verify_result(factory, expr, {param, spec(1.0)}, expect));
    }

    void test_tensor_peek() {
        auto param_double = spec({x({"0", "1"}),y(2)}, Seq({1.0, 2.0, 3.0, 4.0}));
        auto param_float = spec(float_cells({x({"0", "1"}),y(2)}), Seq({1.0, 2.0, 3.0, 4.0}));
        TEST_DO(test_tensor_peek("tensor(x[2]):[a{x:1,y:1},a{x:(b-1),y:(b-1)}]", param_double, spec(x(2), Seq({4.0, 1.0}))));
        TEST_DO(test_tensor_peek("tensor(x[2]):[a{x:1,y:1},a{x:(b-1),y:(b-1)}]", param_float, spec(x(2), Seq({4.0, 1.0}))));
        TEST_DO(test_tensor_peek("tensor<float>(x[2]):[a{x:1,y:1},a{x:(b-1),y:(b-1)}]", param_double, spec(float_cells({x(2)}), Seq({4.0, 1.0}))));
        TEST_DO(test_tensor_peek("tensor<float>(x[2]):[a{x:1,y:1},a{x:(b-1),y:(b-1)}]", param_float, spec(float_cells({x(2)}), Seq({4.0, 1.0}))));
        TEST_DO(test_tensor_peek("a{x:(b)}", param_double, spec(y(2), Seq({3.0, 4.0}))));
        TEST_DO(test_tensor_peek("a{x:(b)}", param_float, spec(float_cells({y(2)}), Seq({3.0, 4.0}))));
        TEST_DO(test_tensor_peek("a{y:(b)}", param_double, spec(x({"0", "1"}), Seq({2.0, 4.0}))));
        TEST_DO(test_tensor_peek("a{y:(b)}", param_float, spec(float_cells({x({"0", "1"})}), Seq({2.0, 4.0}))));
    }

    //-------------------------------------------------------------------------

    void test_tensor_merge(const vespalib::string &type_base, const vespalib::string &a_str,
                           const vespalib::string &b_str, const vespalib::string &expect_str)
    {
        vespalib::string expr = "merge(a,b,f(x,y)(2*x+y))";
        for (bool a_float: {false, true}) {
            for (bool b_float: {false, true}) {
                bool both_float = a_float && b_float;
                vespalib::string a_expr = fmt("tensor%s(%s):%s", a_float ? "<float>" : "", type_base.c_str(), a_str.c_str());
                vespalib::string b_expr = fmt("tensor%s(%s):%s", b_float ? "<float>" : "", type_base.c_str(), b_str.c_str());
                vespalib::string expect_expr = fmt("tensor%s(%s):%s", both_float ? "<float>" : "", type_base.c_str(), expect_str.c_str());
                TensorSpec a = spec(a_expr);
                TensorSpec b = spec(b_expr);
                TensorSpec expect = spec(expect_expr);
                TEST_DO(verify_result(factory, expr, {a, b}, expect));
            }
        }
    }

    void test_tensor_merge() {
        TEST_DO(test_tensor_merge("x[3]", "[1,2,3]", "[4,5,6]", "[6,9,12]"));
        TEST_DO(test_tensor_merge("x{}", "{a:1,b:2,c:3}", "{b:4,c:5,d:6}", "{a:1,b:8,c:11,d:6}"));
        TEST_DO(test_tensor_merge("x{},y[2]", "{a:[1,2],b:[3,4]}", "{b:[5,6],c:[6,7]}", "{a:[1,2],b:[11,14],c:[6,7]}"));
    }

    //-------------------------------------------------------------------------

    void verify_encode_decode(const TensorSpec &spec,
                              const ValueBuilderFactory &encode_factory,
                              const ValueBuilderFactory &decode_factory)
    {
        nbostream data;
        auto value = value_from_spec(spec, encode_factory);
        encode_value(*value, data);
        auto value2 = decode_value(data, decode_factory);
        TensorSpec spec2 = spec_from_value(*value2);
        EXPECT_EQUAL(spec2, spec);
    }

    void verify_encode_decode(const TensorSpec &spec) {
        const ValueBuilderFactory &simple = SimpleValueBuilderFactory::get();
        TEST_DO(verify_encode_decode(spec, factory, simple));
        if (&factory != &simple) {
            TEST_DO(verify_encode_decode(spec, simple, factory));
        }
    }

    void test_binary_format_spec(Cursor &test) {
        Stash stash;
        TensorSpec spec = TensorSpec::from_slime(test["tensor"]);
        const Inspector &binary = test["binary"];
        EXPECT_GREATER(binary.entries(), 0u);
        nbostream encoded;
        encode_value(*value_from_spec(spec, factory), encoded);
        test.setData("encoded", Memory(encoded.peek(), encoded.size()));
        bool matched_encode = false;
        for (size_t i = 0; i < binary.entries(); ++i) {
            nbostream data = extract_data(binary[i].asString());
            matched_encode = (matched_encode || is_same(encoded, data));
            EXPECT_EQUAL(spec_from_value(*decode_value(data, factory)), spec);
            EXPECT_EQUAL(data.size(), 0u);
        }
        EXPECT_TRUE(matched_encode);
    }

    void test_binary_format_spec() {
        vespalib::string path = module_path;
        path.append("src/apps/make_tensor_binary_format_test_spec/test_spec.json");
        MappedFileInput file(path);
        EXPECT_TRUE(file.valid());
        auto handle_test = [this](Slime &slime)
                           {
                               size_t fail_cnt = TEST_MASTER.getProgress().failCnt;
                               TEST_DO(test_binary_format_spec(slime.get()));
                               if (TEST_MASTER.getProgress().failCnt > fail_cnt) {
                                   fprintf(stderr, "failed:\n%s", slime.get().toString().c_str());
                               }
                           };
        auto handle_summary = [](Slime &slime)
                              {
                                  EXPECT_GREATER(slime["num_tests"].asLong(), 0);
                              };
        for_each_test(file, handle_test, handle_summary);
    }

    void test_binary_format() {
        TEST_DO(test_binary_format_spec());
        TEST_DO(verify_encode_decode(spec(42)));
        TEST_DO(verify_encode_decode(spec({x(3)}, N())));
        TEST_DO(verify_encode_decode(spec({x(3),y(5)}, N())));
        TEST_DO(verify_encode_decode(spec({x(3),y(5),z(7)}, N())));
        TEST_DO(verify_encode_decode(spec(float_cells({x(3),y(5),z(7)}), N())));
        TEST_DO(verify_encode_decode(spec({x({"a","b","c"})}, N())));
        TEST_DO(verify_encode_decode(spec({x({"a","b","c"}),y({"foo","bar"})}, N())));
        TEST_DO(verify_encode_decode(spec({x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}, N())));
        TEST_DO(verify_encode_decode(spec(float_cells({x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}), N())));
        TEST_DO(verify_encode_decode(spec({x(3),y({"foo", "bar"}),z(7)}, N())));
        TEST_DO(verify_encode_decode(spec({x({"a","b","c"}),y(5),z({"i","j","k","l"})}, N())));
        TEST_DO(verify_encode_decode(spec(float_cells({x({"a","b","c"}),y(5),z({"i","j","k","l"})}), N())));
    }

    //-------------------------------------------------------------------------

    void run_tests() {
        TEST_DO(test_tensor_create_type());
        TEST_DO(test_tensor_reduce());
        TEST_DO(test_tensor_map());
        TEST_DO(test_tensor_apply());
        TEST_DO(test_dot_product());
        TEST_DO(test_concat());
        TEST_DO(test_cell_cast());
        TEST_DO(test_rename());
        TEST_DO(test_tensor_lambda());
        TEST_DO(test_tensor_create());
        TEST_DO(test_tensor_peek());
        TEST_DO(test_tensor_merge());
        TEST_DO(test_binary_format());
    }
};

} // <unnamed>

void
TensorConformance::run_tests(const vespalib::string &module_path, const ValueBuilderFactory &factory)
{
    TestContext ctx(module_path, factory);
    fprintf(stderr, "module path: '%s'\n", ctx.module_path.c_str());
    ctx.run_tests();
}

} // namespace

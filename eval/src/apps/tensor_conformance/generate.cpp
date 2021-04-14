// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generate.h"
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/tensor_model.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/aggr.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib::eval;
using namespace vespalib::eval::test;
using vespalib::make_string_short::fmt;

//-----------------------------------------------------------------------------

namespace {

//-----------------------------------------------------------------------------

const std::vector<vespalib::string> basic_layouts = {
    "",
    "a3", "a3c5", "a3c5e7",
    "b2_1", "b2_1d3_1", "b2_1d3_1f4_1",
    "a3b2_1c5d3_1", "b2_1c5d3_1e7"
};

const std::vector<std::pair<vespalib::string,vespalib::string>> join_layouts = {
    {"", ""},
    {"", "a3"},
    {"", "b2_1"},
    {"", "a3b2_1"},
    {"a3c5e7", "a3c5e7"},
    {"c5", "a3e7"},
    {"a3c5", "c5e7"},
    {"b4_1d6_1f8_1", "b2_2d3_2f4_2"},
    {"d3_1", "b2_1f4_1"},
    {"b2_1d6_1", "d3_2f4_2"},
    {"a3b4_1c5d6_1", "a3b2_1c5d3_1"},
    {"a3b2_1", "c5d3_1"},
    {"a3b4_1c5", "b2_1c5d3_1"}
};

//-----------------------------------------------------------------------------

const std::vector<CellType> just_double = {CellType::DOUBLE};
const std::vector<CellType> just_float = {CellType::FLOAT};
const std::vector<CellType> all_types = CellTypeUtils::list_types();

const double my_nan = std::numeric_limits<double>::quiet_NaN();

Sequence skew(const Sequence &seq) {
    return [seq](size_t i) { return seq(i + 7); };
}

Sequence my_seq(double x0, double delta, size_t n) {
    std::vector<double> values;
    double x = x0;
    for (size_t i = 0; i < n; ++i) {
        values.push_back(x);
        x += delta;
    }
    return Seq(values);
}

//-----------------------------------------------------------------------------

void generate(const vespalib::string &expr, const GenSpec &a, TestBuilder &dst) {
    auto a_cell_types = a.dims().empty() ? just_double : dst.full ? all_types : just_float;
    for (auto a_ct: a_cell_types) {
        dst.add(expr, {{"a", a.cpy().cells(a_ct)}});
    }
}

//-----------------------------------------------------------------------------

void generate(const vespalib::string &expr, const GenSpec &a, const GenSpec &b, TestBuilder &dst) {
    auto a_cell_types = a.dims().empty() ? just_double : dst.full ? all_types : just_float;
    auto b_cell_types = b.dims().empty() ? just_double : dst.full ? all_types : just_float;
    for (auto a_ct: a_cell_types) {
        for (auto b_ct: b_cell_types) {
            dst.add(expr, {{"a", a.cpy().cells(a_ct)},{"b", b.cpy().cells(b_ct)}});
        }
    }
}

//-----------------------------------------------------------------------------

void generate_reduce(Aggr aggr, const Sequence &seq, TestBuilder &dst) {
    for (const auto &layout: basic_layouts) {
        GenSpec a = GenSpec::from_desc(layout).seq(seq);
        for (const auto &dim: a.dims()) {
            vespalib::string expr = fmt("reduce(a,%s,%s)",
                                        AggrNames::name_of(aggr)->c_str(),
                                        dim.name().c_str());
            generate(expr, a, dst);
        }
        if (a.dims().size() > 1) {
            vespalib::string expr = fmt("reduce(a,%s,%s,%s)",
                                        AggrNames::name_of(aggr)->c_str(),
                                        a.dims().back().name().c_str(),
                                        a.dims().front().name().c_str());
            generate(expr, a, dst);
        }
        {
            vespalib::string expr = fmt("reduce(a,%s)",
                                        AggrNames::name_of(aggr)->c_str());
            generate(expr, a, dst);
        }
    }
}

void generate_tensor_reduce(TestBuilder &dst) {
    generate_reduce(Aggr::AVG, N(), dst);
    generate_reduce(Aggr::COUNT, N(), dst);
    generate_reduce(Aggr::PROD, SigmoidF(N()), dst);
    generate_reduce(Aggr::SUM, N(), dst);
    generate_reduce(Aggr::MAX, N(), dst);
    generate_reduce(Aggr::MEDIAN, N(), dst);
    generate_reduce(Aggr::MIN, N(), dst);
}

//-----------------------------------------------------------------------------

void generate_map_expr(const vespalib::string &expr, const Sequence &seq, TestBuilder &dst) {
    for (const auto &layout: basic_layouts) {
        GenSpec a = GenSpec::from_desc(layout).seq(seq);
        generate(expr, a, dst);
    }
}

void generate_op1_map(const vespalib::string &op1_expr, const Sequence &seq, TestBuilder &dst) {
    generate_map_expr(op1_expr, seq, dst);
    generate_map_expr(fmt("map(a,f(a)(%s))", op1_expr.c_str()), seq, dst);
}

void generate_tensor_map(TestBuilder &dst) {
    generate_op1_map("-a", Sub2(Div16(N())), dst);
    generate_op1_map("!a", Seq({0.0, 1.0, 1.0}), dst);
    generate_op1_map("cos(a)", Div16(N()), dst);
    generate_op1_map("sin(a)", Div16(N()), dst);
    generate_op1_map("tan(a)", Div16(N()), dst);
    generate_op1_map("cosh(a)", Div16(N()), dst);
    generate_op1_map("sinh(a)", Div16(N()), dst);
    generate_op1_map("tanh(a)", Div16(N()), dst);
    generate_op1_map("acos(a)", SigmoidF(Div16(N())), dst);
    generate_op1_map("asin(a)", SigmoidF(Div16(N())), dst);
    generate_op1_map("atan(a)", Div16(N()), dst);
    generate_op1_map("exp(a)", Div16(N()), dst);
    generate_op1_map("log10(a)", Div16(N()), dst);
    generate_op1_map("log(a)", Div16(N()), dst);
    generate_op1_map("sqrt(a)", Div16(N()), dst);
    generate_op1_map("ceil(a)", Div16(N()), dst);
    generate_op1_map("fabs(a)", Div16(N()), dst);
    generate_op1_map("floor(a)", Div16(N()), dst);
    generate_op1_map("isNan(a)", Seq({my_nan, 1.0, 1.0}), dst);
    generate_op1_map("relu(a)", Sub2(Div16(N())), dst);
    generate_op1_map("sigmoid(a)", Sub2(Div16(N())), dst);
    generate_op1_map("elu(a)", Sub2(Div16(N())), dst);
    generate_op1_map("erf(a)", Sub2(Div16(N())), dst);
    generate_op1_map("a in [1,5,7,13,42]", N(), dst);
    generate_map_expr("map(a,f(a)((a+1)*2))", Div16(N()), dst);
}

//-----------------------------------------------------------------------------

void generate_join_expr(const vespalib::string &expr, const Sequence &seq, TestBuilder &dst) {
    for (const auto &layouts: join_layouts) {
        GenSpec a = GenSpec::from_desc(layouts.first).seq(seq);
        GenSpec b = GenSpec::from_desc(layouts.second).seq(skew(seq));
        generate(expr, a, b, dst);
        generate(expr, b, a, dst);
    }
}

void generate_op2_join(const vespalib::string &op2_expr, const Sequence &seq, TestBuilder &dst) {
    generate_join_expr(op2_expr, seq, dst);
    generate_join_expr(fmt("join(a,b,f(a,b)(%s))", op2_expr.c_str()), seq, dst);
}

void generate_tensor_join(TestBuilder &dst) {
    generate_op2_join("a+b", Div16(N()), dst);
    generate_op2_join("a-b", Div16(N()), dst);
    generate_op2_join("a*b", Div16(N()), dst);
    generate_op2_join("a/b", Div16(N()), dst);
    generate_op2_join("a%b", Div16(N()), dst);
    generate_op2_join("a^b", my_seq(1.0, 1.0, 5), dst);
    generate_op2_join("pow(a,b)", my_seq(1.0, 1.0, 5), dst);
    generate_op2_join("a==b", Div16(N()), dst);
    generate_op2_join("a!=b", Div16(N()), dst);
    generate_op2_join("a~=b", Div16(N()), dst);
    generate_op2_join("a<b", Div16(N()), dst);
    generate_op2_join("a<=b", Div16(N()), dst);
    generate_op2_join("a>b", Div16(N()), dst);
    generate_op2_join("a>=b", Div16(N()), dst);
    generate_op2_join("a&&b", Seq({0.0, 1.0, 1.0}), dst);
    generate_op2_join("a||b", Seq({0.0, 1.0, 1.0}), dst);
    generate_op2_join("atan2(a,b)", Div16(N()), dst);
    generate_op2_join("ldexp(a,b)", Div16(N()), dst);
    generate_op2_join("fmod(a,b)", Div16(N()), dst);
    generate_op2_join("min(a,b)", Div16(N()), dst);
    generate_op2_join("max(a,b)", Div16(N()), dst);
    generate_join_expr("join(a,b,f(a,b)((a+b)/(a*b)))", Div16(N()), dst);
}

//-----------------------------------------------------------------------------

void generate_dot_product(TestBuilder &dst,
                          const Layout &lhs, const Sequence &lhs_seq,
                          const Layout &rhs, const Sequence &rhs_seq)
{
    dst.add("reduce(a*b,sum)", { {"a", spec(lhs, lhs_seq)},{"b", spec(rhs, rhs_seq)} });
}

void generate_dot_product(TestBuilder &dst,
                          const Layout &layout,
                          const Sequence &lhs_seq,
                          const Sequence &rhs_seq)
{
    auto fl_lay = float_cells(layout);
    generate_dot_product(dst, layout, lhs_seq, layout, rhs_seq);
    generate_dot_product(dst, fl_lay, lhs_seq, layout, rhs_seq);
    generate_dot_product(dst, layout, lhs_seq, fl_lay, rhs_seq);
    generate_dot_product(dst, fl_lay, lhs_seq, fl_lay, rhs_seq);
}

void generate_dot_product(TestBuilder &dst) {
    generate_dot_product(dst, {x(3)},
                         Seq({ 2, 3, 5 }),
                         Seq({ 7, 11, 13 }));
}

//-----------------------------------------------------------------------------

void generate_xw_product(TestBuilder &dst) {
    auto matrix = spec({x(2),y(3)}, Seq({ 3, 5, 7, 11, 13, 17 }));
    auto fmatrix = spec(float_cells({x(2),y(3)}), Seq({ 3, 5, 7, 11, 13, 17 }));
    dst.add("reduce(a*b,sum,x)", {{"a", spec(x(2), Seq({ 1, 2 }))}, {"b", matrix}});
    dst.add("reduce(a*b,sum,x)", {{"a", spec(float_cells({x(2)}), Seq({ 1, 2 }))}, {"b", matrix}});
    dst.add("reduce(a*b,sum,x)", {{"a", spec(x(2), Seq({ 1, 2 }))}, {"b", fmatrix}});
    dst.add("reduce(a*b,sum,x)", {{"a", spec(float_cells({x(2)}), Seq({ 1, 2 }))}, {"b", fmatrix}});
    dst.add("reduce(a*b,sum,y)", {{"a", spec(y(3), Seq({ 1, 2, 3 }))}, {"b", matrix}});
}

//-----------------------------------------------------------------------------

void generate_tensor_concat(TestBuilder &dst) {
    dst.add("concat(a,b,x)", {{"a", spec(10.0)}, {"b", spec(20.0)}});
    dst.add("concat(a,b,x)", {{"a", spec(x(1), Seq({10.0}))}, {"b", spec(20.0)}});
    dst.add("concat(a,b,x)", {{"a", spec(10.0)}, {"b", spec(x(1), Seq({20.0}))}});
    dst.add("concat(a,b,x)", {{"a", spec(x(3), Seq({1.0, 2.0, 3.0}))}, {"b", spec(x(2), Seq({4.0, 5.0}))}});
    dst.add("concat(a,b,y)", {{"a", spec({x(2),y(2)}, Seq({1.0, 2.0, 3.0, 4.0}))}, {"b", spec(y(2), Seq({5.0, 6.0}))}});
    dst.add("concat(a,b,x)", {{"a", spec({x(2),y(2)}, Seq({1.0, 2.0, 3.0, 4.0}))}, {"b", spec(x(2), Seq({5.0, 6.0}))}});
    dst.add("concat(a,b,x)", {{"a", spec(z(3), Seq({1.0, 2.0, 3.0}))}, {"b", spec(y(2), Seq({4.0, 5.0}))}});
    dst.add("concat(a,b,x)", {{"a", spec(y(2), Seq({1.0, 2.0}))}, {"b", spec(y(2), Seq({4.0, 5.0}))}});
    dst.add("concat(concat(a,b,x),concat(c,d,x),y)", {{"a", spec(1.0)}, {"b", spec(2.0)}, {"c", spec(3.0)}, {"d", spec(4.0)}});

    dst.add("concat(a,b,x)",
            {{"a", spec(float_cells({x(1)}), Seq({10.0}))}, {"b", spec(20.0) }});

    dst.add("concat(a,b,x)",
            {{"a", spec(10.0)}, {"b", spec(float_cells({x(1)}), Seq({20.0}))}});

    dst.add("concat(a,b,x)",
            {
                {"a", spec(float_cells({x(3)}), Seq({1.0, 2.0, 3.0})) },
                {"b", spec(x(2), Seq({4.0, 5.0})) }
            });

    dst.add("concat(a,b,x)",
            {
                {"a", spec(x(3), Seq({1.0, 2.0, 3.0}))},
                {"b", spec(float_cells({x(2)}), Seq({4.0, 5.0}))}
            });

    dst.add("concat(a,b,x)",
            {
                {"a", spec(float_cells({x(3)}), Seq({1.0, 2.0, 3.0})) },
                {"b", spec(float_cells({x(2)}), Seq({4.0, 5.0})) }
            });
}

//-----------------------------------------------------------------------------

void generate_tensor_rename(TestBuilder &dst) {
    dst.add("rename(a,x,y)", {{"a", spec(x(5), N())}});
    dst.add("rename(a,y,x)", {{"a", spec({y(5),z(5)}, N())}});
    dst.add("rename(a,z,x)", {{"a", spec({y(5),z(5)}, N())}});
    dst.add("rename(a,x,z)", {{"a", spec({x(5),y(5)}, N())}});
    dst.add("rename(a,y,z)", {{"a", spec({x(5),y(5)}, N())}});
    dst.add("rename(a,y,z)", {{"a", spec(float_cells({x(5),y(5)}), N())}});
    dst.add("rename(a,(x,y),(y,x))", {{"a", spec({x(5),y(5)}, N())}});
}

//-----------------------------------------------------------------------------

void generate_tensor_lambda(TestBuilder &dst) {
    dst.add("tensor(x[10])(x+1)", {{}});
    dst.add("tensor<float>(x[5],y[4])(x*4+(y+1))", {{}});
    dst.add("tensor(x[5],y[4])(x*4+(y+1))", {{}});
    dst.add("tensor(x[5],y[4])(x==y)", {{}});
}

//-----------------------------------------------------------------------------

} // namespace <unnamed>

//-----------------------------------------------------------------------------

void
Generator::generate(TestBuilder &dst)
{
    generate_tensor_reduce(dst);
    generate_tensor_map(dst);
    generate_tensor_join(dst);
    generate_dot_product(dst);
    generate_xw_product(dst);
    generate_tensor_concat(dst);
    generate_tensor_rename(dst);
    generate_tensor_lambda(dst);
}

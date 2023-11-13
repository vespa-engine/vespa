// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generate.h"
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/eval/eval/aggr.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib::eval;
using namespace vespalib::eval::test;
using vespalib::make_string_short::fmt;

//-----------------------------------------------------------------------------

namespace {

struct IgnoreJava : TestBuilder {
    TestBuilder &dst;
    IgnoreJava(TestBuilder &dst_in) : TestBuilder(dst_in.full), dst(dst_in) {}
    void add(const vespalib::string &expression,
             const std::map<vespalib::string,TensorSpec> &inputs,
             const std::set<vespalib::string> &ignore) override
    {
        auto my_ignore = ignore;
        my_ignore.insert("vespajlib");
        dst.add(expression, inputs, my_ignore);
    }
};

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

const std::vector<std::pair<vespalib::string,vespalib::string>> merge_layouts = {
    {"", ""},
    {"a3c5e7", "a3c5e7"},
    {"b15_2", "b10_3"},
    {"b6_2d4_3f6_2", "b4_3d6_2f4_3"},
    {"a3b6_2c1d4_3e2f6_2", "a3b4_3c1d6_2e2f4_3"},
};

const std::vector<vespalib::string> concat_c_layouts_a = {
    "", "c3", "a3", "b6_2", "a3b6_2", "a3b6_2c3"
};

const std::vector<vespalib::string> concat_c_layouts_b = {
    "", "c5", "a3", "b4_3", "a3b4_3", "a3b4_3c5"
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

void generate(const vespalib::string &expr, const GenSpec &a, const GenSpec &b, TestBuilder &dst) {
    auto a_cell_types = a.dims().empty() ? just_double : dst.full ? all_types : just_float;
    auto b_cell_types = b.dims().empty() ? just_double : dst.full ? all_types : just_float;
    for (auto a_ct: a_cell_types) {
        for (auto b_ct: b_cell_types) {
            dst.add(expr, {{"a", a.cpy().cells(a_ct)},{"b", b.cpy().cells(b_ct)}});
        }
    }
}

void generate_with_cell_type(const char *expr_fmt, TestBuilder &dst) {
    auto cell_types = dst.full ? all_types : just_float;
    for (auto ct: cell_types) {
        auto name = value_type::cell_type_to_name(ct);
        dst.add(fmt(expr_fmt, name.c_str()), {});
    }
}

void generate_with_cell_type(const char *expr_fmt, double a, double b, double c, TestBuilder &dst) {
    auto cell_types = dst.full ? all_types : just_float;
    for (auto ct: cell_types) {
        auto name = value_type::cell_type_to_name(ct);
        dst.add(fmt(expr_fmt, name.c_str()), {{"a", GenSpec(a)},{"b", GenSpec(b)},{"c", GenSpec(c)}});
    }
}

//-----------------------------------------------------------------------------

void generate_const(TestBuilder &dst) {
    dst.add("1.25", {});
    dst.add("2.75", {});
    dst.add("\"this is a string that will be hashed\"", {});
    dst.add("\"foo bar baz\"", {});
    // constant tensor lambda
    generate_with_cell_type("tensor<%s>(x[10])(x+1)", dst);
    generate_with_cell_type("tensor<%s>(x[5],y[4])(x*4+(y+1))", dst);
    generate_with_cell_type("tensor<%s>(x[5],y[4])(x==y)", dst);
    // constant verbose tensor create
    generate_with_cell_type("tensor<%s>(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}", dst);
    generate_with_cell_type("tensor<%s>(x{}):{{x:a}:1,{x:b}:2,{x:c}:3}", dst);
    generate_with_cell_type("tensor<%s>(x{},y[2]):{{x:a,y:0}:1,{x:a,y:1}:2}", dst);
    // constant convenient tensor create
    generate_with_cell_type("tensor<%s>(x[3]):[1,2,3]", dst);
    generate_with_cell_type("tensor<%s>(x{}):{a:1,b:2,c:3}", dst);
    generate_with_cell_type("tensor<%s>(x{},y[2]):{a:[1,2]}", dst);
}

//-----------------------------------------------------------------------------

void generate_inject(TestBuilder &dst) {
    for (const auto &layout: basic_layouts) {
        GenSpec a = GenSpec::from_desc(layout).seq(N());
        generate("a", a, dst);
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

void generate_reduce(TestBuilder &dst) {
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

void generate_map(TestBuilder &dst) {
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
    // custom lambda
    generate_map_expr("map(a,f(a)((a+1)*2))", Div16(N()), dst);
}

//-----------------------------------------------------------------------------

void generate_map_subspaces(TestBuilder &dst) {
    auto my_seq = Seq({-128, -43, 85, 127});
    auto scalar = GenSpec(7.0);
    auto sparse = GenSpec().from_desc("x8_1").seq(my_seq);
    auto mixed = GenSpec().from_desc("x4_1y4").seq(my_seq);
    auto dense = GenSpec().from_desc("y4").seq(my_seq);
    vespalib::string map_a("map_subspaces(a,f(a)(a*3+2))");
    vespalib::string unpack_a("map_subspaces(a,f(a)(tensor<int8>(y[8])(bit(a,7-y%8))))");
    vespalib::string unpack_y4("map_subspaces(a,f(a)(tensor<int8>(y[32])(bit(a{y:(y/8)},7-y%8))))");
    vespalib::string pack_y4("map_subspaces(a,f(a)(a{y:0}+a{y:1}-a{y:2}+a{y:3}))");
    generate(map_a, scalar, dst);
    generate(map_a, sparse, dst);
    generate(unpack_a, scalar, dst);
    generate(unpack_a, sparse, dst);
    generate(unpack_y4, mixed, dst);
    generate(unpack_y4, dense, dst);
    generate(pack_y4, mixed, dst);
    generate(pack_y4, dense, dst);
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

void generate_join_expr(const vespalib::string &expr, const Sequence &seq_a, const Sequence &seq_b, TestBuilder &dst) {
    for (const auto &layouts: join_layouts) {
        GenSpec a = GenSpec::from_desc(layouts.first).seq(seq_a);
        GenSpec b = GenSpec::from_desc(layouts.second).seq(seq_b);
        generate(expr, a, b, dst);
    }
}

void generate_op2_join(const vespalib::string &op2_expr, const Sequence &seq, TestBuilder &dst) {
    generate_join_expr(op2_expr, seq, dst);
    generate_join_expr(fmt("join(a,b,f(a,b)(%s))", op2_expr.c_str()), seq, dst);
}

void generate_op2_join(const vespalib::string &op2_expr, const Sequence &seq_a, const Sequence &seq_b, TestBuilder &dst) {
    generate_join_expr(op2_expr, seq_a, seq_b, dst);
    generate_join_expr(fmt("join(a,b,f(a,b)(%s))", op2_expr.c_str()), seq_a, seq_b, dst);
}

void generate_join(TestBuilder &dst) {
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
    generate_op2_join("bit(a,b)", Seq({-128, -43, -1, 0, 85, 127}), Seq({0, 1, 2, 3, 4, 5, 6, 7}), dst);
    // TODO: add ignored Java test when it can be ignored
    // IgnoreJava ignore_java(dst);
    // generate_op2_join("hamming(a,b)", Seq({-128, -43, -1, 0, 85, 127}), ignore_java); // TODO: require java
    // inverted lambda
    generate_join_expr("join(a,b,f(a,b)(b-a))", Div16(N()), dst);
    // custom lambda
    generate_join_expr("join(a,b,f(a,b)((a+b)/(a*b)))", Div16(N()), dst);
}

//-----------------------------------------------------------------------------

void generate_merge_expr(const vespalib::string &expr, const Sequence &seq, TestBuilder &dst) {
    for (const auto &layouts: merge_layouts) {
        GenSpec a = GenSpec::from_desc(layouts.first).seq(seq);
        GenSpec b = GenSpec::from_desc(layouts.second).seq(skew(seq));
        generate(expr, a, b, dst);
        generate(expr, b, a, dst);
    }
}

void generate_merge_expr(const vespalib::string &expr, const Sequence &seq_a, const Sequence &seq_b, TestBuilder &dst) {
    for (const auto &layouts: merge_layouts) {
        GenSpec a = GenSpec::from_desc(layouts.first).seq(seq_a);
        GenSpec b = GenSpec::from_desc(layouts.second).seq(seq_b);
        generate(expr, a, b, dst);
    }
}

void generate_op2_merge(const vespalib::string &op2_expr, const Sequence &seq, TestBuilder &dst) {
    generate_merge_expr(op2_expr, seq, dst);
    generate_merge_expr(fmt("merge(a,b,f(a,b)(%s))", op2_expr.c_str()), seq, dst);
}

void generate_op2_merge(const vespalib::string &op2_expr, const Sequence &seq_a, const Sequence &seq_b, TestBuilder &dst) {
    generate_merge_expr(op2_expr, seq_a, seq_b, dst);
    generate_merge_expr(fmt("merge(a,b,f(a,b)(%s))", op2_expr.c_str()), seq_a, seq_b, dst);
}

void generate_merge(TestBuilder &dst) {
    generate_op2_merge("a+b", Div16(N()), dst);
    generate_op2_merge("a-b", Div16(N()), dst);
    generate_op2_merge("a*b", Div16(N()), dst);
    generate_op2_merge("a/b", Div16(N()), dst);
    generate_op2_merge("a%b", Div16(N()), dst);
    generate_op2_merge("a^b", my_seq(1.0, 1.0, 5), dst);
    generate_op2_merge("pow(a,b)", my_seq(1.0, 1.0, 5), dst);
    generate_op2_merge("a==b", Div16(N()), dst);
    generate_op2_merge("a!=b", Div16(N()), dst);
    generate_op2_merge("a~=b", Div16(N()), dst);
    generate_op2_merge("a<b", Div16(N()), dst);
    generate_op2_merge("a<=b", Div16(N()), dst);
    generate_op2_merge("a>b", Div16(N()), dst);
    generate_op2_merge("a>=b", Div16(N()), dst);
    generate_op2_merge("a&&b", Seq({0.0, 1.0, 1.0}), dst);
    generate_op2_merge("a||b", Seq({0.0, 1.0, 1.0}), dst);
    generate_op2_merge("atan2(a,b)", Div16(N()), dst);
    generate_op2_merge("ldexp(a,b)", Div16(N()), dst);
    generate_op2_merge("fmod(a,b)", Div16(N()), dst);
    generate_op2_merge("min(a,b)", Div16(N()), dst);
    generate_op2_merge("max(a,b)", Div16(N()), dst);
    generate_op2_merge("bit(a,b)", Seq({-128, -43, -1, 0, 85, 127}), Seq({0, 1, 2, 3, 4, 5, 6, 7}), dst);
    // TODO: add ignored Java test when it can be ignored
    // IgnoreJava ignore_java(dst);
    // generate_op2_merge("hamming(a,b)", Seq({-128, -43, -1, 0, 85, 127}), ignore_java); // TODO: require java
    // inverted lambda
    generate_merge_expr("merge(a,b,f(a,b)(b-a))", Div16(N()), dst);
    // custom lambda
    generate_merge_expr("merge(a,b,f(a,b)((a+b)/(a*b)))", Div16(N()), dst);
}

//-----------------------------------------------------------------------------

void generate_concat(TestBuilder &dst) {
    for (const auto &layout_a: concat_c_layouts_a) {
        for (const auto &layout_b: concat_c_layouts_b) {
            GenSpec a = GenSpec::from_desc(layout_a).seq(N());
            GenSpec b = GenSpec::from_desc(layout_b).seq(skew(N()));
            generate("concat(a, b, c)", a, b, dst);
            generate("concat(a, b, c)", b, a, dst);
        }
    }
}

//-----------------------------------------------------------------------------

void generate_create(TestBuilder &dst) {
    generate_with_cell_type("tensor<%s>(x[3]):[a,b,c]", 1, 2, 3, dst);
    generate_with_cell_type("tensor<%s>(x{}):{a:a,b:b,c:c}", 1, 2, 3, dst);
    generate_with_cell_type("tensor<%s>(x{},y[2]):{a:[a,b+c]}", 1, 2, 3, dst);
}

//-----------------------------------------------------------------------------

void generate_lambda(TestBuilder &dst) {
    generate_with_cell_type("tensor<%s>(x[10])(a+b+c+x+1)", 1, 2, 3, dst);
    generate_with_cell_type("tensor<%s>(x[5],y[4])(a+b+c+x*4+(y+1))", 1, 2, 3, dst);
    generate_with_cell_type("tensor<%s>(x[5],y[4])(a+b+c+(x==y))", 1, 2, 3, dst);
}

//-----------------------------------------------------------------------------

void generate_cell_cast(TestBuilder &dst) {
    for (const auto &layout: basic_layouts) {
        GenSpec a = GenSpec::from_desc(layout).seq(N(-100));
        auto from_cell_types = a.dims().empty() ? just_double : dst.full ? all_types : just_float;
        auto to_cell_types = a.dims().empty() ? just_double : all_types;
        for (auto a_ct: from_cell_types) {
            for (auto to_ct: to_cell_types) {
                auto name = value_type::cell_type_to_name(to_ct);
                dst.add(fmt("cell_cast(a,%s)", name.c_str()), {{"a", a.cpy().cells(a_ct)}});
            }
        }
    }
}

//-----------------------------------------------------------------------------

void generate_peek(TestBuilder &dst) {
    GenSpec num(2);
    GenSpec dense  = GenSpec::from_desc("x3y5z7").seq(N());
    GenSpec sparse = GenSpec::from_desc("x3_1y5_1z7_1").seq(N());
    GenSpec mixed  = GenSpec::from_desc("x3_1y5z7").seq(N());
    for (const auto &spec: {dense, sparse, mixed}) {
        generate("a{x:1,y:2,z:4}", spec, dst);
        generate("a{y:2,z:5}", spec, dst);
        generate("a{x:2}", spec, dst);
        generate("a{x:1,y:(b),z:(b+2)}", spec, num, dst);
        generate("a{y:(b),z:5}", spec, num, dst);
        generate("a{x:(b)}", spec, num, dst);
    }
}

//-----------------------------------------------------------------------------

void generate_rename(TestBuilder &dst) {
    GenSpec dense  = GenSpec::from_desc("x3y5z7").seq(N());
    GenSpec sparse = GenSpec::from_desc("x3_1y5_1z7_1").seq(N());
    GenSpec mixed  = GenSpec::from_desc("x3_1y5z7").seq(N());
    for (const auto &spec: {dense, sparse, mixed}) {
        generate("rename(a,x,d)", spec, dst);
        generate("rename(a,y,d)", spec, dst);
        generate("rename(a,z,d)", spec, dst);
        generate("rename(a,(x,z),(z,x))", spec, dst);
    }
}

//-----------------------------------------------------------------------------

void generate_if(TestBuilder &dst) {
    vespalib::string expr = "if(a,b,c)";
    for (const auto &layout: basic_layouts) {
        GenSpec b = GenSpec::from_desc(layout).seq(N());
        GenSpec c = GenSpec::from_desc(layout).seq(skew(N()));
        auto cell_types = b.dims().empty() ? just_double : dst.full ? all_types : just_float;
        for (auto ct: cell_types) {
            dst.add(expr, {{"a", GenSpec(0.0)},{"b", b.cpy().cells(ct)},{"c", c.cpy().cells(ct)}});
            dst.add(expr, {{"a", GenSpec(1.0)},{"b", b.cpy().cells(ct)},{"c", c.cpy().cells(ct)}});
        }
    }
}

//-----------------------------------------------------------------------------

void generate_products(TestBuilder &dst) {
    auto z1 = GenSpec(1).from_desc("z7");
    auto z2 = GenSpec(7).from_desc("z7");
    auto xz = GenSpec(1).from_desc("x3z7");
    auto yz = GenSpec(3).from_desc("y5z7");
    // dot product
    generate("reduce(a*b,sum,z)", z1, z2, dst);
    // xw product
    generate("reduce(a*b,sum,z)", z1, xz, dst);
    generate("reduce(a*b,sum,z)", xz, z2, dst);
    // matmul
    generate("reduce(a*b,sum,z)", xz, yz, dst);
}

//-----------------------------------------------------------------------------

void generate_expanding_reduce(TestBuilder &dst) {
    auto spec = GenSpec::from_desc("x5y0_0");
    for (Aggr aggr: Aggregator::list()) {
        // end up with more cells than you started with
        auto expr1 = fmt("reduce(a,%s,y)", AggrNames::name_of(aggr)->c_str());
        auto expr2 = fmt("reduce(a,%s)", AggrNames::name_of(aggr)->c_str());
        dst.add(expr1, {{"a", spec}});
        dst.add(expr2, {{"a", spec}});
    }
}

//-----------------------------------------------------------------------------

void generate_converting_lambda(TestBuilder &dst) {
    auto dense = GenSpec::from_desc("x3");
    auto sparse = GenSpec::from_desc("y5_2");
    auto mixed = GenSpec::from_desc("x3y5_2");
    // change cell type and dimension types
    dst.add("tensor<bfloat16>(x[5])(a{x:(x)})", {{"a", dense}});
    dst.add("tensor<bfloat16>(y[10])(a{y:(y)})", {{"a", sparse}});
    dst.add("tensor<bfloat16>(x[5],y[10])(a{x:(x),y:(y)})", {{"a", mixed}});
}

//-----------------------------------------------------------------------------

void generate_shadowing_lambda(TestBuilder &dst) {
    auto a = GenSpec::from_desc("a3");
    auto b = GenSpec::from_desc("b3");
    // index 'a' shadows external parameter 'a'
    dst.add("tensor(a[5])(reduce(a,sum)+reduce(b,sum))", {{"a", a}, {"b", b}});
}

//-----------------------------------------------------------------------------

void generate_strict_verbatim_peek(TestBuilder &dst) {
    auto a = GenSpec(3);
    auto b = GenSpec().map("x", {"3", "a"});
    // 'a' without () is verbatim even if 'a' is a known value
    dst.add("b{x:a}", {{"a", a}, {"b", b}});
}

//-----------------------------------------------------------------------------

void generate_nested_tensor_lambda(TestBuilder &dst) {
    auto a = GenSpec(2);
    auto b = GenSpec::from_desc("x3").seq(Seq({3,5,7}));
    // constant nested tensor lambda
    dst.add("tensor(x[2],y[3],z[5])(tensor(x[5],y[3],z[2])(x*6+y*2+z){x:(z),y:(y),z:(x)})", {});
    // dynamic nested tensor lambda
    dst.add("tensor(x[2],y[3],z[5])(tensor(x[5],y[3],z[2])(20*(a+x)+2*(b{x:(a)}+y)+z){x:(z),y:(y),z:(x)})",
            {{"a", a}, {"b", b}});
}

//-----------------------------------------------------------------------------

void generate_erf_value_test(TestBuilder &dst) {
    auto a = GenSpec().idx("x", 16 * 17 * 6).seq(Div17(Div16(N(0))));
    dst.add("erf(a)", {{"a", a}});
    dst.add("erf(-a)", {{"a", a}});
}

//-----------------------------------------------------------------------------

void generate_nan_existence(TestBuilder &dst) {
    auto seq1 = Seq({1.0, 1.0, my_nan, my_nan});
    auto seq2 = Seq({2.0, 2.0, my_nan, my_nan});
    auto sparse1 = GenSpec().from_desc("x8_1").seq(seq1);
    auto sparse2 = GenSpec().from_desc("x8_2").seq(seq2);
    auto mixed1 = GenSpec().from_desc("x4_1y4").seq(seq1);
    auto mixed2 = GenSpec().from_desc("x4_2y4").seq(seq2);
    // try to provoke differences between nan and non-existence
    const vespalib::string inner_expr = "f(x,y)(if(isNan(x),11,x)+if(isNan(y),22,y))";
    vespalib::string merge_expr = fmt("merge(a,b,%s)", inner_expr.c_str());
    vespalib::string join_expr = fmt("join(a,b,%s)", inner_expr.c_str());
    dst.add(merge_expr, {{"a", sparse1}, {"b", sparse2}});
    dst.add(merge_expr, {{"a",  mixed1}, {"b",  mixed2}});
    dst.add(join_expr, {{"a", sparse1}, {"b", sparse2}});
    dst.add(join_expr, {{"a",  mixed1}, {"b",  mixed2}});
    dst.add(join_expr, {{"a", sparse1}, {"b",  mixed2}});
    dst.add(join_expr, {{"a",  mixed1}, {"b", sparse2}});
}

//-----------------------------------------------------------------------------

} // namespace <unnamed>

//-----------------------------------------------------------------------------

void
Generator::generate(TestBuilder &dst)
{
    generate_const(dst);
    generate_inject(dst);
    generate_reduce(dst);
    generate_map(dst);
    generate_map_subspaces(dst);
    generate_join(dst);
    generate_merge(dst);
    generate_concat(dst);
    generate_create(dst);
    generate_lambda(dst);
    generate_cell_cast(dst);
    generate_peek(dst);
    generate_rename(dst);
    generate_if(dst);
    //--------------------
    generate_products(dst);
    generate_expanding_reduce(dst);
    generate_converting_lambda(dst);
    generate_shadowing_lambda(dst);
    generate_strict_verbatim_peek(dst);
    generate_nested_tensor_lambda(dst);
    generate_erf_value_test(dst);
    generate_nan_existence(dst);
}

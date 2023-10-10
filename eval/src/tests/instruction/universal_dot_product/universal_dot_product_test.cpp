// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/lazy_params.h>
#include <vespa/eval/eval/make_tensor_function.h>
#include <vespa/eval/eval/optimize_tensor_function.h>
#include <vespa/eval/eval/compile_tensor_function.h>
#include <vespa/eval/instruction/universal_dot_product.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/reference_evaluation.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/trinary.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();
bool bench = false;
double budget = 1.0;
size_t verify_cnt = 0;

std::vector<std::string> ns_list = {
    {"vespalib::eval::instruction::(anonymous namespace)::"},
    {"vespalib::eval::(anonymous namespace)::"},
    {"vespalib::eval::InterpretedFunction::"},
    {"vespalib::eval::tensor_function::"},
    {"vespalib::eval::operation::"},
    {"vespalib::eval::aggr::"},
    {"vespalib::eval::"}
};
std::string strip_ns(const vespalib::string &str) {
    std::string tmp = str;
    for (const auto &ns: ns_list) {
        for (bool again = true; again;) {
            again = false;
            if (auto pos = tmp.find(ns); pos < tmp.size()) {
                tmp.erase(pos, ns.size());
                again = true;
            }
        }
    }
    return tmp;
}

using select_cell_type_t = std::function<CellType(size_t idx)>;
CellType always_double(size_t) { return CellType::DOUBLE; }
select_cell_type_t select(CellType lct) { return [lct](size_t)noexcept{ return lct; }; }
select_cell_type_t  select(CellType lct, CellType rct) { return [lct,rct](size_t idx)noexcept{ return idx ? rct : lct; }; }

TensorSpec make_spec(const vespalib::string &param_name, size_t idx, select_cell_type_t select_cell_type) {
    return GenSpec::from_desc(param_name).cells(select_cell_type(idx)).seq(N(1 + idx));
}

TensorSpec eval_ref(const Function &fun, select_cell_type_t select_cell_type) {
    std::vector<TensorSpec> params;
    for (size_t i = 0; i < fun.num_params(); ++i) {
        params.push_back(make_spec(fun.param_name(i), i, select_cell_type));
    }
    return ReferenceEvaluation::eval(fun, params);
}

class Optimize
{
private:
    struct ctor_tag{};
public:
    enum class With { NONE, CUSTOM, PROD, SPECIFIC };
    With with;
    vespalib::string name;
    OptimizeTensorFunctionOptions options;
    tensor_function_optimizer optimizer;
    Optimize(With with_in, const vespalib::string name_in,
             const OptimizeTensorFunctionOptions &options_in,
             tensor_function_optimizer optimizer_in, ctor_tag)
      : with(with_in), name(name_in), options(options_in), optimizer(optimizer_in) {}
    static Optimize none() { return {With::NONE, "none", {}, {}, {}}; }
    static Optimize prod() { return {With::PROD, "prod", {}, {}, {}}; }
    static Optimize custom(const vespalib::string &name_in, const OptimizeTensorFunctionOptions &options_in) {
        return {With::CUSTOM, name_in, options_in, {}, {}};
    }
    static Optimize specific(const vespalib::string &name_in, tensor_function_optimizer optimizer_in) {
        return {With::SPECIFIC, name_in, {}, optimizer_in, {}};
    }
    ~Optimize();
};
Optimize::~Optimize() = default;

Optimize baseline() {
    OptimizeTensorFunctionOptions my_options;
    my_options.allow_universal_dot_product = false;
    return Optimize::custom("baseline", my_options);
}

Optimize with_universal() {
    OptimizeTensorFunctionOptions my_options;
    my_options.allow_universal_dot_product = true;
    return Optimize::custom("with_universal", my_options);
}

Optimize universal_only() {
    auto my_optimizer = [](const TensorFunction &expr, Stash &stash)->const TensorFunction &
                        {
                            return UniversalDotProduct::optimize(expr, stash, true);
                        };
    return Optimize::specific("universal_only", my_optimizer);
}

Trinary tri(bool value) {
    return value ? Trinary::True : Trinary::False;
}

bool satisfies(bool actual, Trinary expect) {
    return (expect == Trinary::Undefined) || (actual == (expect == Trinary::True));
}

void verify(const vespalib::string &expr, select_cell_type_t select_cell_type,
            Trinary expect_forward, Trinary expect_distinct, Trinary expect_single)
{
    ++verify_cnt;
    auto fun = Function::parse(expr);
    ASSERT_FALSE(fun->has_error());
    std::vector<Value::UP> values;
    for (size_t i = 0; i < fun->num_params(); ++i) {
        auto value = value_from_spec(make_spec(fun->param_name(i), i, select_cell_type), prod_factory);
        values.push_back(std::move(value));
    }
    SimpleObjectParams params({});
    std::vector<ValueType> param_types;
    for (auto &&up: values) {
        params.params.emplace_back(*up);
        param_types.push_back(up->type());
    }
    NodeTypes node_types(*fun, param_types);
    const ValueType &expected_type = node_types.get_type(fun->root());
    ASSERT_FALSE(expected_type.is_error());
    Stash stash;
    std::vector<const TensorFunction *> list;
    const TensorFunction &plain_fun = make_tensor_function(prod_factory, fun->root(), node_types, stash);
    const TensorFunction &optimized = apply_tensor_function_optimizer(plain_fun, universal_only().optimizer, stash,
                                                                      [&list](const auto &node){
                                                                          list.push_back(std::addressof(node));
                                                                      });
    ASSERT_EQ(list.size(), 1);
    auto node = as<UniversalDotProduct>(*list[0]);
    ASSERT_TRUE(node);
    EXPECT_TRUE(satisfies(node->forward(), expect_forward));
    EXPECT_TRUE(satisfies(node->distinct(), expect_distinct));
    EXPECT_TRUE(satisfies(node->single(), expect_single));
    InterpretedFunction ifun(prod_factory, optimized);
    InterpretedFunction::Context ctx(ifun);
    const Value &actual = ifun.eval(ctx, params);
    EXPECT_EQ(actual.type(), expected_type);
    EXPECT_EQ(actual.cells().type, expected_type.cell_type());
    if (expected_type.count_mapped_dimensions() == 0) {
        EXPECT_EQ(actual.index().size(), TrivialIndex::get().size());
        EXPECT_EQ(actual.cells().size, expected_type.dense_subspace_size());
    } else {
        EXPECT_EQ(actual.cells().size, actual.index().size() * expected_type.dense_subspace_size());
    }
    auto expected = eval_ref(*fun, select_cell_type);
    EXPECT_EQ(spec_from_value(actual), expected);
}
void verify(const vespalib::string &expr) {
    verify(expr, always_double, Trinary::Undefined, Trinary::Undefined, Trinary::Undefined);
}
void verify(const vespalib::string &expr, select_cell_type_t select_cell_type, bool forward, bool distinct, bool single) {
    verify(expr, select_cell_type, tri(forward), tri(distinct), tri(single));
}

using cost_list_t = std::vector<std::pair<vespalib::string,double>>;
std::vector<std::pair<vespalib::string,cost_list_t>> benchmark_results;

void benchmark(const vespalib::string &expr, std::vector<Optimize> list) {
    verify(expr);
    auto fun = Function::parse(expr);
    ASSERT_FALSE(fun->has_error());
    cost_list_t cost_list;
    fprintf(stderr, "BENCH: %s\n", expr.c_str());
    for (Optimize &optimize: list) {
        std::vector<Value::UP> values;
        for (size_t i = 0; i < fun->num_params(); ++i) {
            auto value = value_from_spec(make_spec(fun->param_name(i), i, always_double), prod_factory);
            values.push_back(std::move(value));
        }
        SimpleObjectParams params({});
        std::vector<ValueType> param_types;
        for (auto &&up: values) {
            params.params.emplace_back(*up);
            param_types.push_back(up->type());
        }
        NodeTypes node_types(*fun, param_types);
        ASSERT_FALSE(node_types.get_type(fun->root()).is_error());
        Stash stash;
        const TensorFunction &plain_fun = make_tensor_function(prod_factory, fun->root(), node_types, stash);
        const TensorFunction *optimized = nullptr;
        switch (optimize.with) {
        case Optimize::With::NONE:
            optimized = std::addressof(plain_fun);
            break;
        case Optimize::With::PROD:
            optimized = std::addressof(optimize_tensor_function(prod_factory, plain_fun, stash));
            break;
        case Optimize::With::CUSTOM:
            optimized = std::addressof(optimize_tensor_function(prod_factory, plain_fun, stash, optimize.options));
            break;
        case Optimize::With::SPECIFIC:
            size_t count = 0;
            optimized = std::addressof(apply_tensor_function_optimizer(plain_fun, optimize.optimizer, stash,
                                                                       [&count](const auto &)noexcept{ ++count; }));
            ASSERT_EQ(count, 1);
            break;
        }
        ASSERT_NE(optimized, nullptr);
        CTFMetaData ctf_meta;
        InterpretedFunction ifun(prod_factory, *optimized, &ctf_meta);
        InterpretedFunction::ProfiledContext pctx(ifun);
        ASSERT_EQ(ctf_meta.steps.size(), ifun.program_size());
        std::vector<duration> prev_time(ctf_meta.steps.size(), duration::zero());
        std::vector<duration> min_time(ctf_meta.steps.size(), duration::max());
        BenchmarkTimer timer(budget);
        while (timer.has_budget()) {
            timer.before();
            const Value &result = ifun.eval(pctx.context, params);
            (void) result;
            timer.after();
            const Value &profiled_result = ifun.eval(pctx, params);
            (void) profiled_result;
            for (size_t i = 0; i < ctf_meta.steps.size(); ++i) {
                min_time[i] = std::min(min_time[i], pctx.cost[i].second - prev_time[i]);
                prev_time[i] = pctx.cost[i].second;
            }
        }
        double cost_us = timer.min_time() * 1000.0 * 1000.0;
        cost_list.emplace_back(optimize.name, cost_us);
        fprintf(stderr, "  optimized with: %s: %g us {\n", optimize.name.c_str(), cost_us);
        for (size_t i = 0; i < ctf_meta.steps.size(); ++i) {
            auto name = strip_ns(ctf_meta.steps[i].class_name);
            if (name.find("Inject") > name.size() && name.find("ConstValue") > name.size()) {
                fprintf(stderr, "    %s: %zu ns\n", name.c_str(), (size_t)count_ns(min_time[i]));
                fprintf(stderr, "    +-- %s\n", strip_ns(ctf_meta.steps[i].symbol_name).c_str());
            }
        }
        fprintf(stderr, "  }\n");
    }
    fprintf(stderr, "\n");
    benchmark_results.emplace_back(expr, std::move(cost_list));
}

TEST(UniversalDotProductTest, test_select_cell_types) {
    auto always = always_double;
    EXPECT_EQ(always(0), CellType::DOUBLE);
    EXPECT_EQ(always(1), CellType::DOUBLE);
    EXPECT_EQ(always(0), CellType::DOUBLE);
    EXPECT_EQ(always(1), CellType::DOUBLE);
    for (CellType lct: CellTypeUtils::list_types()) {
        auto sel1 = select(lct);
        EXPECT_EQ(sel1(0), lct);
        EXPECT_EQ(sel1(1), lct);
        EXPECT_EQ(sel1(0), lct);
        EXPECT_EQ(sel1(1), lct);
        for (CellType rct: CellTypeUtils::list_types()) {
            auto sel2 = select(lct, rct);
            EXPECT_EQ(sel2(0), lct);
            EXPECT_EQ(sel2(1), rct);
            EXPECT_EQ(sel2(0), lct);
            EXPECT_EQ(sel2(1), rct);
        }
    }
}

TEST(UniversalDotProductTest, universal_dot_product_works_for_various_cases) {
    //  forward, distinct, single
    verify("reduce(2.0*3.0, sum)", always_double, true, true, true);

    for (CellType lct: CellTypeUtils::list_types()) {
        for (CellType rct: CellTypeUtils::list_types()) {
            auto sel2 = select(lct, rct);
                                                       // forward, distinct, single
            verify("reduce(a4_1x8*a2_1x8,sum,a,x)", sel2,   false,    false,  false);
            verify("reduce(a4_1x8*a2_1x8,sum,a)",   sel2,   false,    false,   true);
            verify("reduce(a4_1x8*a2_1x8,sum,x)",   sel2,   false,     true,  false);
            verify("reduce(a4_1x8*b2_1x8,sum,b,x)", sel2,    true,    false,  false);
            verify("reduce(a4_1x8*b2_1x8,sum,b)",   sel2,    true,    false,   true);
            verify("reduce(a4_1x8*x8,sum,x)",       sel2,    true,     true,  false);
        }
    }
    // !forward, distinct, single
    //
    // This case is not possible since 'distinct' implies '!single' as
    // long as we reduce anything. The only expression allowed to
    // reduce nothing is the scalar case, which satisfies 'forward'
}

TEST(UniversalDotProductTest, universal_dot_product_works_with_complex_dimension_nesting) {
    verify("reduce(a4_1b4_1c4_1x4y3z2w1*a2_1c1_1x4z2,sum,b,c,x)");
}

TEST(UniversalDotProductTest, forwarding_empty_result) {
    verify("reduce(x0_0*y8_1,sum,y)");
    verify("reduce(x8_1*y0_0,sum,y)");
    verify("reduce(x0_0z16*y8_1z16,sum,y)");
    verify("reduce(x8_1z16*y0_0z16,sum,y)");
}

TEST(UniversalDotProductTest, nonforwarding_empty_result) {
    verify("reduce(x0_0y8*x1_1y8,sum,y)");
    verify("reduce(x1_1y8*x0_0y8,sum,y)");
    verify("reduce(x1_7y8z2*x1_1y8z2,sum,y)");
}

TEST(UniversalDotProductTest, forwarding_expanding_reduce) {
    verify("reduce(5.0*y0_0,sum,y)");
    verify("reduce(5.0*y0_0z1,sum,y)");
    verify("reduce(z16*y0_0,sum,y)");
    verify("reduce(x1_1*y0_0,sum,y)");
    verify("reduce(x0_0*y1_1,sum,y)");
    verify("reduce(x1_1z16*y0_0,sum,y)");
    verify("reduce(x0_0z16*y1_1,sum,y)");
}

TEST(UniversalDotProductTest, nonforwarding_expanding_reduce) {
    verify("reduce(x0_0*y1_1,sum,x,y)");
    verify("reduce(x1_1*y0_0,sum,x,y)");
    verify("reduce(x1_1*y0_0z1,sum,x,y)");
    verify("reduce(x0_0y16*x1_1y16,sum,x)");
    verify("reduce(x1_1y16*x0_0y16,sum,x)");
    verify("reduce(x1_7*y1_1,sum,x,y)");
    verify("reduce(x1_1*y1_7,sum,x,y)");
    verify("reduce(x1_7y16*x1_1y16,sum,x)");
    verify("reduce(x1_1y16*x1_7y16,sum,x)");
}

TEST(UniversalDotProductTest, bench_vector_dot_product) {
    if (!bench) {
        fprintf(stderr, "benchmarking disabled, run with 'bench' parameter to enable\n");
        return;
    }
    auto optimize_list = std::vector<Optimize>({baseline(), with_universal(), universal_only()});

    benchmark("reduce(2.0*3.0,sum)",                    optimize_list);
    benchmark("reduce(5.0*x128,sum,x)",                 optimize_list);
    benchmark("reduce(a1*x128,sum,x)",                  optimize_list);
    benchmark("reduce(a8*x128,sum,x)",                  optimize_list);
    benchmark("reduce(a1_1b8*x128,sum,x)",              optimize_list);
    benchmark("reduce(x16*x16,sum,x)",                  optimize_list);
    benchmark("reduce(x768*x768,sum,x)",                optimize_list);
    benchmark("reduce(y64*x8y64,sum,x,y)",              optimize_list);
    benchmark("reduce(y64*x8y64,sum,y)",                optimize_list);
    benchmark("reduce(y64*x8y64,sum,x)",                optimize_list);
    benchmark("reduce(a8y64*a8y64,sum,y)",              optimize_list);
    benchmark("reduce(a8y64*a8y64,sum,a)",              optimize_list);
    benchmark("reduce(a8y64*b8y64,sum,y)",              optimize_list);
    benchmark("reduce(a8b64*b64c8,sum,b)",              optimize_list);
    benchmark("reduce(x64_1*x64_1,sum,x)",              optimize_list);
    benchmark("reduce(a64_1*b64_1,sum,b)",              optimize_list);
    benchmark("reduce(a8_1b8_1*b8_1c8_1,sum,b)",        optimize_list);
    benchmark("reduce(a8_1b8_1*b8_1c8_1,sum,a,c)",      optimize_list);
    benchmark("reduce(a8_1b8_1*b8_1c8_1,sum,a,b,c)",    optimize_list);
    benchmark("reduce(b64_1x128*x128,sum,x)",           optimize_list);
    benchmark("reduce(b64_1x8y128*x8y128,sum,y)",       optimize_list);
    benchmark("reduce(b64_1x128*x128,sum,b,x)",         optimize_list);
    benchmark("reduce(a1_1x128*a2_1b64_1x128,sum,a,x)", optimize_list);

    size_t max_expr_size = 0;
    for (const auto &[expr, cost_list]: benchmark_results) {
        max_expr_size = std::max(max_expr_size, expr.size());
    }
    for (const auto &[expr, cost_list]: benchmark_results) {
        for (size_t i = 0; i < max_expr_size - expr.size(); ++i) {
            fprintf(stderr, " ");
        }
        fprintf(stderr, "%s: ", expr.c_str());
        size_t cnt = 0;
        double baseline_cost = 0.0;
        double with_universal_cost = 0.0;
        double universal_only_cost = 0.0;
        for (const auto &[name, cost]: cost_list) {
            if (++cnt > 1) {
                fprintf(stderr, ", ");
            }
            if (name == "baseline") {
                baseline_cost = cost;
            } else if (name == "with_universal") {
                with_universal_cost = cost;
            } else if (name == "universal_only") {
                universal_only_cost = cost;
            }
            fprintf(stderr, "%s: %8.3f us", name.c_str(), cost);
        }
        if (with_universal_cost > 1.1 * baseline_cost) {
            fprintf(stderr, ", LOSS:   %8.3f", with_universal_cost / baseline_cost);
        }
        if (baseline_cost > 1.1 * with_universal_cost) {
            fprintf(stderr, ", GAIN:   %8.3f", baseline_cost / with_universal_cost);
        }
        if (with_universal_cost > 1.1 * universal_only_cost) {
            fprintf(stderr, ", MISSED: %8.3f", with_universal_cost / universal_only_cost);
        }
        fprintf(stderr, "\n");
    }
    fprintf(stderr, "\n");
}

int main(int argc, char **argv) {
    const std::string bench_option = "bench";
    const std::string fast_option = "fast";
    const std::string slow_option = "slow";
    if ((argc > 1) && (bench_option == argv[1])) {
        bench = true;
        ++argv;
        --argc;
    }
    if ((argc > 1) && (fast_option == argv[1])) {
        budget = 0.1;
        ++argv;
        --argc;
    }
    if ((argc > 1) && (slow_option == argv[1])) {
        budget = 10.0;
        ++argv;
        --argc;
    }
    ::testing::InitGoogleTest(&argc, argv);
    int result = RUN_ALL_TESTS();
    fprintf(stderr, "verify called %zu times\n", verify_cnt);
    return result;
}

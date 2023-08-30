// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();
bool bench = false;
double budget = 1.0;

GenSpec::seq_t N_16ths = [] (size_t i) noexcept { return (i + 33.0) / 16.0; };

GenSpec G() { return GenSpec().seq(N_16ths); }

const std::vector<GenSpec> layouts = {
    G(),                                                         G(),
    G().idx("x", 5),                                             G().idx("x", 5),
    G().idx("x", 5),                                             G().idx("y", 5),
    G().idx("x", 5),                                             G().idx("x", 5).idx("y", 5),
    G().idx("y", 3),                                             G().idx("x", 2).idx("z", 3),
    G().idx("x", 3).idx("y", 5),                                 G().idx("y", 5).idx("z", 7),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b","c"}),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b"}),
    G().map("x", {"a","b","c"}),                                 G().map("y", {"foo","bar","baz"}),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b","c"}).map("y", {"foo","bar","baz"}),
    G().map("x", {"a","b"}).map("y", {"foo","bar","baz"}),       G().map("x", {"a","b","c"}).map("y", {"foo","bar"}),
    G().map("x", {"a","b"}).map("y", {"foo","bar","baz"}),       G().map("y", {"foo","bar"}).map("z", {"i","j","k","l"}),
    G().idx("x", 3).map("y", {"foo", "bar"}),                    G().map("y", {"foo", "bar"}).idx("z", 7),
    G().map("x", {"a","b","c"}).idx("y", 5),                     G().idx("y", 5).map("z", {"i","j","k","l"})
};

const std::vector<std::vector<vespalib::string>> reductions = {
    {}, {"x"}, {"y"}, {"z"}, {"x", "y"}, {"x", "z"}, {"y", "z"}
};

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

TensorSpec make_spec(const vespalib::string &param_name, size_t idx) {
    return GenSpec::from_desc(param_name).cells_double().seq(N(1 + idx));
}

TensorSpec eval_ref(const Function &fun) {
    std::vector<TensorSpec> params;
    for (size_t i = 0; i < fun.num_params(); ++i) {
        params.push_back(make_spec(fun.param_name(i), i));
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

using cost_map_t = std::map<vespalib::string,double>;
std::vector<std::pair<vespalib::string,cost_map_t>> benchmark_results;

void benchmark(const vespalib::string &desc, const vespalib::string &expr, std::vector<Optimize> list) {
    auto fun = Function::parse(expr);
    ASSERT_FALSE(fun->has_error());
    auto expected = eval_ref(*fun);
    cost_map_t cost_map;
    fprintf(stderr, "BENCH: %s (%s)\n", desc.c_str(), expr.c_str());
    for (Optimize &optimize: list) {
        std::vector<Value::UP> values;
        for (size_t i = 0; i < fun->num_params(); ++i) {
            auto value = value_from_spec(make_spec(fun->param_name(i), i), prod_factory);
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
            optimized = std::addressof(apply_tensor_function_optimizer(plain_fun, optimize.optimizer, stash, &count));
            ASSERT_GT(count, 0);
            break;
        }
        ASSERT_NE(optimized, nullptr);
        CTFMetaData ctf_meta;
        InterpretedFunction ifun(prod_factory, *optimized, &ctf_meta);
        ASSERT_EQ(ctf_meta.steps.size(), ifun.program_size());
        BenchmarkTimer timer(budget);
        std::vector<duration> prev_time(ctf_meta.steps.size(), duration::zero());
        std::vector<duration> min_time(ctf_meta.steps.size(), duration::max());
        InterpretedFunction::ProfiledContext pctx(ifun);
        for (bool first = true; timer.has_budget(); first = false) {
            const Value &profiled_result =  ifun.eval(pctx, params);
            if (first) {
                EXPECT_EQ(spec_from_value(profiled_result), expected);
            }
            timer.before();
            const Value &result = ifun.eval(pctx.context, params);
            timer.after();
            if (first) {
                EXPECT_EQ(spec_from_value(result), expected);
            }
            for (size_t i = 0; i < ctf_meta.steps.size(); ++i) {
                min_time[i] = std::min(min_time[i], pctx.cost[i].second - prev_time[i]);
                prev_time[i] = pctx.cost[i].second;
            }
        }
        double cost_us = timer.min_time() * 1000.0 * 1000.0;
        cost_map.emplace(optimize.name, cost_us);
        fprintf(stderr, "  optimized with: %s: %g us {\n", optimize.name.c_str(), cost_us);
        for (size_t i = 0; i < ctf_meta.steps.size(); ++i) {
            auto name = strip_ns(ctf_meta.steps[i].class_name);
            if (name.find("Inject") > name.size() && name.find("ConstValue") > name.size()) {
                fprintf(stderr, "    %s: %zu ns\n", name.c_str(), count_ns(min_time[i]));
                fprintf(stderr, "    +-- %s\n", strip_ns(ctf_meta.steps[i].symbol_name).c_str());
            }
        }
        fprintf(stderr, "  }\n");
    }
    fprintf(stderr, "\n");
    benchmark_results.emplace_back(desc, std::move(cost_map));
}

TensorSpec perform_dot_product(const TensorSpec &a, const TensorSpec &b, const std::vector<vespalib::string> &dims)
{
    Stash stash;
    auto lhs = value_from_spec(a, prod_factory);
    auto rhs = value_from_spec(b, prod_factory);
    auto res_type = ValueType::join(lhs->type(), rhs->type()).reduce(dims);
    EXPECT_FALSE(res_type.is_error());
    UniversalDotProduct dot_product(res_type,
                                    tensor_function::inject(lhs->type(), 0, stash),
                                    tensor_function::inject(rhs->type(), 1, stash));
    auto my_op = dot_product.compile_self(prod_factory, stash);
    InterpretedFunction::EvalSingle single(prod_factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs,*rhs})));
}

TEST(UniversalDotProductTest, generic_dot_product_works_for_various_cases) {
    size_t test_cases = 0;
    ASSERT_TRUE((layouts.size() % 2) == 0);
    for (size_t i = 0; i < layouts.size(); i += 2) {
        const auto &l = layouts[i];
        const auto &r = layouts[i+1];
        for (CellType lct : CellTypeUtils::list_types()) {
            auto lhs = l.cpy().cells(lct);
            if (lhs.bad_scalar()) continue;
            for (CellType rct : CellTypeUtils::list_types()) {
                auto rhs = r.cpy().cells(rct);
                if (rhs.bad_scalar()) continue;
                for (const std::vector<vespalib::string> &dims: reductions) {
                    if (ValueType::join(lhs.type(), rhs.type()).reduce(dims).is_error()) continue;
                    ++test_cases;
                    SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.gen().to_string().c_str(), rhs.gen().to_string().c_str()));
                    auto expect = ReferenceOperations::reduce(ReferenceOperations::join(lhs, rhs, operation::Mul::f), Aggr::SUM, dims);
                    auto actual = perform_dot_product(lhs, rhs, dims);
                    // fprintf(stderr, "\n===\nLHS: %s\nRHS: %s\n===\nRESULT: %s\n===\n", lhs.gen().to_string().c_str(), rhs.gen().to_string().c_str(), actual.to_string().c_str());
                    EXPECT_EQ(actual, expect);
                }
            }
        }
    }
    EXPECT_GT(test_cases, 500);
    fprintf(stderr, "total test cases run: %zu\n", test_cases);
}

TEST(UniversalDotProductTest, bench_vector_dot_product) {
    if (!bench) {
        fprintf(stderr, "benchmarking disabled, run with 'bench' parameter to enable\n");
        return;
    }
    auto optimize_list = std::vector<Optimize>({baseline(), with_universal(), universal_only()});

    benchmark("number number",                  "reduce(1.0*2.0,sum)",                    optimize_list);    
    benchmark("number vector",                  "reduce(5.0*x128,sum,x)",                 optimize_list);
    benchmark("vector vector small",            "reduce(x16*x16,sum,x)",                  optimize_list);
    benchmark("vector vector large",            "reduce(x768*x768,sum,x)",                optimize_list);
    benchmark("vector matrix full",             "reduce(y64*x8y64,sum,x,y)",              optimize_list);
    benchmark("vector matrix inner",            "reduce(y64*x8y64,sum,y)",                optimize_list);
    benchmark("vector matrix outer",            "reduce(y64*x8y64,sum,x)",                optimize_list);
    benchmark("matrix matrix same",             "reduce(a8y64*a8y64,sum,y)",              optimize_list);
    benchmark("matrix matrix different",        "reduce(a8y64*b8y64,sum,y)",              optimize_list);
    benchmark("matmul",                         "reduce(a8b64*b64c8,sum,b)",              optimize_list);
    benchmark("sparse overlap",                 "reduce(x64_1*x64_1,sum,x)",              optimize_list);
    benchmark("sparse no overlap",              "reduce(a64_1*b64_1,sum,b)",              optimize_list);
    benchmark("mixed dense",                    "reduce(a1_16x768*x768,sum,x)",           optimize_list);
    benchmark("mixed mixed complex",            "reduce(a1_1x128*a2_1b64_1x128,sum,a,x)", optimize_list);

    size_t max_desc_size = 0;
    for (const auto &[desc, cost_map]: benchmark_results) {
        max_desc_size = std::max(max_desc_size, desc.size());
    }
    for (const auto &[desc, cost_map]: benchmark_results) {
        for (size_t i = 0; i < max_desc_size - desc.size(); ++i) {
            fprintf(stderr, " ");
        }
        fprintf(stderr, "%s: ", desc.c_str());
        size_t cnt = 0;
        double baseline_cost = 0.0;
        double with_universal_cost = 0.0;
        double universal_only_cost = 0.0;
        for (const auto &[name, cost]: cost_map) {
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
        budget = 5.0;
        ++argv;
        --argc;
    }
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}

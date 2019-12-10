// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/gbdt.h>
#include <vespa/eval/eval/vm_forest.h>
#include <vespa/eval/eval/llvm/deinline_forest.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/eval/eval/function.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "model.cpp"

using namespace vespalib::eval;
using namespace vespalib::eval::nodes;
using namespace vespalib::eval::gbdt;

double budget = 2.0;

//-----------------------------------------------------------------------------

struct CompileStrategy {
    virtual const char *name() const = 0;
    virtual const char *code_name() const = 0;
    virtual CompiledFunction compile(const Function &function) const = 0;
    virtual CompiledFunction compile_lazy(const Function &function) const = 0;
    bool is_same(const CompileStrategy &rhs) const {
        return (this == &rhs);
    }
    virtual ~CompileStrategy() {}
};

struct NullStrategy : CompileStrategy {
    const char *name() const override {
        return "none";
    }
    const char *code_name() const override {
        return "Optimize::none";
    }
    CompiledFunction compile(const Function &function) const override {
        return CompiledFunction(function, PassParams::ARRAY, Optimize::none);
    }
    CompiledFunction compile_lazy(const Function &function) const override {
        return CompiledFunction(function, PassParams::LAZY, Optimize::none);
    }
};
NullStrategy none;

struct VMForestStrategy : CompileStrategy {
    const char *name() const override {
        return "vm-forest";
    }
    const char *code_name() const override {
        return "VMForest::optimize_chain";
    }
    CompiledFunction compile(const Function &function) const override {
        return CompiledFunction(function, PassParams::ARRAY, VMForest::optimize_chain);
    }
    CompiledFunction compile_lazy(const Function &function) const override {
        return CompiledFunction(function, PassParams::LAZY, VMForest::optimize_chain);
    }
};
VMForestStrategy vm_forest;

struct DeinlineForestStrategy : CompileStrategy {
    const char *name() const override {
        return "deinline-forest";
    }
    const char *code_name() const override {
        return "DeinlineForest::optimize_chain";
    }
    CompiledFunction compile(const Function &function) const override {
        return CompiledFunction(function, PassParams::ARRAY, DeinlineForest::optimize_chain);
    }
    CompiledFunction compile_lazy(const Function &function) const override {
        return CompiledFunction(function, PassParams::LAZY, DeinlineForest::optimize_chain);
    }
};
DeinlineForestStrategy deinline_forest;

//-----------------------------------------------------------------------------

struct Option {
    size_t id;
    const CompileStrategy &strategy;
    bool is_same(const Option &rhs) const { return strategy.is_same(rhs.strategy); }
    const char *name() const { return strategy.name(); }
    CompiledFunction compile(const Function &function) const { return strategy.compile(function); }
    CompiledFunction compile_lazy(const Function &function) const { return strategy.compile_lazy(function); }
    const char *code_name() const { return strategy.code_name(); }
};

std::vector<Option> all_options({{0, none},{1, vm_forest}});

//-----------------------------------------------------------------------------

struct Result {
    double us;
    size_t opt_idx;
    bool operator<(const Result &rhs) const {
        return (us < rhs.us);
    }
};

struct Segment {
    double min;
    Option option;
    vespalib::string build() const {
        return vespalib::make_string("{%g, %zu}", min, option.id);
    }
};

struct Plan {
    std::vector<Segment> segments;
    void add(const Segment &seg) {
        if (segments.empty()) {
            segments.push_back(seg);
        } else {
            if (!segments.back().option.is_same(seg.option)) {
                segments.push_back(seg);
            }
        }
    }
    vespalib::string build() const {
        vespalib::string plan;
        plan.append("{");
        for (size_t i = 0; i < segments.size(); ++i) {
            if (i > 0) {
                plan.append(", ");
            }
            plan += segments[i].build();
        }
        plan.append("}");
        return plan;
    }
};

//-----------------------------------------------------------------------------

bool crop(const std::vector<Option> &options, const Option &opt, size_t &end) {
    for (size_t i = 0; i < end; ++i) {
        if (options[i].is_same(opt)) {
            end = i;
            return true;
        }
    }
    return false;
}

std::vector<Option> keep_contested(const std::vector<Option> &a, 
                                   const std::vector<Option> &b)
{
    size_t end = b.size();
    std::vector<Option> ret;
    for (size_t i = 0; (i < a.size()) && (end > 0); ++i) {
        if (crop(b, a[i], end)) {
            ret.push_back(a[i]);
        }
    }
    return ret;
}

std::vector<Option> find_order(const ForestParams &params,
                               const std::vector<Option> &options, 
                               size_t num_trees)
{
    std::vector<Result> results;
    auto forest = make_forest(params, num_trees);
    for (size_t i = 0; i < options.size(); ++i) {
        CompiledFunction compiled_function = options[i].compile(*forest);
        CompiledFunction compiled_function_lazy = options[i].compile_lazy(*forest);
        std::vector<double> inputs(compiled_function.num_params(), 0.5);
        results.push_back({compiled_function.estimate_cost_us(inputs, budget), i});
        double lazy_time = compiled_function_lazy.estimate_cost_us(inputs, budget);
        double lazy_factor = lazy_time / results.back().us;
        fprintf(stderr, "  %20s@%6zu: %16g us (inputs: %zu) [lazy: %g us, factor: %g]\n",
                options[i].name(), num_trees, results.back().us,
                inputs.size(), lazy_time, lazy_factor);
    }
    std::sort(results.begin(), results.end());
    std::vector<Option> ret;
    for (auto result: results) {
        ret.push_back(options[result.opt_idx]);
    }
    return ret;
}

double expected_path(const ForestParams &params, size_t num_trees) {
    return ForestStats(extract_trees(make_forest(params, num_trees)->root())).total_expected_path_length;
}

void explore_segment(const ForestParams &params,
                     const std::vector<Option> &min_order,
                     const std::vector<Option> &max_order,
                     size_t min_trees, size_t max_trees,
                     Plan &plan_out)
{
    assert(min_trees != max_trees);
    std::vector<Option> options = keep_contested(min_order, max_order);
    assert(!options.empty());
    if (options.size() == 1) {
        plan_out.add(Segment{expected_path(params, min_trees), options[0]});
    } else {
        if ((max_trees - min_trees) == 1) {
            plan_out.add(Segment{expected_path(params, min_trees), min_order[0]});
            plan_out.add(Segment{expected_path(params, max_trees), max_order[0]});
        } else {
            size_t num_trees = (min_trees + max_trees) / 2;
            std::vector<Option> order = find_order(params, options, num_trees);
            explore_segment(params, min_order, order, min_trees, num_trees, plan_out);
            explore_segment(params, order, max_order, num_trees, max_trees, plan_out);
        }
    }
}

Plan find_plan(const ForestParams &params, std::initializer_list<size_t> limits) {
    Plan plan;
    auto num_trees = limits.begin();
    size_t min_trees = *num_trees++;
    std::vector<Option> min_order = find_order(params, all_options, min_trees);
    while (num_trees != limits.end()) {
        size_t max_trees = *num_trees++;
        std::vector<Option> max_order = find_order(params, all_options, max_trees);
        explore_segment(params, min_order, max_order, min_trees, max_trees, plan);        
        std::swap(min_trees, max_trees);
        std::swap(min_order, max_order);
    }
    return plan;
}

//-----------------------------------------------------------------------------

void dump_options(const std::vector<Option> &options) {
    fprintf(stdout, "std::vector<Optimize::Chain> options({");
    for (size_t i = 0; i < options.size(); ++i) {
        if (i > 0) {
            fprintf(stdout, ", ");
        }
        fprintf(stdout, "%s", options[i].code_name());
    }
    fprintf(stdout, "});\n");
    fflush(stdout);
}

void dump_param_values(const char *name, const std::vector<size_t> &values) {
    fprintf(stdout, "std::vector<size_t> %s({", name);
    for (size_t i = 0; i < values.size(); ++i) {
        if (i > 0) {
            fprintf(stdout, ", ");
        }
        fprintf(stdout, "%zu", values[i]);
    }
    fprintf(stdout, "});\n");
    fflush(stdout);
}

void dump_plan(const ForestParams &params, const Plan &plan) {
    fprintf(stdout, "{{%zu, %zu}, %s}",
            params.less_percent, params.tree_size,
            plan.build().c_str());
}

//-----------------------------------------------------------------------------

TEST("find optimization plans") {
    std::vector<size_t> less_percent_values({90, 100});
    std::vector<size_t> tree_size_values(
            {2, 3, 4, 5, 6, 7, 8,
                    9,  10, 11, 12,  13,  14,  15,  16,
                    18, 20, 22, 24,  26,  28,  30,  32,
                    36, 40, 44, 48,  52,  56,  60,  64,
                    72, 80, 88, 96, 104, 112, 120, 128});

    dump_options(all_options);
    dump_param_values("less_percent_values", less_percent_values);
    dump_param_values("tree_size_values", tree_size_values);

    size_t num_plans = 0;
    fprintf(stdout, "std::map<Params,Plan> plan_repo({");
    for (size_t less_percent: less_percent_values) {
        for (size_t tree_size: tree_size_values) {
            ForestParams params(1234u, less_percent, tree_size);
            fprintf(stdout, "%s\n", (num_plans++ == 0) ? "" : ",");
            fflush(stdout);
            fprintf(stdout, "  ");
            Plan plan = find_plan(params, {8, 512});
            dump_plan(params, plan);
        }
    }
    fprintf(stdout, "});\n");
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }

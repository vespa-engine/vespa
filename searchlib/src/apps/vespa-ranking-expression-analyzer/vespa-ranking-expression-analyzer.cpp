// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/rankingexpression/feature_name_extractor.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/call_nodes.h>
#include <vespa/eval/eval/operator_nodes.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/eval/eval/vm_forest.h>
#include <vespa/eval/eval/llvm/deinline_forest.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/eval/eval/param_usage.h>
#include <vespa/fastos/app.h>

//-----------------------------------------------------------------------------

using vespalib::BenchmarkTimer;
using vespalib::tensor::DefaultTensorEngine;
using namespace vespalib::eval;
using namespace vespalib::eval::nodes;
using namespace vespalib::eval::gbdt;
using namespace search::features::rankingexpression;

//-----------------------------------------------------------------------------

vespalib::string strip_name(const vespalib::string &name) {
    const char *expected_ending = ".expression";
    vespalib::string tmp = name;
    size_t pos = tmp.rfind("/");
    if (pos != tmp.npos) {
        tmp = tmp.substr(pos + 1);
    }
    pos = tmp.rfind(expected_ending);
    if (pos == tmp.size() - strlen(expected_ending)) {
        tmp = tmp.substr(0, pos);
    }
    return tmp;
}

size_t as_percent(double value) {
    return size_t(std::round(value * 100.0));
}

const char *maybe_s(size_t n) { return (n == 1) ? "" : "s"; }

//-----------------------------------------------------------------------------

size_t count_nodes(const Node &node) {
    size_t count = 1;
    for (size_t i = 0; i < node.num_children(); ++i) {
        count += count_nodes(node.get_child(i));
    }
    return count;
}

//-----------------------------------------------------------------------------

struct InputInfo {
    vespalib::string name;
    std::vector<double> cmp_with;
    double usage_probability;
    double expected_usage;
    InputInfo(vespalib::stringref name_in, double usage_probability_in, double expected_usage_in)
        : name(name_in), cmp_with(), usage_probability(usage_probability_in), expected_usage(expected_usage_in) {}
    double select_value() const {
        return cmp_with.empty() ? 0.5 : cmp_with[(cmp_with.size()-1)/2];
    }
};

//-----------------------------------------------------------------------------

struct FunctionInfo {
    typedef std::vector<const Node *> TreeList;

    size_t expression_size;
    bool root_is_forest;
    std::vector<TreeList> forests;
    std::vector<InputInfo> inputs;
    std::vector<double> params;

    void find_forests(const Node &node) {
        if (node.is_forest()) {
            forests.push_back(extract_trees(node));
        } else {
            for (size_t i = 0; i < node.num_children(); ++i) {
                find_forests(node.get_child(i));
            }
        }
    }

    template <typename T>
    void check_cmp(const T *node) {
        if (node) {
            auto lhs_symbol = as<Symbol>(node->lhs());
            auto rhs_symbol = as<Symbol>(node->rhs());
            if (lhs_symbol && node->rhs().is_const()) {
                inputs[lhs_symbol->id()].cmp_with.push_back(node->rhs().get_const_value());
            }
            if (node->lhs().is_const() && rhs_symbol) {
                inputs[rhs_symbol->id()].cmp_with.push_back(node->lhs().get_const_value());
            }
        }
    }

    void check_in(const In *node) {
        if (node) {
            if (auto symbol = as<Symbol>(node->child())) {
                for (size_t i = 0; i < node->num_entries(); ++i) {
                    inputs[symbol->id()].cmp_with.push_back(node->get_entry(i).get_const_value());
                }
            }
        }
    }

    void analyze_inputs(const Node &node) {
        for (size_t i = 0; i < node.num_children(); ++i) {
            analyze_inputs(node.get_child(i));
        }
        check_cmp(as<Equal>(node));
        check_cmp(as<NotEqual>(node));
        check_cmp(as<Approx>(node));
        check_cmp(as<Less>(node));
        check_cmp(as<LessEqual>(node));
        check_cmp(as<Greater>(node));
        check_cmp(as<GreaterEqual>(node));
        check_in(as<In>(node));
    }

    FunctionInfo(const Function &function)
        : expression_size(count_nodes(function.root())),
          root_is_forest(function.root().is_forest()),
          forests(),
          inputs(),
          params()
    {
        auto checked_usage = check_param_usage(function);
        auto counted_usage = count_param_usage(function);
        for (size_t i = 0; i < function.num_params(); ++i) {
            inputs.emplace_back(function.param_name(i), checked_usage[i], counted_usage[i]);
        }
        find_forests(function.root());
        analyze_inputs(function.root());
        for (size_t i = 0; i < function.num_params(); ++i) {
            std::sort(inputs[i].cmp_with.begin(), inputs[i].cmp_with.end());
        }
        for (size_t i = 0; i < function.num_params(); ++i) {
            params.push_back(inputs[i].select_value());
        }
    }

    size_t get_path_len(const TreeList &trees) const {
        size_t path = 0;
        for (const Node *tree: trees) {
            InterpretedFunction ifun(DefaultTensorEngine::ref(), *tree, params.size(), NodeTypes());
            InterpretedFunction::Context ctx(ifun);
            SimpleParams fun_params(params);
            ifun.eval(ctx, fun_params);
            path += ctx.if_cnt();
        }
        return path;
    }

    void report(bool verbose) const {
        fprintf(stderr, "  number of inputs: %zu\n", inputs.size());
        if (verbose) {
            for (size_t i = 0; i < inputs.size(); ++i) {
                fprintf(stderr, "  input %zu:\n", i);
                fprintf(stderr, "    name: %s\n", inputs[i].name.c_str());
                fprintf(stderr, "    usage probability: %g\n", inputs[i].usage_probability);
                fprintf(stderr, "    expected usage: %g\n", inputs[i].expected_usage);
                fprintf(stderr, "    constants compared with: %zu\n", inputs[i].cmp_with.size());
                fprintf(stderr, "    sample value: %g\n", inputs[i].select_value());
            }
        }
        fprintf(stderr, "  expression size (AST node count): %zu\n", expression_size);
        if (root_is_forest) {
            fprintf(stderr, "  expression root is a sum of GBD trees\n");
        }
        if (!forests.empty()) {
            fprintf(stderr, "  expression contains %zu GBD forest%s\n",
                    forests.size(), maybe_s(forests.size()));            
        }
        for (size_t i = 0; i < forests.size(); ++i) {
            ForestStats forest(forests[i]);
            fprintf(stderr, "  GBD forest %zu:\n", i);
            fprintf(stderr, "    average path length: %g\n", forest.total_average_path_length);
            fprintf(stderr, "    expected path length: %g\n", forest.total_expected_path_length);
            fprintf(stderr, "    actual path with sample input: %zu\n", get_path_len(forests[i]));
            if (forest.total_tuned_checks == 0) {
                fprintf(stderr, "    WARNING: checks are not tuned (expected path length to be ignored)\n");
            }
            fprintf(stderr, "    largest set membership check: %zu\n", forest.max_set_size);
            for (const auto &item: forest.tree_sizes) {
                fprintf(stderr, "    forest contains %zu GBD tree%s of size %zu\n",
                        item.count, maybe_s(item.count), item.size);
            }
            if (forest.tree_sizes.size() > 1) {
                fprintf(stderr, "    forest contains %zu GBD trees in total\n", forest.num_trees);
            }
        }
    }
};

//-----------------------------------------------------------------------------

bool none_used(const std::vector<Forest::UP> &forests) {
    return forests.empty();
}

bool deinline_used(const std::vector<Forest::UP> &forests) {
    if (forests.empty()) {
        return false;
    }
    for (const Forest::UP &forest: forests) {
        if (dynamic_cast<DeinlineForest*>(forest.get()) == nullptr) {
            return false;
        }
    }
    return true;
}

bool vmforest_used(const std::vector<Forest::UP> &forests) {
    if (forests.empty()) {
        return false;
    }
    for (const Forest::UP &forest: forests) {
        if (dynamic_cast<VMForest*>(forest.get()) == nullptr) {
            return false;
        }
    }
    return true;
}

//-----------------------------------------------------------------------------

struct State {
    vespalib::string     name;
    vespalib::string     expression;
    Function             function;
    FunctionInfo         fun_info;
    CompiledFunction::UP compiled_function;

    double llvm_compile_s  = 0.0;
    double llvm_execute_us = 0.0;

    std::vector<vespalib::string> options;
    std::vector<double> options_us;

    State(const vespalib::string &file_name, vespalib::string expression_in);
    ~State();

    void benchmark_llvm_compile() {
        BenchmarkTimer timer(1.0);
        while (timer.has_budget()) {
            timer.before();
            CompiledFunction::UP new_cf(new CompiledFunction(function, PassParams::ARRAY));
            timer.after();
            compiled_function = std::move(new_cf);
        }
        llvm_compile_s = timer.min_time();
    }

    void benchmark_option(const vespalib::string &opt_name, Optimize::Chain optimizer_chain) {
        options.push_back(opt_name);
        options_us.push_back(CompiledFunction(function, PassParams::ARRAY, optimizer_chain).estimate_cost_us(fun_info.params));
        fprintf(stderr, "  LLVM(%s) execute time: %g us\n", opt_name.c_str(), options_us.back());
    }

    void report(bool verbose) {
        fun_info.report(verbose);
        benchmark_llvm_compile();
        fprintf(stderr, "  LLVM compile time: %g s\n", llvm_compile_s);
        llvm_execute_us = compiled_function->estimate_cost_us(fun_info.params);
        fprintf(stderr, "  LLVM(default) execute time: %g us\n", llvm_execute_us);
        if (!none_used(compiled_function->get_forests())) {
            benchmark_option("none", Optimize::none);
        }
        if (!deinline_used(compiled_function->get_forests()) && !fun_info.forests.empty()) {
            benchmark_option("deinline", DeinlineForest::optimize_chain);
        }
        if (!vmforest_used(compiled_function->get_forests()) && !fun_info.forests.empty()) {
            benchmark_option("vmforest", VMForest::optimize_chain);
        }
        fprintf(stdout, "[compile: %.3fs][execute: %.3fus]", llvm_compile_s, llvm_execute_us);
        for (size_t i = 0; i < options.size(); ++i) {
            double rel_speed = (llvm_execute_us / options_us[i]);
            fprintf(stdout, "[%s: %zu%%]", options[i].c_str(), as_percent(rel_speed));
            if (rel_speed >= 1.1) {
                fprintf(stderr, "  WARNING: LLVM(%s) faster than default choice\n",
                        options[i].c_str());
            }
        }
        fprintf(stdout, "[name: %s]\n", name.c_str());
        fflush(stdout);
    }
};

State::State(const vespalib::string &file_name, vespalib::string expression_in)
    : name(strip_name(file_name)),
      expression(std::move(expression_in)),
      function(Function::parse(expression, FeatureNameExtractor())),
      fun_info(function),
      compiled_function(),
      llvm_compile_s(0.0),
      llvm_execute_us(0.0),
      options(),
      options_us()
{
}

State::~State() {}

//-----------------------------------------------------------------------------

struct MyApp : public FastOS_Application {
    int Main() override;
    int usage();
    virtual bool useProcessStarter() const override { return false; }
};

int
MyApp::usage() {
    fprintf(stderr, "usage: %s [-v] <expression-file>\n", _argv[0]);
    fprintf(stderr, "  analyze/benchmark vespa ranking expression\n");
    fprintf(stderr, "  -v: more verbose output\n");
    return 1;
}

int
MyApp::Main()
{
    bool verbose = (_argc == 3) && (strcmp(_argv[1], "-v") == 0);
    if (!(verbose || (_argc == 2))) {
        return usage();
    }
    vespalib::string file_name(verbose ? _argv[2] : _argv[1]);
    vespalib::MappedFileInput file(file_name);
    if (!file.valid()) {
        fprintf(stderr, "could not read input file: '%s'\n",
                file_name.c_str());
        return 1;
    }
    State state(file_name, file.get().make_string());
    if (state.function.has_error()) {
        vespalib::string error_message = state.function.get_error();
        fprintf(stderr, "input file (%s) contains an illegal expression:\n%s\n",
                file_name.c_str(), error_message.c_str());
        return 1;
    }
    fprintf(stderr, "analyzing expression file: '%s'\n",
            file_name.c_str());
    state.report(verbose);
    return 0;
}

int main(int argc, char **argv) {
    MyApp my_app;
    return my_app.Entry(argc, argv);
}

//-----------------------------------------------------------------------------

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/gbdt.h>
#include <vespa/eval/eval/vm_forest.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/fast_forest.h>
#include <vespa/eval/eval/llvm/deinline_forest.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "model.cpp"

using namespace vespalib::eval;
using namespace vespalib::eval::nodes;
using namespace vespalib::eval::gbdt;

//-----------------------------------------------------------------------------

bool is_little_endian() {
    uint32_t value = 0;
    uint8_t bytes[4] = {0, 1, 2, 3};
    static_assert(sizeof(bytes) == sizeof(value));
    memcpy(&value, bytes, sizeof(bytes));
    return (value == 0x03020100);
}

double eval_double(const Function &function, const std::vector<double> &params) {
    NodeTypes node_types(function, std::vector<ValueType>(params.size(), ValueType::double_type()));
    InterpretedFunction ifun(SimpleValueBuilderFactory::get(), function, node_types);
    InterpretedFunction::Context ctx(ifun);
    SimpleParams fun_params(params);
    return ifun.eval(ctx, fun_params).as_double();
}

double my_resolve(void *ctx, size_t idx) { return ((double*)ctx)[idx]; }

double eval_compiled(const CompiledFunction &cfun, std::vector<double> &params) {
    ASSERT_EQUAL(params.size(), cfun.num_params());
    if (cfun.pass_params() == PassParams::ARRAY) {
        return cfun.get_function()(&params[0]);
    }
    if (cfun.pass_params() == PassParams::LAZY) {
        return cfun.get_lazy_function()(my_resolve, &params[0]);
    }
    return 31212.0;
}

double eval_ff(const FastForest &ff, FastForest::Context &ctx, const std::vector<double> &params) {
    std::vector<float> my_params(params.begin(), params.end());
    return ff.eval(ctx, &my_params[0]);
}

//-----------------------------------------------------------------------------

TEST("require that tree stats can be calculated") {
    for (size_t tree_size = 2; tree_size < 64; ++tree_size) {
        EXPECT_EQUAL(tree_size, TreeStats(Function::parse(Model().make_tree(tree_size))->root()).size);
    }

    TreeStats stats1(Function::parse("if((a<1),1.0,if((b in [1,2,3]),if((c in [1]),2.0,3.0),4.0))")->root());
    EXPECT_EQUAL(3u, stats1.num_params);
    EXPECT_EQUAL(4u, stats1.size);
    EXPECT_EQUAL(1u, stats1.num_less_checks);
    EXPECT_EQUAL(2u, stats1.num_in_checks);
    EXPECT_EQUAL(0u, stats1.num_inverted_checks);
    EXPECT_EQUAL(3u, stats1.max_set_size);

    TreeStats stats2(Function::parse("if((d in [1]),10.0,if(!(e>=1),20.0,30.0))")->root());
    EXPECT_EQUAL(2u, stats2.num_params);
    EXPECT_EQUAL(3u, stats2.size);
    EXPECT_EQUAL(0u, stats2.num_less_checks);
    EXPECT_EQUAL(1u, stats2.num_in_checks);
    EXPECT_EQUAL(1u, stats2.num_inverted_checks);
    EXPECT_EQUAL(1u, stats2.max_set_size);
}

TEST("require that trees can be extracted from forest") {
    for (size_t tree_size = 10; tree_size < 20; ++tree_size) {
        for (size_t forest_size = 10; forest_size < 20; ++forest_size) {
            vespalib::string expression = Model().make_forest(forest_size, tree_size);
            auto function = Function::parse(expression);
            std::vector<const Node *> trees = extract_trees(function->root());
            EXPECT_EQUAL(forest_size, trees.size());
            for (const Node *tree: trees) {
                EXPECT_EQUAL(tree_size, TreeStats(*tree).size);
            }
        }
    }
}

TEST("require that forest stats can be calculated") {
    auto function = Function::parse("if((a<1),1.0,if((b in [1,2,3]),if((c in [1]),2.0,3.0),4.0))+"
                                    "if((d in [1]),10.0,if(!(e>=1),20.0,30.0))+"
                                    "if((a<1),10.0,if(!(e>=1),20.0,30.0))");
    std::vector<const Node *> trees = extract_trees(function->root());
    ForestStats stats(trees);
    EXPECT_EQUAL(5u, stats.num_params);
    EXPECT_EQUAL(3u, stats.num_trees);
    EXPECT_EQUAL(10u, stats.total_size);
    ASSERT_EQUAL(2u, stats.tree_sizes.size());
    EXPECT_EQUAL(3u, stats.tree_sizes[0].size);
    EXPECT_EQUAL(2u, stats.tree_sizes[0].count);
    EXPECT_EQUAL(4u, stats.tree_sizes[1].size);
    EXPECT_EQUAL(1u, stats.tree_sizes[1].count);
    EXPECT_EQUAL(2u, stats.total_less_checks);
    EXPECT_EQUAL(3u, stats.total_in_checks);
    EXPECT_EQUAL(2u, stats.total_inverted_checks);
    EXPECT_EQUAL(3u, stats.max_set_size);
}

double expected_path(const vespalib::string &forest) {
    return ForestStats(extract_trees(Function::parse(forest)->root())).total_expected_path_length;
}

TEST("require that expected path length is calculated correctly") {
    EXPECT_EQUAL(0.0, expected_path("1"));
    EXPECT_EQUAL(0.0, expected_path("if(1,2,3)"));
    EXPECT_EQUAL(1.0, expected_path("if(a<1,2,3)"));
    EXPECT_EQUAL(1.0, expected_path("if(b in [1,2,3],2,3)"));
    EXPECT_EQUAL(2.0, expected_path("if(a<1,2,3)+if(a<1,2,3)"));
    EXPECT_EQUAL(3.0, expected_path("if(a<1,2,3)+if(a<1,2,3)+if(a<1,2,3)"));
    EXPECT_EQUAL(0.50*1.0 + 0.50*2.0, expected_path("if(a<1,1,if(a<1,2,3))"));
    EXPECT_EQUAL(0.25*1.0 + 0.75*2.0, expected_path("if(a<1,1,if(a<1,2,3),0.25)"));
    EXPECT_EQUAL(0.75*1.0 + 0.25*2.0, expected_path("if(a<1,1,if(a<1,2,3),0.75)"));
}

double average_path(const vespalib::string &forest) {
    return ForestStats(extract_trees(Function::parse(forest)->root())).total_average_path_length;
}

TEST("require that average path length is calculated correctly") {
    EXPECT_EQUAL(0.0, average_path("1"));
    EXPECT_EQUAL(0.0, average_path("if(1,2,3)"));
    EXPECT_EQUAL(1.0, average_path("if(a<1,2,3)"));
    EXPECT_EQUAL(1.0, average_path("if(b in [1,2,3],2,3)"));
    EXPECT_EQUAL(2.0, average_path("if(a<1,2,3)+if(a<1,2,3)"));
    EXPECT_EQUAL(3.0, average_path("if(a<1,2,3)+if(a<1,2,3)+if(a<1,2,3)"));
    EXPECT_EQUAL(5.0/3.0, average_path("if(a<1,1,if(a<1,2,3))"));
    EXPECT_EQUAL(5.0/3.0, average_path("if(a<1,1,if(a<1,2,3),0.25)"));
    EXPECT_EQUAL(5.0/3.0, average_path("if(a<1,1,if(a<1,2,3),0.75)"));
}

double count_tuned(const vespalib::string &forest) {
    return ForestStats(extract_trees(Function::parse(forest)->root())).total_tuned_checks;
}

TEST("require that tuned checks are counted correctly") {
    EXPECT_EQUAL(0.0, count_tuned("if(a<1,2,3)"));
    EXPECT_EQUAL(0.0, count_tuned("if(a<1,2,3,0.5)")); // NB: no explicit tuned flag
    EXPECT_EQUAL(1.0, count_tuned("if(a<1,2,3,0.3)"));
    EXPECT_EQUAL(1.0, count_tuned("if(b in [1,2,3],2,3,0.8)"));
    EXPECT_EQUAL(2.0, count_tuned("if(a<1,2,3,0.3)+if(a<1,2,3,0.8)"));
    EXPECT_EQUAL(3.0, count_tuned("if(a<1,2,3,0.3)+if(a<1,2,3,0.4)+if(a<1,2,3,0.9)"));
    EXPECT_EQUAL(1.0, count_tuned("if(a<1,1,if(a<1,2,3),0.25)"));
    EXPECT_EQUAL(2.0, count_tuned("if(a<1,1,if(a<1,2,3,0.2),0.25)"));
}

//-----------------------------------------------------------------------------

struct DummyForest0 : public Forest {
    static double eval(const Forest *, const double *) { return 1234.0; }
    static Optimize::Result optimize(const ForestStats &, const std::vector<const nodes::Node *> &) {
        return Optimize::Result(Forest::UP(new DummyForest0()), eval);
    }
};

//-----------------------------------------------------------------------------

struct DummyForest1 : public Forest {
    size_t num_trees;
    explicit DummyForest1(size_t num_trees_in) : num_trees(num_trees_in) {}
    static double eval(const Forest *forest, const double *) {
        const DummyForest1 &self = *((const DummyForest1 *)forest);
        return double(self.num_trees * 2);
    }
    static Optimize::Result optimize(const ForestStats &stats,
                                     const std::vector<const nodes::Node *> &trees)
    {
        if (stats.num_trees < 50) {
            return Optimize::Result();
        } 
        return Optimize::Result(Forest::UP(new DummyForest1(trees.size())), eval);
    }
};

struct DummyForest2 : public Forest {
    size_t num_trees;
    explicit DummyForest2(size_t num_trees_in) : num_trees(num_trees_in) {}
    static double eval(const Forest *forest, const double *) {
        const DummyForest1 &self = *((const DummyForest1 *)forest);
        return double(self.num_trees);
    }
    static Optimize::Result optimize(const ForestStats &stats,
                                     const std::vector<const nodes::Node *> &trees)
    {
        if (stats.num_trees < 25) {
            return Optimize::Result();
        }
        return Optimize::Result(Forest::UP(new DummyForest2(trees.size())), eval);
    }
};

//-----------------------------------------------------------------------------

TEST("require that trees cannot be optimized by a forest optimizer when using SEPARATE params") {
    Optimize::Chain chain({DummyForest0::optimize});
    auto function = Function::parse("if((a<1),1.0,if((b<1),if((c<1),2.0,3.0),4.0))+"
                                    "if((d<1),10.0,if((e<1),if((f<1),20.0,30.0),40.0))");
    CompiledFunction compiled_function(*function, PassParams::SEPARATE, chain);
    CompiledFunction compiled_function_array(*function, PassParams::ARRAY, chain);
    CompiledFunction compiled_function_lazy(*function, PassParams::LAZY, chain);
    EXPECT_EQUAL(0u, compiled_function.get_forests().size());
    EXPECT_EQUAL(1u, compiled_function_array.get_forests().size());
    EXPECT_EQUAL(1u, compiled_function_lazy.get_forests().size());
    auto f = compiled_function.get_function<6>();
    auto f_array = compiled_function_array.get_function();
    auto f_lazy = compiled_function_lazy.get_lazy_function();
    std::vector<double> params = {1.5, 0.5, 0.5, 1.5, 0.5, 0.5};
    EXPECT_EQUAL(22.0, f(params[0], params[1], params[2], params[3], params[4], params[5]));
    EXPECT_EQUAL(1234.0, f_array(&params[0]));
    EXPECT_EQUAL(1234.0, f_lazy(my_resolve, &params[0]));
}

TEST("require that trees can be optimized by a forest optimizer when using ARRAY params") {
    Optimize::Chain chain({DummyForest1::optimize, DummyForest2::optimize});
    size_t tree_size = 20;
    for (size_t forest_size = 10; forest_size <= 100; forest_size += 10) {
        vespalib::string expression = Model().make_forest(forest_size, tree_size);
        auto function = Function::parse(expression);
        CompiledFunction compiled_function(*function, PassParams::ARRAY, chain);
        std::vector<double> inputs(function->num_params(), 0.5);
        if (forest_size < 25) {
            EXPECT_EQUAL(0u, compiled_function.get_forests().size());
            EXPECT_EQUAL(eval_double(*function, inputs), compiled_function.get_function()(&inputs[0]));
        } else if (forest_size < 50) {
            EXPECT_EQUAL(1u, compiled_function.get_forests().size());
            EXPECT_EQUAL(double(forest_size), compiled_function.get_function()(&inputs[0]));
        } else {
            EXPECT_EQUAL(1u, compiled_function.get_forests().size());
            EXPECT_EQUAL(double(2 * forest_size), compiled_function.get_function()(&inputs[0]));
        }
    }
}

TEST("require that trees can be optimized by a forest optimizer when using LAZY params") {
    Optimize::Chain chain({DummyForest1::optimize, DummyForest2::optimize});
    size_t tree_size = 20;
    for (size_t forest_size = 10; forest_size <= 100; forest_size += 10) {
        vespalib::string expression = Model().make_forest(forest_size, tree_size);
        auto function = Function::parse(expression);
        CompiledFunction compiled_function(*function, PassParams::LAZY, chain);
        std::vector<double> inputs(function->num_params(), 0.5);
        if (forest_size < 25) {
            EXPECT_EQUAL(0u, compiled_function.get_forests().size());
            EXPECT_EQUAL(eval_double(*function, inputs), compiled_function.get_lazy_function()(my_resolve, &inputs[0]));
        } else if (forest_size < 50) {
            EXPECT_EQUAL(1u, compiled_function.get_forests().size());
            EXPECT_EQUAL(double(forest_size), compiled_function.get_lazy_function()(my_resolve, &inputs[0]));
        } else {
            EXPECT_EQUAL(1u, compiled_function.get_forests().size());
            EXPECT_EQUAL(double(2 * forest_size), compiled_function.get_lazy_function()(my_resolve, &inputs[0]));
        }
    }
}

//-----------------------------------------------------------------------------

Optimize::Chain less_only_vm_chain({VMForest::less_only_optimize});
Optimize::Chain general_vm_chain({VMForest::general_optimize});

TEST("require that less only VM tree optimizer works") {
    auto function = Function::parse("if((a<1),1.0,if((b<1),if((c<1),2.0,3.0),4.0))+"
                                    "if((d<1),10.0,if((e<1),if((f<1),20.0,30.0),40.0))");
    CompiledFunction compiled_function(*function, PassParams::ARRAY, less_only_vm_chain);
    EXPECT_EQUAL(1u, compiled_function.get_forests().size());
    auto f = compiled_function.get_function();
    EXPECT_EQUAL(11.0, f(&std::vector<double>({0.5, 0.0, 0.0, 0.5, 0.0, 0.0})[0]));
    EXPECT_EQUAL(22.0, f(&std::vector<double>({1.5, 0.5, 0.5, 1.5, 0.5, 0.5})[0]));
    EXPECT_EQUAL(33.0, f(&std::vector<double>({1.5, 0.5, 1.5, 1.5, 0.5, 1.5})[0]));
    EXPECT_EQUAL(44.0, f(&std::vector<double>({1.5, 1.5, 0.0, 1.5, 1.5, 0.0})[0]));
}

TEST("require that models with in checks are rejected by less only vm optimizer") {
    auto function = Function::parse(Model().less_percent(100).make_forest(300, 30));
    auto trees = extract_trees(function->root());
    ForestStats stats(trees);
    EXPECT_TRUE(Optimize::apply_chain(less_only_vm_chain, stats, trees).valid());
    stats.total_in_checks = 1;
    EXPECT_TRUE(!Optimize::apply_chain(less_only_vm_chain, stats, trees).valid());
}

TEST("require that models with inverted checks are rejected by less only vm optimizer") {
    auto function = Function::parse(Model().less_percent(100).make_forest(300, 30));
    auto trees = extract_trees(function->root());
    ForestStats stats(trees);
    EXPECT_TRUE(Optimize::apply_chain(less_only_vm_chain, stats, trees).valid());
    stats.total_inverted_checks = 1;
    EXPECT_TRUE(!Optimize::apply_chain(less_only_vm_chain, stats, trees).valid());
}

TEST("require that general VM tree optimizer works") {
    auto function = Function::parse("if((a<1),1.0,if((b in [1,2,3]),if((c in [1]),2.0,3.0),4.0))+"
                                        "if((d in [1]),10.0,if(!(e>=1),if((f<1),20.0,30.0),40.0))");
    CompiledFunction compiled_function(*function, PassParams::ARRAY, general_vm_chain);
    EXPECT_EQUAL(1u, compiled_function.get_forests().size());
    auto f = compiled_function.get_function();
    EXPECT_EQUAL(11.0, f(&std::vector<double>({0.5, 0.0, 0.0, 1.0, 0.0, 0.0})[0]));
    EXPECT_EQUAL(22.0, f(&std::vector<double>({1.5, 2.0, 1.0, 2.0, 0.5, 0.5})[0]));
    EXPECT_EQUAL(33.0, f(&std::vector<double>({1.5, 2.0, 2.0, 2.0, 0.5, 1.5})[0]));
    EXPECT_EQUAL(44.0, f(&std::vector<double>({1.5, 5.0, 0.0, 2.0, 1.5, 0.0})[0]));
}

TEST("require that models with too large sets are rejected by general vm optimizer") {
    auto function = Function::parse(Model().less_percent(80).make_forest(300, 30));
    auto trees = extract_trees(function->root());
    ForestStats stats(trees);
    EXPECT_TRUE(stats.total_in_checks > 0);
    EXPECT_TRUE(Optimize::apply_chain(general_vm_chain, stats, trees).valid());
    stats.max_set_size = 256;
    EXPECT_TRUE(!Optimize::apply_chain(general_vm_chain, stats, trees).valid());
}

TEST("require that FastForest model evaluation works") {
    auto function = Function::parse("if((a<2),1.0,if((b<2),if((c<2),2.0,3.0),4.0))+"
                                        "if(!(c>=1),10.0,if((a<1),if((b<1),20.0,30.0),40.0))");
    CompiledFunction compiled(*function, PassParams::ARRAY, Optimize::none);
    auto f = compiled.get_function();
    EXPECT_TRUE(compiled.get_forests().empty());
    auto forest = FastForest::try_convert(*function);
    ASSERT_TRUE(forest);
    auto ctx = forest->create_context();
    std::vector<double> p1({0.5, 0.5, 0.5}); // all true: 1.0 + 10.0
    std::vector<double> p2({2.5, 2.5, 2.5}); // all false: 4.0 + 40.0
    std::vector<double> pn(3, std::numeric_limits<double>::quiet_NaN()); // default: 4.0 + 10.0
    EXPECT_EQUAL(eval_ff(*forest, *ctx, p1), f(&p1[0]));
    EXPECT_EQUAL(eval_ff(*forest, *ctx, p2), f(&p2[0]));
    EXPECT_EQUAL(eval_ff(*forest, *ctx, pn), f(&pn[0]));
    EXPECT_EQUAL(eval_ff(*forest, *ctx, p1), f(&p1[0]));
}

//-----------------------------------------------------------------------------

TEST("require that forests evaluate to approximately the same for all evaluation options") {
    for (PassParams pass_params: {PassParams::ARRAY, PassParams::LAZY}) {
        for (size_t tree_size: std::vector<size_t>({20})) {
            for (size_t num_trees: std::vector<size_t>({60})) {
                for (size_t less_percent: std::vector<size_t>({100, 80})) {
                    for (size_t invert_percent: std::vector<size_t>({0, 50})) {
                        vespalib::string expression = Model().less_percent(less_percent).invert_percent(invert_percent).make_forest(num_trees, tree_size);
                        auto function = Function::parse(expression);
                        auto forest = FastForest::try_convert(*function);
                        EXPECT_EQUAL(bool(forest), bool(less_percent == 100));
                        CompiledFunction none(*function, pass_params, Optimize::none);
                        CompiledFunction deinline(*function, pass_params, DeinlineForest::optimize_chain);
                        CompiledFunction vm_forest(*function, pass_params, VMForest::optimize_chain);
                        EXPECT_EQUAL(0u, none.get_forests().size());
                        ASSERT_EQUAL(1u, deinline.get_forests().size());
                        EXPECT_TRUE(dynamic_cast<DeinlineForest*>(deinline.get_forests()[0].get()) != nullptr);
                        ASSERT_EQUAL(1u, vm_forest.get_forests().size());
                        EXPECT_TRUE(dynamic_cast<VMForest*>(vm_forest.get_forests()[0].get()) != nullptr);
                        std::vector<double> inputs(function->num_params(), 0.5);
                        std::vector<double> inputs_nan(function->num_params(), std::numeric_limits<double>::quiet_NaN());
                        double expected = eval_double(*function, inputs);
                        double expected_nan = eval_double(*function, inputs_nan);
                        EXPECT_EQUAL(expected, eval_compiled(none, inputs));
                        EXPECT_EQUAL(expected, eval_compiled(deinline, inputs));
                        EXPECT_EQUAL(expected, eval_compiled(vm_forest, inputs));
                        EXPECT_EQUAL(expected_nan, eval_compiled(none, inputs_nan));
                        EXPECT_EQUAL(expected_nan, eval_compiled(deinline, inputs_nan));
                        EXPECT_EQUAL(expected_nan, eval_compiled(vm_forest, inputs_nan));
                        if (forest) {
                            auto ctx = forest->create_context();
                            EXPECT_EQUAL(expected, eval_ff(*forest, *ctx, inputs));
                            EXPECT_EQUAL(expected_nan, eval_ff(*forest, *ctx, inputs_nan));
                        }
                    }
                }
            }
        }
    }
}

TEST("require that fast forest evaluation is correct for all tree size categories") {
    for (size_t tree_size: std::vector<size_t>({7,15,30,61,127})) {
        for (size_t num_trees: std::vector<size_t>({127})) {
            for (size_t num_features: std::vector<size_t>({35})) {
                for (size_t less_percent: std::vector<size_t>({100})) {
                    for (size_t invert_percent: std::vector<size_t>({50})) {
                        vespalib::string expression = Model().max_features(num_features).less_percent(less_percent).invert_percent(invert_percent).make_forest(num_trees, tree_size);
                        auto function = Function::parse(expression);
                        auto forest = FastForest::try_convert(*function);
                        if ((tree_size <= 64) || is_little_endian()) {
                            ASSERT_TRUE(forest);
                            TEST_STATE(forest->impl_name().c_str());
                            std::vector<double> inputs(function->num_params(), 0.5);
                            std::vector<double> inputs_nan(function->num_params(), std::numeric_limits<double>::quiet_NaN());
                            double expected = eval_double(*function, inputs);
                            double expected_nan = eval_double(*function, inputs_nan);
                            auto ctx = forest->create_context();
                            EXPECT_EQUAL(expected, eval_ff(*forest, *ctx, inputs));
                            EXPECT_EQUAL(expected_nan, eval_ff(*forest, *ctx, inputs_nan));
                        }
                    }
                }
            }
        }
    }
}

//-----------------------------------------------------------------------------

TEST("require that GDBT expressions can be detected") {
    auto function = Function::parse("if((a<1),1.0,if((b in [1,2,3]),if((c in [1]),2.0,3.0),4.0))+"
                                    "if((d in [1]),10.0,if(!(e>=1),20.0,30.0))+"
                                    "if((d in [1]),10.0,if(!(e>=1),20.0,30.0))");
    EXPECT_TRUE(contains_gbdt(function->root(), 9));
    EXPECT_TRUE(!contains_gbdt(function->root(), 10));
}

TEST("require that wrapped GDBT expressions can be detected") {
    auto function = Function::parse("10*(if((a<1),1.0,if((b in [1,2,3]),if((c in [1]),2.0,3.0),4.0))+"
                                    "if((d in [1]),10.0,if((e<1),20.0,30.0))+"
                                    "if((d in [1]),10.0,if((e<1),20.0,30.0)))");
    EXPECT_TRUE(contains_gbdt(function->root(), 9));
    EXPECT_TRUE(!contains_gbdt(function->root(), 10));
}

TEST("require that lazy parameters are not suggested for GBDT models") {
    auto function = Function::parse(Model().make_forest(10, 8));
    EXPECT_TRUE(!CompiledFunction::should_use_lazy_params(*function));
}

TEST("require that lazy parameters can be suggested for small GBDT models") {
    auto function = Function::parse("if((a<1),1.0,if((b in [1,2,3]),if((c in [1]),2.0,3.0),4.0))+"
                                    "if((d in [1]),10.0,if((e<1),20.0,30.0))+"
                                    "if((d in [1]),10.0,if((e<1),20.0,30.0))");
    EXPECT_TRUE(CompiledFunction::should_use_lazy_params(*function));
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }

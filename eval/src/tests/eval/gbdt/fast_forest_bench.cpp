// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/fast_forest.h>
#include <vespa/eval/eval/vm_forest.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include "model.cpp"

using namespace vespalib::eval;
using namespace vespalib::eval::gbdt;

template <typename T>
void estimate_cost(size_t num_params, const char *label, const T &impl) {
    std::vector<double> inputs_min(num_params, 0.25);
    std::vector<double> inputs_med(num_params, 0.50);
    std::vector<double> inputs_max(num_params, 0.75);
    std::vector<double> inputs_nan(num_params, std::numeric_limits<double>::quiet_NaN());
    double us_min = impl.estimate_cost_us(inputs_min, 5.0);
    double us_med = impl.estimate_cost_us(inputs_med, 5.0);
    double us_max = impl.estimate_cost_us(inputs_max, 5.0);
    double us_nan = impl.estimate_cost_us(inputs_nan, 5.0);
    fprintf(stderr, "[%12s] (per 100 eval): [low values] %6.3f ms, [medium values] %6.3f ms, [high values] %6.3f ms, [nan values] %6.3f ms\n",
            label, (us_min / 10.0), (us_med / 10.0), (us_max / 10.0), (us_nan / 10.0));
}

void run_fast_forest_bench() {
    for (size_t tree_size: std::vector<size_t>({8,16,32,64,128,256})) {
        for (size_t num_trees: std::vector<size_t>({100, 500, 2500, 5000, 10000})) {
            for (size_t max_features: std::vector<size_t>({200})) {
                for (size_t less_percent: std::vector<size_t>({100})) {
                    for (size_t invert_percent: std::vector<size_t>({50})) {
                        fprintf(stderr, "\n=== features: %zu, num leafs: %zu, num trees: %zu\n", max_features, tree_size, num_trees);
                        vespalib::string expression = Model().max_features(max_features).less_percent(less_percent).invert_percent(invert_percent).make_forest(num_trees, tree_size);
                        auto function = Function::parse(expression);
                        for (size_t min_bits = std::max(size_t(8), tree_size); true; min_bits *= 2) {
                            auto forest = FastForest::try_convert(*function, min_bits, 64);
                            if (forest) {
                                estimate_cost(function->num_params(), forest->impl_name().c_str(), *forest);
                            }
                            if (min_bits > 64) {
                                break;
                            }
                        }
                        estimate_cost(function->num_params(), "vm forest", CompiledFunction(*function, PassParams::ARRAY, VMForest::optimize_chain));
                    }
                }
            }
        }
    }
    fprintf(stderr, "\n");
}

int main(int, char **) {
    run_fast_forest_bench();
    return 0;
}

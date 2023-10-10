// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "deinline_forest.h"

namespace vespalib {
namespace eval {
namespace gbdt {

DeinlineForest::DeinlineForest(const std::vector<const nodes::Node *> &trees)
{
    size_t idx = 0;
    size_t num_fragments = 0;
    while (idx < trees.size()) {
        size_t fragment_size = 0;
        std::vector<const nodes::Node *> fragment;
        while ((idx < trees.size()) && (fragment_size < 256)) {
            fragment_size += TreeStats(*trees[idx]).size;
            fragment.push_back(trees[idx++]);
        }
        ForestStats stats(fragment);
        size_t id = _llvm_wrapper.make_forest_fragment(stats.num_params, fragment);
        assert(id == num_fragments);
        ++num_fragments;
    }
    _llvm_wrapper.compile();
    for (size_t id = 0; id < num_fragments; ++id) {
        _fragments.push_back((array_function)_llvm_wrapper.get_function_address(id));
    }
}

Optimize::Result
DeinlineForest::optimize(const ForestStats &,
                         const std::vector<const nodes::Node *> &trees)
{
    return Optimize::Result(Forest::UP(new DeinlineForest(trees)), eval);
}

double
DeinlineForest::eval(const Forest *forest, const double *input)
{
    const DeinlineForest &self = *((const DeinlineForest *)forest);
    double sum = 0.0;
    for (auto fragment: self._fragments) {
        sum += fragment(input);
    }
    return sum;
}

Optimize::Chain DeinlineForest::optimize_chain({optimize});

} // namespace vespalib::eval::gbdt
} // namespace vespalib::eval
} // namespace vespalib

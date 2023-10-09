// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <memory>

namespace vespalib {
namespace eval {

namespace nodes { struct Node; }

namespace gbdt {

//-----------------------------------------------------------------------------

/**
 * Function used to map out individual GBDT trees from a GBDT forest.
 **/
std::vector<const nodes::Node *> extract_trees(const nodes::Node &node);

/**
 * Statistics for a single GBDT tree.
 **/
struct TreeStats {
    size_t size;
    size_t num_less_checks;     // foo < 2.5
    size_t num_in_checks;       // foo in [1,2,3]
    size_t num_inverted_checks; // !(foo >= 2.5)
    size_t num_tuned_checks;
    size_t max_set_size;
    double expected_path_length;
    double average_path_length;
    size_t num_params;
    explicit TreeStats(const nodes::Node &tree);
private:
    double traverse(const nodes::Node &tree, size_t depth, size_t &sum_path);
};

/**
 * Statistics for a GBDT forest.
 **/
struct ForestStats {
    struct TreeSize {
        size_t size;
        size_t count;
    };
    size_t num_trees;
    size_t total_size;
    std::vector<TreeSize> tree_sizes;
    size_t total_less_checks;
    size_t total_in_checks;
    size_t total_inverted_checks;
    size_t total_tuned_checks;
    size_t max_set_size;
    double total_expected_path_length;
    double total_average_path_length;
    size_t num_params;
    explicit ForestStats(const std::vector<const nodes::Node *> &trees);
};

//-----------------------------------------------------------------------------

/**
 * Check if the given sub-expression contains GBDT. This function
 * returns true if the number of tree/forest nodes exceeds the given
 * limit.
 **/
bool contains_gbdt(const nodes::Node &node, size_t limit);

//-----------------------------------------------------------------------------

/**
 * A Forest object represents deletable custom prepared state that may
 * be used to evaluate a GBDT forest from within LLVM generated
 * machine code. It is very important that the evaluation function
 * used is passed exactly the subclass of Forest it expects. This is
 * why Optimize::Result bundles together both the prepared state
 * (Forest object) and the evaluation function reference; they are
 * chosen at the same time at the same place.
 **/
struct Forest {
    using UP = std::unique_ptr<Forest>;
    using eval_function = double (*)(const Forest *self, const double *args);
    virtual ~Forest() {}
};

/**
 * Definitions and helper functions related to custom GBDT forest
 * optimization. The optimization chain named 'best' is used by
 * default. The one named 'none' results in no special handling for
 * GBDT forests.
 **/
struct Optimize {
    struct Result {
        Forest::UP forest;
        Forest::eval_function eval;
        Result() : forest(nullptr), eval(nullptr) {}
        Result(Forest::UP &&forest_in, Forest::eval_function eval_in)
            : forest(std::move(forest_in)), eval(eval_in) {}
        Result(Result &&rhs) : forest(std::move(rhs.forest)), eval(rhs.eval) {}
        bool valid() const { return (forest.get() != nullptr); }
    };
    using optimize_function = Result (*)(const ForestStats &stats,
                                         const std::vector<const nodes::Node *> &trees);
    using Chain = std::vector<optimize_function>;
    static Result select_best(const ForestStats &stats,
                              const std::vector<const nodes::Node *> &trees);
    static Chain best;
    static Chain none;
    static Result apply_chain(const Chain &chain,
                              const ForestStats &stats,
                              const std::vector<const nodes::Node *> &trees) {
        for (optimize_function optimize: chain) {
            Result result = optimize(stats, trees);
            if (result.valid()) {
                return result;
            }
        }
        return Result();
    }
    // Optimize() = delete;
};

//-----------------------------------------------------------------------------

} // namespace vespalib::eval::gbdt
} // namespace vespalib::eval
} // namespace vespalib

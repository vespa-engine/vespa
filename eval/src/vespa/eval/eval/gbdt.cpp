// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "gbdt.h"
#include <vespa/vespalib/eval/basic_nodes.h>
#include <vespa/vespalib/eval/call_nodes.h>
#include <vespa/vespalib/eval/operator_nodes.h>
#include "vm_forest.h"

namespace vespalib {
namespace eval {
namespace gbdt {

//-----------------------------------------------------------------------------

std::vector<const nodes::Node *> extract_trees(const nodes::Node &node) {
    std::vector<const nodes::Node *> trees;
    std::vector<const nodes::Node *> todo;
    if (node.is_tree()) {
        trees.push_back(&node);
    } else if (node.is_forest()) {
        todo.push_back(&node);
    }
    while (!todo.empty()) {
        const nodes::Node &forest = *todo.back(); todo.pop_back();
        for (size_t i = 0; i < forest.num_children(); ++i) {
            const nodes::Node &child = forest.get_child(i);
            if (child.is_tree()) {
                trees.push_back(&child);
            } else if (child.is_forest()) {
                todo.push_back(&child);
            }
        }
    }
    return trees;
}

//-----------------------------------------------------------------------------

TreeStats::TreeStats(const nodes::Node &tree)
    : size(0),
      num_less_checks(0),
      num_in_checks(0),
      num_tuned_checks(0),
      max_set_size(0),
      expected_path_length(0.0),
      average_path_length(0.0)
{
    size_t sum_path = 0.0;
    expected_path_length = traverse(tree, 0, sum_path);
    average_path_length = double(sum_path) / double(size);
}

double
TreeStats::traverse(const nodes::Node &node, size_t depth, size_t &sum_path) {
    auto if_node = nodes::as<nodes::If>(node);
    if (if_node) {
        double p_true = if_node->p_true();
        if (p_true != 0.5) {
            ++num_tuned_checks;
        }
        double true_path = traverse(if_node->true_expr(), depth + 1, sum_path);
        double false_path = traverse(if_node->false_expr(), depth + 1, sum_path);
        auto less = nodes::as<nodes::Less>(if_node->cond());
        auto in = nodes::as<nodes::In>(if_node->cond());
        if (less) {
            ++num_less_checks;
        } else {
            assert(in);
            ++num_in_checks;
            auto array = nodes::as<nodes::Array>(in->rhs());
            size_t array_size = (array) ? array->size() : 1;
            max_set_size = std::max(max_set_size, array_size);
        }
        return 1.0 + (p_true * true_path) + ((1.0 - p_true) * false_path);
    } else {
        ++size;
        sum_path += depth;
        return 0.0;
    }
}

ForestStats::ForestStats(const std::vector<const nodes::Node *> &trees)
    : num_trees(trees.size()),
      total_size(0),
      tree_sizes(),
      total_less_checks(0),
      total_in_checks(0),
      total_tuned_checks(0),
      max_set_size(0),
      total_expected_path_length(0.0),
      total_average_path_length(0.0)
{
    std::map<size_t,size_t> size_map;
    for (const nodes::Node *tree: trees) {
        TreeStats stats(*tree);
        total_size += stats.size;
        ++size_map[stats.size];
        total_less_checks += stats.num_less_checks;
        total_in_checks += stats.num_in_checks;
        total_tuned_checks += stats.num_tuned_checks;
        max_set_size = std::max(max_set_size, stats.max_set_size);
        total_expected_path_length += stats.expected_path_length;
        total_average_path_length += stats.average_path_length;
    }
    for (auto const &size: size_map) {
        tree_sizes.push_back(TreeSize{size.first, size.second});
    }
}

//-----------------------------------------------------------------------------

Optimize::Result
Optimize::select_best(const ForestStats &stats,
                      const std::vector<const nodes::Node *> &trees)
{
    double path_len = stats.total_average_path_length;
    if ((stats.tree_sizes.back().size > 12) && (path_len > 2500.0)) {
        return apply_chain(VMForest::optimize_chain, stats, trees);
    }
    return Optimize::Result();
}

Optimize::Chain Optimize::best({select_best});
Optimize::Chain Optimize::none;

//-----------------------------------------------------------------------------

} // namespace vespalib::eval::gbdt
} // namespace vespalib::eval
} // namespace vespalib

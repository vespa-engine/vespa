// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gbdt.h"
#include "vm_forest.h"
#include "node_traverser.h"
#include <vespa/eval/eval/basic_nodes.h>
#include <vespa/eval/eval/call_nodes.h>
#include <vespa/eval/eval/operator_nodes.h>

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
      num_inverted_checks(0),
      num_tuned_checks(0),
      max_set_size(0),
      expected_path_length(0.0),
      average_path_length(0.0),
      num_params(0)
{
    size_t sum_path = 0.0;
    expected_path_length = traverse(tree, 0, sum_path);
    average_path_length = double(sum_path) / double(size);
}

double
TreeStats::traverse(const nodes::Node &node, size_t depth, size_t &sum_path) {
    if (auto if_node = nodes::as<nodes::If>(node)) {
        double p_true = if_node->p_true();
        if (p_true != 0.5) {
            ++num_tuned_checks;
        }
        double true_path = traverse(if_node->true_expr(), depth + 1, sum_path);
        double false_path = traverse(if_node->false_expr(), depth + 1, sum_path);
        auto less = nodes::as<nodes::Less>(if_node->cond());
        auto in = nodes::as<nodes::In>(if_node->cond());
        auto inverted = nodes::as<nodes::Not>(if_node->cond());
        if (less) {
            auto symbol = nodes::as<nodes::Symbol>(less->lhs());
            assert(symbol);
            num_params = std::max(num_params, size_t(symbol->id() + 1));
            ++num_less_checks;
        } else if (in) {
            auto symbol = nodes::as<nodes::Symbol>(in->child());
            assert(symbol);
            num_params = std::max(num_params, size_t(symbol->id() + 1));
            ++num_in_checks;
            max_set_size = std::max(max_set_size, in->num_entries());
        } else {
            assert(inverted);
            auto ge = nodes::as<nodes::GreaterEqual>(inverted->child());
            assert(ge);
            auto symbol = nodes::as<nodes::Symbol>(ge->lhs());
            assert(symbol);
            num_params = std::max(num_params, size_t(symbol->id() + 1));
            ++num_inverted_checks;
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
      total_inverted_checks(0),
      total_tuned_checks(0),
      max_set_size(0),
      total_expected_path_length(0.0),
      total_average_path_length(0.0),
      num_params(0)
{
    std::map<size_t,size_t> size_map;
    for (const nodes::Node *tree: trees) {
        TreeStats stats(*tree);
        num_params = std::max(num_params, stats.num_params);
        total_size += stats.size;
        ++size_map[stats.size];
        total_less_checks += stats.num_less_checks;
        total_in_checks += stats.num_in_checks;
        total_inverted_checks += stats.num_inverted_checks;
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

bool contains_gbdt(const nodes::Node &node, size_t limit) {
    struct FindGBDT : NodeTraverser {
        size_t seen;
        size_t limit;
        explicit FindGBDT(size_t limit_in) : seen(0), limit(limit_in) {}
        bool found() const { return (seen >= limit); }
        bool open(const nodes::Node &) override { return !found(); }
        void close(const nodes::Node &node) override {
            if (node.is_tree() || node.is_forest()) {
                ++seen;
            }
        }
    } findGBDT(limit);
    node.traverse(findGBDT);
    return findGBDT.found(); 
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

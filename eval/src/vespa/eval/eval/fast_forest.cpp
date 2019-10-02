// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_forest.h"
#include "gbdt.h"
#include <vespa/eval/eval/basic_nodes.h>
#include <vespa/eval/eval/call_nodes.h>
#include <vespa/eval/eval/operator_nodes.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <algorithm>
#include <cassert>

namespace vespalib::eval::gbdt {

namespace {

struct BitRange {
    uint32_t first;
    uint32_t last;
    BitRange(uint32_t bit) : first(bit), last(bit) {}
    BitRange(uint32_t a, uint32_t b) : first(a), last(b) {}
    static BitRange join(const BitRange &a, const BitRange &b) {
        assert((a.last + 1) == b.first);
        return BitRange(a.first, b.last);
    }
    ~BitRange() = default;
};

struct CmpNode {
    float value;
    uint32_t tree_id;
    BitRange false_mask;
    bool false_is_default;
    CmpNode(float v, uint32_t t, BitRange m, bool f_def)
        : value(v), tree_id(t), false_mask(m), false_is_default(f_def) {}
    bool operator<(const CmpNode &rhs) const {
        return (value < rhs.value);
    }
    ~CmpNode() = default;
};

struct State {
    using Leafs = std::vector<float>;
    using CmpNodes = std::vector<CmpNode>;
    std::vector<CmpNodes> cmp_nodes;
    std::vector<Leafs> leafs;
    BitRange encode_node(uint32_t tree_id, const nodes::Node &node);
    State(size_t num_params, const std::vector<const nodes::Node *> &trees);
    ~State() = default;
};

BitRange
State::encode_node(uint32_t tree_id, const nodes::Node &node)
{
    auto if_node = nodes::as<nodes::If>(node);
    if (if_node) {
        BitRange true_leafs = encode_node(tree_id, if_node->true_expr());
        BitRange false_leafs = encode_node(tree_id, if_node->false_expr());
        auto less = nodes::as<nodes::Less>(if_node->cond());
        auto inverted = nodes::as<nodes::Not>(if_node->cond());
        if (less) {
            auto symbol = nodes::as<nodes::Symbol>(less->lhs());
            assert(symbol);
            assert(less->rhs().is_const());
            size_t feature = symbol->id();
            assert(feature < cmp_nodes.size());
            cmp_nodes[feature].emplace_back(less->rhs().get_const_value(), tree_id, true_leafs, true);
        } else {
            assert(inverted);
            auto ge = nodes::as<nodes::GreaterEqual>(inverted->child());
            assert(ge);
            auto symbol = nodes::as<nodes::Symbol>(ge->lhs());
            assert(symbol);
            assert(ge->rhs().is_const());
            size_t feature = symbol->id();
            assert(feature < cmp_nodes.size());
            cmp_nodes[feature].emplace_back(ge->rhs().get_const_value(), tree_id, true_leafs, false);
        }
        return BitRange::join(true_leafs, false_leafs);
    } else {
        assert(node.is_const());
        BitRange leaf_range(leafs[tree_id].size());
        leafs[tree_id].push_back(node.get_const_value());
        return leaf_range;
    }
}

State::State(size_t num_params, const std::vector<const nodes::Node *> &trees)
    : cmp_nodes(num_params),
      leafs(trees.size())
{
    for (uint32_t tree_id = 0; tree_id < trees.size(); ++tree_id) {
        BitRange leaf_range = encode_node(tree_id, *trees[tree_id]);
        assert(leaf_range.first == 0);
        assert((leaf_range.last + 1) == leafs[tree_id].size());
    }
    for (CmpNodes &cmp_range: cmp_nodes) {
        assert(!cmp_range.empty());
        std::sort(cmp_range.begin(), cmp_range.end());
    }
}

}

struct FastForestBuilder {

    static FastForest::MaskType get_mask_type(uint32_t idx1, uint32_t idx2) {
        assert(idx1 <= idx2);
        if (idx1 == idx2) {
            return FastForest::MaskType::ONE;
        } else if ((idx1 + 1) == idx2) {
            return FastForest::MaskType::TWO;
        } else {
            return FastForest::MaskType::MANY;
        }
    }

    static FastForest::Mask make_mask(const CmpNode &cmp_node) {
        BitRange range = cmp_node.false_mask;
        assert(range.last < (8 * 256));
        assert(range.first <= range.last);
        uint32_t idx1 = (range.first / 8);
        uint32_t idx2 = (range.last / 8);
        uint8_t bits1 = 0;
        uint8_t bits2 = 0;
        for (uint32_t i = 0; i < 8; ++i) {
            uint32_t bit1 = (idx1 * 8) + i;
            if ((bit1 < range.first) || (bit1 > range.last)) {
                bits1 |= (1 << i);
            }
            uint32_t bit2 = (idx2 * 8) + i;
            if ((bit2 < range.first) || (bit2 > range.last)) {
                bits2 |= (1 << i);
            }
        }
        assert(cmp_node.tree_id < (256 * 256));
        return FastForest::Mask(cmp_node.tree_id, get_mask_type(idx1, idx2), cmp_node.false_is_default,
                                idx1, bits1, idx2, bits2);
    }

    static void build(State &state, FastForest &ff) {
        for (const auto &cmp_nodes: state.cmp_nodes) {
            ff._feature_sizes.push_back(cmp_nodes.size());
            for (const CmpNode &cmp_node: cmp_nodes) {
                ff._values.push_back(cmp_node.value);
                ff._masks.push_back(make_mask(cmp_node));
            }
        }
        for (const auto &leafs: state.leafs) {
            ff._tree_sizes.push_back(leafs.size());
            for (float leaf: leafs) {
                ff._leafs.push_back(leaf);
            }
        }
    }
};

FastForest::Context::Context(const FastForest &ff)
    : _forest(&ff),
      _bytes_per_tree((ff.max_leafs() + 7) / 8),
      _bits(_bytes_per_tree * ff.num_trees()) {}

FastForest::Context::~Context() = default;

FastForest::FastForest() = default;
FastForest::~FastForest() = default;

size_t
FastForest::num_params() const
{
    return _feature_sizes.size();
}

size_t
FastForest::num_trees() const
{
    return _tree_sizes.size();
}

size_t
FastForest::max_leafs() const
{
    size_t res = 0;
    size_t sum = 0;
    for (size_t sz: _tree_sizes) {
        res = std::max(res, sz);
        sum += sz;
    }
    assert(res <= (8 * 256));
    assert(sum == _leafs.size());
    return res;
}

FastForest::UP
FastForest::try_convert(const Function &fun)
{
    const auto &root = fun.root();
    if (!root.is_forest()) {
        // must be only forest
        return FastForest::UP();
    }
    auto trees = gbdt::extract_trees(root);
    if (trees.size() > (256 * 256)) {
        // too many trees
        return FastForest::UP();
    }
    gbdt::ForestStats stats(trees);
    if (stats.total_in_checks > 0) {
        // set membership not supported
        return FastForest::UP();
    }
    if (stats.tree_sizes.back().size > (8 * 256)) {
        // too many leaf nodes per tree
        return FastForest::UP();
    }
    State state(fun.num_params(), trees);
    FastForest::UP res = FastForest::UP(new FastForest());
    FastForestBuilder::build(state, *res);
    assert(fun.num_params() == res->num_params());
    assert(trees.size() == res->num_trees());
    return res;
}

double
FastForest::estimate_cost_us(const std::vector<double> &params, double budget) const
{
    Context ctx(*this);
    auto get_param = [&params](size_t i)->float{ return params[i]; };
    auto self_eval = [&](){ this->eval(ctx, get_param); };
    return BenchmarkTimer::benchmark(self_eval, budget) * 1000.0 * 1000.0;
}

}

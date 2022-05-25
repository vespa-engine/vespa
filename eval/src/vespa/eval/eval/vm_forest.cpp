// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gbdt.h"
#include "vm_forest.h"
#include <vespa/eval/eval/basic_nodes.h>
#include <vespa/eval/eval/call_nodes.h>
#include <vespa/eval/eval/operator_nodes.h>

namespace vespalib {
namespace eval {
namespace gbdt {

namespace {

//-----------------------------------------------------------------------------

constexpr uint32_t LEAF     = 0; 
constexpr uint32_t LESS     = 1; 
constexpr uint32_t IN       = 2; 
constexpr uint32_t INVERTED = 3; 

// layout:
//
// <feature+types>: [feature ref|my type|left child type|right child type]
// bits:                      20       4               4                4
//
// LEAF:    [const]
// bits:         32
//
// LESS:    [<feature+types>][const][skip]
// bits                    32     32    32
//
// IN:      [<feature+types>][skip|set size](set size)X[const]
// bits                    32    24        8                64

// Note: We need to use double for set membership checks (IN) due to
// string hashing.

double read_double(const uint32_t *pos) {
    double value;
    memcpy(&value, pos, sizeof(value));
    return value;
}

const float *as_float_ptr(const uint32_t *pos) {
    return reinterpret_cast<const float*>(pos);
}

bool find_in(double value, const uint32_t *set, const uint32_t *end) {
    for (; set < end; set += 2) {
        if (value == read_double(set)) {
            return true;
        }
    }
    return false;
}

double less_only_find_leaf(const double *input, const uint32_t *pos, uint32_t node_type) {
    for (;;) {
        if (input[pos[0] >> 12] < *as_float_ptr(pos + 1)) {
            node_type = (pos[0] & 0xf0) >> 4;
            pos += 3;
        } else {
            node_type = (pos[0] & 0xf);
            pos += 3 + pos[2];
        }
        if (node_type == LEAF) {
            return *as_float_ptr(pos);
        }
    }
}

double general_find_leaf(const double *input, const uint32_t *pos, uint32_t node_type) {
    for (;;) {
        if (node_type == LESS) {
            if (input[pos[0] >> 12] < *as_float_ptr(pos + 1)) {
                node_type = (pos[0] & 0xf0) >> 4;
                pos += 3;
            } else {
                node_type = (pos[0] & 0xf);
                pos += 3 + pos[2];
            }
            if (node_type == LEAF) {
                return *as_float_ptr(pos);
            }
        } else if (node_type == IN) {
            if (find_in(input[pos[0] >> 12], pos + 2,
                        pos + 2 + (2 * (pos[1] & 0xff))))
            {
                node_type = (pos[0] & 0xf0) >> 4;
                pos += 2 + (2 * (pos[1] & 0xff));
            } else {
                node_type = (pos[0] & 0xf);
                pos += (2 + (2 * (pos[1] & 0xff))) + (pos[1] >> 8);
            }
            if (node_type == LEAF) {
                return *as_float_ptr(pos);
            }
        } else {
            if (input[pos[0] >> 12] >= *as_float_ptr(pos + 1)) {
                node_type = (pos[0] & 0xf);
                pos += 3 + pos[2];
            } else {
                node_type = (pos[0] & 0xf0) >> 4;
                pos += 3;
            }
            if (node_type == LEAF) {
                return *as_float_ptr(pos);
            }
        }
    }
}

//-----------------------------------------------------------------------------

void encode_large_const(double value, std::vector<uint32_t> &model_out) {
    uint32_t buf[2];
    static_assert(sizeof(buf) == sizeof(value));
    memcpy(buf, &value, sizeof(value));
    model_out.push_back(buf[0]);
    model_out.push_back(buf[1]);
}

void encode_const(float value, std::vector<uint32_t> &model_out) {
    uint32_t buf;
    static_assert(sizeof(buf) == sizeof(value));
    memcpy(&buf, &value, sizeof(value));
    model_out.push_back(buf);
}

uint32_t encode_node(const nodes::Node &node_in, std::vector<uint32_t> &model_out);

void encode_less(const nodes::Less &less,
                 const nodes::Node &left_child, const nodes::Node &right_child,
                 std::vector<uint32_t> &model_out)
{
    size_t meta_idx = model_out.size();
    auto symbol = nodes::as<nodes::Symbol>(less.lhs());
    assert(symbol);
    model_out.push_back(uint32_t(symbol->id()) << 12);
    assert(less.rhs().is_const_double());
    encode_const(less.rhs().get_const_double_value(), model_out);
    size_t skip_idx = model_out.size();
    model_out.push_back(0); // left child size placeholder
    uint32_t left_type = encode_node(left_child, model_out);
    model_out[skip_idx] = (model_out.size() - (skip_idx + 1));
    uint32_t right_type = encode_node(right_child, model_out);
    model_out[meta_idx] |= ((LESS << 8) | (left_type << 4) | right_type);
}

void encode_in(const nodes::In &in,
               const nodes::Node &left_child, const nodes::Node &right_child,
               std::vector<uint32_t> &model_out)
{
    size_t meta_idx = model_out.size();
    auto symbol = nodes::as<nodes::Symbol>(in.child());
    assert(symbol);
    model_out.push_back(uint32_t(symbol->id()) << 12);
    size_t set_size_idx = model_out.size();
    model_out.push_back(in.num_entries());
    for (size_t i = 0; i < in.num_entries(); ++i) {
        encode_large_const(in.get_entry(i).get_const_double_value(), model_out);
    }
    size_t left_idx = model_out.size();
    uint32_t left_type = encode_node(left_child, model_out);
    model_out[set_size_idx] |= (model_out.size() - left_idx) << 8;
    uint32_t right_type = encode_node(right_child, model_out);
    model_out[meta_idx] |= ((IN << 8) | (left_type << 4) | right_type);
}

void encode_inverted(const nodes::Not &inverted,
                     const nodes::Node &left_child, const nodes::Node &right_child,
                     std::vector<uint32_t> &model_out)
{
    size_t meta_idx = model_out.size();
    auto ge = nodes::as<nodes::GreaterEqual>(inverted.child());
    assert(ge);
    auto symbol = nodes::as<nodes::Symbol>(ge->lhs());
    assert(symbol);
    model_out.push_back(uint32_t(symbol->id()) << 12);
    assert(ge->rhs().is_const_double());
    encode_const(ge->rhs().get_const_double_value(), model_out);
    size_t skip_idx = model_out.size();
    model_out.push_back(0); // left child size placeholder
    uint32_t left_type = encode_node(left_child, model_out);
    model_out[skip_idx] = (model_out.size() - (skip_idx + 1));
    uint32_t right_type = encode_node(right_child, model_out);
    model_out[meta_idx] |= ((INVERTED << 8) | (left_type << 4) | right_type);
}

uint32_t encode_node(const nodes::Node &node_in, std::vector<uint32_t> &model_out) {
    auto if_node = nodes::as<nodes::If>(node_in);
    if (if_node) {
        auto less = nodes::as<nodes::Less>(if_node->cond());
        auto in = nodes::as<nodes::In>(if_node->cond());
        auto inverted = nodes::as<nodes::Not>(if_node->cond());
        if (less) {
            encode_less(*less, if_node->true_expr(), if_node->false_expr(), model_out);
            return LESS;
        } else if (in) {
            encode_in(*in, if_node->true_expr(), if_node->false_expr(), model_out);
            return IN;
        } else {
            assert(inverted);
            encode_inverted(*inverted, if_node->true_expr(), if_node->false_expr(), model_out);
            return INVERTED;
        }
    } else {
        assert(node_in.is_const_double());
        encode_const(node_in.get_const_double_value(), model_out);
        return LEAF;
    }
}

void encode_tree(const nodes::Node &root_in, std::vector<uint32_t> &model_out) {
    size_t size_idx = model_out.size();
    model_out.push_back(0); // tree size placeholder
    encode_node(root_in, model_out);
    model_out[size_idx] = (model_out.size() - (size_idx + 1));
}

//-----------------------------------------------------------------------------

Optimize::Result optimize(const std::vector<const nodes::Node *> &trees,
                          Forest::eval_function eval)
{
    std::vector<uint32_t> model;
    for (const nodes::Node *tree: trees) {
        encode_tree(*tree, model);
    }
    return Optimize::Result(Forest::UP(new VMForest(std::move(model))), eval);
}

//-----------------------------------------------------------------------------

} // namespace vespalib::eval::gbdt::<unnamed>

//-----------------------------------------------------------------------------

Optimize::Result
VMForest::less_only_optimize(const ForestStats &stats,
                             const std::vector<const nodes::Node *> &trees)
{
    if ((stats.total_in_checks > 0) || (stats.total_inverted_checks > 0)) {
        return Optimize::Result();
    }
    return optimize(trees, less_only_eval);
}

double
VMForest::less_only_eval(const Forest *forest, const double *input)
{
    const VMForest &self = *((const VMForest *)forest);
    const uint32_t *pos = &self._model[0];
    const uint32_t *end = pos + self._model.size();
    double sum = 0.0;
    while (pos < end) {
        uint32_t tree_size = *pos++;
        sum += less_only_find_leaf(input, pos, (*pos & 0xf00) >> 8);
        pos += tree_size;
    }
    return sum;
}

Optimize::Result
VMForest::general_optimize(const ForestStats &stats,
                           const std::vector<const nodes::Node *> &trees)
{
    if (stats.max_set_size > 255) {
        return Optimize::Result();
    }
    return optimize(trees, general_eval);
}

double
VMForest::general_eval(const Forest *forest, const double *input)
{
    const VMForest &self = *((const VMForest *)forest);
    const uint32_t *pos = &self._model[0];
    const uint32_t *end = pos + self._model.size();
    double sum = 0.0;
    while (pos < end) {
        uint32_t tree_size = *pos++;
        sum += general_find_leaf(input, pos, (*pos & 0xf00) >> 8);
        pos += tree_size;
    }
    return sum;
}

Optimize::Chain VMForest::optimize_chain({less_only_optimize, general_optimize});

//-----------------------------------------------------------------------------

} // namespace vespalib::eval::gbdt
} // namespace vespalib::eval
} // namespace vespalib

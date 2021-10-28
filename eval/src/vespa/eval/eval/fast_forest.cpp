// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_forest.h"
#include "gbdt.h"
#include <vespa/eval/eval/basic_nodes.h>
#include <vespa/eval/eval/call_nodes.h>
#include <vespa/eval/eval/operator_nodes.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <algorithm>
#include <cassert>
#include <arpa/inet.h>

namespace vespalib::eval::gbdt {

namespace {

//-----------------------------------------------------------------------------
// internal concepts used during model creation
//-----------------------------------------------------------------------------

constexpr size_t bits_per_byte = 8;

bool is_little_endian() {
    uint32_t value = 0;
    uint8_t bytes[4] = {0, 1, 2, 3};
    static_assert(sizeof(bytes) == sizeof(value));
    memcpy(&value, bytes, sizeof(bytes));
    return (value == 0x03020100);
}

struct BitRange {
    uint32_t first;
    uint32_t last;
    BitRange(uint32_t bit) : first(bit), last(bit) {}
    BitRange(uint32_t a, uint32_t b) : first(a), last(b) {}
    template <typename T>
    size_t covered_words() const {
        assert(first <= last);
        uint32_t v1 = (first / (bits_per_byte * sizeof(T)));
        uint32_t v2 = (last / (bits_per_byte * sizeof(T)));
        return ((v2 - v1) + 1);
    }
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
    CmpNode(float v, uint32_t t, BitRange m, bool f_def) noexcept
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
    size_t max_leafs;
    BitRange encode_node(uint32_t tree_id, const nodes::Node &node);
    State(size_t num_params, const std::vector<const nodes::Node *> &trees);
    size_t num_params() const { return cmp_nodes.size(); }
    size_t num_trees() const { return leafs.size(); }
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
            assert(less->rhs().is_const_double());
            size_t feature = symbol->id();
            assert(feature < cmp_nodes.size());
            cmp_nodes[feature].emplace_back(less->rhs().get_const_double_value(), tree_id, true_leafs, true);
        } else {
            assert(inverted);
            auto ge = nodes::as<nodes::GreaterEqual>(inverted->child());
            assert(ge);
            auto symbol = nodes::as<nodes::Symbol>(ge->lhs());
            assert(symbol);
            assert(ge->rhs().is_const_double());
            size_t feature = symbol->id();
            assert(feature < cmp_nodes.size());
            cmp_nodes[feature].emplace_back(ge->rhs().get_const_double_value(), tree_id, true_leafs, false);
        }
        return BitRange::join(true_leafs, false_leafs);
    } else {
        assert(node.is_const_double());
        BitRange leaf_range(leafs[tree_id].size());
        leafs[tree_id].push_back(node.get_const_double_value());
        return leaf_range;
    }
}

State::State(size_t num_params, const std::vector<const nodes::Node *> &trees)
    : cmp_nodes(num_params),
      leafs(trees.size()),
      max_leafs(0)
{
    for (uint32_t tree_id = 0; tree_id < trees.size(); ++tree_id) {
        BitRange leaf_range = encode_node(tree_id, *trees[tree_id]);
        assert(leaf_range.first == 0);
        assert((leaf_range.last + 1) == leafs[tree_id].size());
        max_leafs = std::max(max_leafs, leafs[tree_id].size());
    }
    for (CmpNodes &cmp_range: cmp_nodes) {
        assert(!cmp_range.empty());
        std::sort(cmp_range.begin(), cmp_range.end());
    }
}

template <typename T> size_t get_lsb(T value) { return vespalib::Optimized::lsbIdx(value); }
template <> size_t get_lsb<uint8_t>(uint8_t value) { return vespalib::Optimized::lsbIdx(uint32_t(value)); }
template <> size_t get_lsb<uint16_t>(uint16_t value) { return vespalib::Optimized::lsbIdx(uint32_t(value)); }

//-----------------------------------------------------------------------------
// implementation using single value mask per tree
//-----------------------------------------------------------------------------

template <typename T> vespalib::string fixed_impl_name();
template <> vespalib::string fixed_impl_name<uint8_t>() { return "ff-fixed<8>"; }
template <> vespalib::string fixed_impl_name<uint16_t>() { return "ff-fixed<16>"; }
template <> vespalib::string fixed_impl_name<uint32_t>() { return "ff-fixed<32>"; }
template <> vespalib::string fixed_impl_name<uint64_t>() { return "ff-fixed<64>"; }

template <typename T>
constexpr size_t max_leafs() { return (sizeof(T) * bits_per_byte); }

template <typename T>
struct FixedContext : FastForest::Context {
    std::vector<T> masks;
    FixedContext(size_t num_trees) : masks(num_trees) {}
};

template <typename T>
struct FixedForest : FastForest {

    static T make_mask(const CmpNode &cmp_node) {
        BitRange range = cmp_node.false_mask;
        size_t num_bits = (sizeof(T) * bits_per_byte);
        assert(range.last < num_bits);
        assert(range.first <= range.last);
        T mask = 0;
        for (uint32_t i = 0; i < num_bits; ++i) {
            if ((i < range.first) || (i > range.last)) {
                mask |= (T(1) << i);
            }
        }
        return mask;
    }

    struct Mask {
        float value;
        uint32_t tree;
        T bits;
        Mask(float v, uint32_t t, T b) noexcept
            : value(v), tree(t), bits(b) {}
    };

    struct DMask {
        uint32_t tree;
        T bits;
        DMask(uint32_t t, T b) noexcept
            : tree(t), bits(b) {}
    };

    std::vector<uint32_t> _mask_sizes;
    std::vector<Mask>     _masks;
    std::vector<uint32_t> _default_offsets;
    std::vector<DMask>    _default_masks;
    std::vector<float>    _padded_leafs;
    uint32_t              _num_trees;
    uint32_t              _max_leafs;

    FixedForest(const State &state);
    static FastForest::UP try_build(const State &state, size_t min_fixed, size_t max_fixed);

    void init_state(T *ctx_masks) const;
    static void apply_masks(T *ctx_masks, const Mask *pos, const Mask *end, float limit);
    static void apply_masks(T *ctx_masks, const DMask *pos, const DMask *end);
    double get_result(const T *ctx_masks) const;

    vespalib::string impl_name() const override { return fixed_impl_name<T>(); }
    Context::UP create_context() const override;
    double eval(Context &context, const float *params) const override;
};

template <typename T>
FixedForest<T>::FixedForest(const State &state)
    : _mask_sizes(),
      _masks(),
      _default_offsets(),
      _default_masks(),
      _padded_leafs(),
      _num_trees(state.num_trees()),
      _max_leafs(state.max_leafs)
{
    for (const auto &cmp_nodes: state.cmp_nodes) {
        _mask_sizes.emplace_back(cmp_nodes.size());
        _default_offsets.push_back(_default_masks.size());
        for (const CmpNode &cmp_node: cmp_nodes) {
            _masks.emplace_back(cmp_node.value, cmp_node.tree_id, make_mask(cmp_node));
            if (cmp_node.false_is_default) {
                _default_masks.emplace_back(cmp_node.tree_id, make_mask(cmp_node));
            }
        }
    }
    _default_offsets.push_back(_default_masks.size());
    for (const auto &leafs: state.leafs) {
        for (float leaf: leafs) {
            _padded_leafs.push_back(leaf);
        }
        size_t padding = (_max_leafs - leafs.size());
        while (padding-- > 0) {
            _padded_leafs.push_back(0.0);
        }
    }
    assert(_padded_leafs.size() == (_num_trees * _max_leafs));
}

template <typename T>
FastForest::UP
FixedForest<T>::try_build(const State &state, size_t min_fixed, size_t max_fixed)
{
    if ((max_leafs<T>() >= min_fixed) &&
        (max_leafs<T>() <= max_fixed) &&
        (state.max_leafs <= max_leafs<T>()))
    {
        return std::make_unique<FixedForest<T>>(state);
    }
    return FastForest::UP();
}

template <typename T>
void
FixedForest<T>::init_state(T *ctx_masks) const
{
    memset(ctx_masks, 0xff, _num_trees * sizeof(T));
}

template <typename T>
void
FixedForest<T>::apply_masks(T *ctx_masks, const Mask *pos, const Mask *end, float limit)
{
    for (; ((pos+3) < end) && !(limit < pos[3].value); pos += 4) {
        ctx_masks[pos[0].tree] &= pos[0].bits;
        ctx_masks[pos[1].tree] &= pos[1].bits;
        ctx_masks[pos[2].tree] &= pos[2].bits;
        ctx_masks[pos[3].tree] &= pos[3].bits;
    }
    for (; (pos < end) && !(limit < pos->value); ++pos) {
        ctx_masks[pos->tree] &= pos->bits;
    }
}

template <typename T>
void
FixedForest<T>::apply_masks(T *ctx_masks, const DMask *pos, const DMask *end)
{
    for (; ((pos+3) < end); pos += 4) {
        ctx_masks[pos[0].tree] &= pos[0].bits;
        ctx_masks[pos[1].tree] &= pos[1].bits;
        ctx_masks[pos[2].tree] &= pos[2].bits;
        ctx_masks[pos[3].tree] &= pos[3].bits;
    }
    for (; (pos < end); ++pos) {
        ctx_masks[pos->tree] &= pos->bits;
    }
}

template <typename T>
double
FixedForest<T>::get_result(const T *ctx_masks) const
{
    double result1 = 0.0;
    double result2 = 0.0;
    const T *ctx_end = (ctx_masks + _num_trees);
    const float *leafs = &_padded_leafs[0];
    size_t leaf_cnt = _max_leafs;
    for (; (ctx_masks + 3) < ctx_end; ctx_masks += 4, leafs += (leaf_cnt * 4)) {
        result1 += leafs[(0 * leaf_cnt) + get_lsb(ctx_masks[0])];
        result2 += leafs[(1 * leaf_cnt) + get_lsb(ctx_masks[1])];
        result1 += leafs[(2 * leaf_cnt) + get_lsb(ctx_masks[2])];
        result2 += leafs[(3 * leaf_cnt) + get_lsb(ctx_masks[3])];
    }
    for (; ctx_masks < ctx_end; ++ctx_masks, leafs += leaf_cnt) {
        result1 += leafs[get_lsb(*ctx_masks)];
    }
    return (result1 + result2);
}

template <typename T>
FastForest::Context::UP
FixedForest<T>::create_context() const
{
    return std::make_unique<FixedContext<T>>(_num_trees);
}

template <typename T>
double
FixedForest<T>::eval(Context &context, const float *params) const
{
    T *ctx_masks = &static_cast<FixedContext<T>&>(context).masks[0];
    init_state(ctx_masks);
    const Mask *mask_pos = &_masks[0];
    const float *param_pos = params;
    for (uint32_t size: _mask_sizes) {
        float feature = *param_pos++;
        if (!std::isnan(feature)) {
            apply_masks(ctx_masks, mask_pos, mask_pos + size, feature);
        } else {
            apply_masks(ctx_masks,
                        &_default_masks[_default_offsets[(param_pos-params)-1]],
                        &_default_masks[_default_offsets[(param_pos-params)]]);
        }
        mask_pos += size;
    }
    return get_result(ctx_masks);
}

//-----------------------------------------------------------------------------
// implementation using multiple words for each tree
//-----------------------------------------------------------------------------

struct MultiWordContext : FastForest::Context {
    std::vector<uint32_t> words;
    MultiWordContext(size_t size) : words(size) {}
};

struct MultiWordForest : FastForest {

    constexpr static size_t word_size = sizeof(uint32_t);
    constexpr static size_t bits_per_word = (word_size * bits_per_byte);

    struct Sizes {
        uint32_t fixed;
        uint32_t rle;
        Sizes(uint32_t f, uint32_t r) noexcept : fixed(f), rle(r) {}
    };

    struct Mask {
        float value;
        uint32_t offset;
        union {
            uint32_t bits;
            uint8_t rle_mask[3];
        };
        Mask(float v, uint32_t word_offset, uint32_t mask_bits) noexcept
            : value(v), offset(word_offset), bits(mask_bits) {}
        Mask(float v, uint32_t byte_offset, uint8_t first_bits, uint8_t empty_bytes, uint8_t last_bits) noexcept
            : value(v), offset(byte_offset), rle_mask{first_bits, empty_bytes, last_bits} {}
    };

    struct DMask {
        uint32_t offset;
        union {
            uint32_t bits;
            uint8_t rle_mask[3];
        };
        DMask(uint32_t word_offset, uint32_t mask_bits) noexcept
            : offset(word_offset), bits(mask_bits) {}
        DMask(uint32_t byte_offset, uint8_t first_bits, uint8_t empty_bytes, uint8_t last_bits) noexcept
            : offset(byte_offset), rle_mask{first_bits, empty_bytes, last_bits} {}
    };

    static Mask make_fixed_mask(const CmpNode &cmp_node, size_t words_per_tree) {
        BitRange range = cmp_node.false_mask;
        assert(range.covered_words<uint32_t>() == 1);
        size_t offset = (range.first / bits_per_word);
        uint32_t bits = 0;
        for (uint32_t i = 0; i < bits_per_word; ++i) {
            uint32_t bit = (offset * bits_per_word) + i;
            if ((bit < range.first) || (bit > range.last)) {
                bits |= (uint32_t(1) << i);
            }
        }
        offset += (words_per_tree * cmp_node.tree_id);
        return Mask(cmp_node.value, offset, bits);
    }

    static Mask make_rle_mask(const CmpNode &cmp_node, size_t words_per_tree) {
        BitRange range = cmp_node.false_mask;
        assert(range.covered_words<uint32_t>() > 1);
        uint32_t idx1 = (range.first / bits_per_byte);
        uint32_t idx2 = (range.last / bits_per_byte);
        uint8_t bits1 = 0;
        uint8_t bits2 = 0;
        for (uint32_t i = 0; i < bits_per_byte; ++i) {
            uint32_t bit1 = (idx1 * bits_per_byte) + i;
            if ((bit1 < range.first) || (bit1 > range.last)) {
                bits1 |= (uint8_t(1) << i);
            }
            uint32_t bit2 = (idx2 * bits_per_byte) + i;
            if ((bit2 < range.first) || (bit2 > range.last)) {
                bits2 |= (uint8_t(1) << i);
            }
        }
        uint32_t offset = (idx1 + (word_size * words_per_tree * cmp_node.tree_id));
        uint32_t empty_cnt = ((idx2 - idx1) - 1);
        assert(empty_cnt < 256);
        return Mask(cmp_node.value, offset, bits1, empty_cnt, bits2);
    }

    std::vector<Sizes>    _mask_sizes;
    std::vector<Mask>     _masks;
    std::vector<Sizes>    _default_offsets;
    std::vector<DMask>    _default_masks;
    std::vector<uint32_t> _tree_offsets;
    std::vector<float>    _leafs;
    uint32_t              _words_per_tree;

    MultiWordForest(const State &state);
    static FastForest::UP try_build(const State &state);

    void init_state(uint32_t *ctx_words) const;
    static void apply_fixed_masks(uint32_t *ctx_words, const Mask *pos, const Mask *end, float limit);
    static void apply_rle_masks(unsigned char *ctx_bytes, const Mask *pos, const Mask *end, float limit);
    static void apply_fixed_masks(uint32_t *ctx_words, const DMask *pos, const DMask *end);
    static void apply_rle_masks(unsigned char *ctx_bytes, const DMask *pos, const DMask *end);
    static size_t find_leaf(const uint32_t *ctx_words);
    double get_result(const uint32_t *ctx_words) const;

    vespalib::string impl_name() const override { return "ff-multiword"; }
    Context::UP create_context() const override;
    double eval(Context &context, const float *params) const override;
};

MultiWordForest::MultiWordForest(const State &state)
    : _mask_sizes(),
      _masks(),
      _default_offsets(),
      _default_masks(),
      _tree_offsets(),
      _leafs(),
      _words_per_tree(BitRange(0, state.max_leafs - 1).covered_words<uint32_t>())
{
    for (const auto &cmp_nodes: state.cmp_nodes) {
        std::vector<CmpNode> fixed;
        std::vector<CmpNode> rle;
        size_t default_fixed_cnt = 0;
        for (const CmpNode &cmp_node: cmp_nodes) {
            if (cmp_node.false_mask.covered_words<uint32_t>() == 1) {
                fixed.push_back(cmp_node);
                if (cmp_node.false_is_default) {
                    ++default_fixed_cnt;
                }
            } else {
                rle.push_back(cmp_node);
            }
        }
        _mask_sizes.emplace_back(fixed.size(), rle.size());
        _default_offsets.emplace_back(_default_masks.size(),
                                      _default_masks.size() + default_fixed_cnt);
        for (const CmpNode &cmp_node: fixed) {
            _masks.push_back(make_fixed_mask(cmp_node, _words_per_tree));
            if (cmp_node.false_is_default) {
                _default_masks.emplace_back(_masks.back().offset,
                                            _masks.back().bits);
            }
        }
        assert(_default_masks.size() == _default_offsets.back().rle);
        for (const CmpNode &cmp_node: rle) {
            _masks.push_back(make_rle_mask(cmp_node, _words_per_tree));
            if (cmp_node.false_is_default) {
                _default_masks.emplace_back(_masks.back().offset,
                                            _masks.back().rle_mask[0],
                                            _masks.back().rle_mask[1],
                                            _masks.back().rle_mask[2]);
            }
        }
    }
    _default_offsets.emplace_back(_default_masks.size(), _default_masks.size());
    for (const auto &leafs: state.leafs) {
        _tree_offsets.push_back(_leafs.size());
        for (float leaf: leafs) {
            _leafs.push_back(leaf);
        }
    }
}

FastForest::UP
MultiWordForest::try_build(const State &state)
{
    if (is_little_endian()) {
        if (state.max_leafs <= (bits_per_byte * 256)) {
            return std::make_unique<MultiWordForest>(state);
        }
    }
    return FastForest::UP();
}

void
MultiWordForest::init_state(uint32_t *ctx_words) const
{
    memset(ctx_words, 0xff, word_size * _words_per_tree * _tree_offsets.size());
}

void
MultiWordForest::apply_fixed_masks(uint32_t *ctx_words, const Mask *pos, const Mask *end, float limit)
{
    for (; ((pos+3) < end) && !(limit < pos[3].value); pos += 4) {
        ctx_words[pos[0].offset] &= pos[0].bits;
        ctx_words[pos[1].offset] &= pos[1].bits;
        ctx_words[pos[2].offset] &= pos[2].bits;
        ctx_words[pos[3].offset] &= pos[3].bits;
    }
    for (; (pos < end) && !(limit < pos->value); ++pos) {
        ctx_words[pos->offset] &= pos->bits;
    }
}

void
MultiWordForest::apply_rle_masks(unsigned char *ctx_bytes, const Mask *pos, const Mask *end, float limit)
{
    for (; (pos < end) && !(limit < pos->value); ++pos) {
        unsigned char *dst = (ctx_bytes + pos->offset);
        *dst++ &= pos->rle_mask[0];
        for (size_t e = pos->rle_mask[1]; e-- > 0; ) {
            *dst++ = 0;
        }
        *dst &= pos->rle_mask[2];
    }
}

void
MultiWordForest::apply_fixed_masks(uint32_t *ctx_words, const DMask *pos, const DMask *end)
{
    for (; ((pos+3) < end); pos += 4) {
        ctx_words[pos[0].offset] &= pos[0].bits;
        ctx_words[pos[1].offset] &= pos[1].bits;
        ctx_words[pos[2].offset] &= pos[2].bits;
        ctx_words[pos[3].offset] &= pos[3].bits;
    }
    for (; (pos < end); ++pos) {
        ctx_words[pos->offset] &= pos->bits;
    }
}

void
MultiWordForest::apply_rle_masks(unsigned char *ctx_bytes, const DMask *pos, const DMask *end)
{
    for (; pos < end; ++pos) {
        unsigned char *dst = (ctx_bytes + pos->offset);
        *dst++ &= pos->rle_mask[0];
        for (size_t e = pos->rle_mask[1]; e-- > 0; ) {
            *dst++ = 0;
        }
        *dst &= pos->rle_mask[2];
    }
}

size_t
MultiWordForest::find_leaf(const uint32_t *word)
{
    size_t idx = 0;
    for (; *word == 0; ++word) {
        idx += bits_per_word;
    }
    return (idx + get_lsb(*word));
}

double
MultiWordForest::get_result(const uint32_t *ctx_words) const
{
    double result = 0.0;
    const float *leafs = &_leafs[0];
    for (size_t tree_offset: _tree_offsets) {
        result += leafs[tree_offset + find_leaf(ctx_words)];
        ctx_words += _words_per_tree;
    }
    return result;
}

FastForest::Context::UP
MultiWordForest::create_context() const
{
    return std::make_unique<MultiWordContext>(_words_per_tree * _tree_offsets.size());
}

double
MultiWordForest::eval(Context &context, const float *params) const
{
    uint32_t *ctx_words = &static_cast<MultiWordContext&>(context).words[0];
    init_state(ctx_words);
    const Mask *mask_pos = &_masks[0];
    const float *param_pos = params;
    for (const Sizes &size: _mask_sizes) {
        float feature = *param_pos++;
        if (!std::isnan(feature)) {
            apply_fixed_masks(ctx_words, mask_pos, mask_pos + size.fixed, feature);
            apply_rle_masks(reinterpret_cast<unsigned char *>(ctx_words),
                            mask_pos + size.fixed, mask_pos + size.fixed + size.rle, feature);
        } else {
            apply_fixed_masks(ctx_words,
                              &_default_masks[_default_offsets[(param_pos-params)-1].fixed],
                              &_default_masks[_default_offsets[(param_pos-params)-1].rle]);
            apply_rle_masks(reinterpret_cast<unsigned char *>(ctx_words),
                            &_default_masks[_default_offsets[(param_pos-params)-1].rle],
                            &_default_masks[_default_offsets[(param_pos-params)].fixed]);
        }
        mask_pos += (size.fixed + size.rle);
    }
    return get_result(ctx_words);
}

}

//-----------------------------------------------------------------------------
// outer shell unifying the different implementations
//-----------------------------------------------------------------------------

FastForest::Context::Context() = default;
FastForest::Context::~Context() = default;

FastForest::FastForest() = default;
FastForest::~FastForest() = default;

FastForest::UP
FastForest::try_convert(const Function &fun, size_t min_fixed, size_t max_fixed)
{
    const auto &root = fun.root();
    if (root.is_forest()) {
        auto trees = gbdt::extract_trees(root);
        gbdt::ForestStats stats(trees);
        if (stats.total_in_checks == 0) {
            State state(fun.num_params(), trees);
            if (auto forest = FixedForest<uint8_t>::try_build(state, min_fixed, max_fixed)) {
                return forest;
            }
            if (auto forest = FixedForest<uint16_t>::try_build(state, min_fixed, max_fixed)) {
                return forest;
            }
            if (auto forest = FixedForest<uint32_t>::try_build(state, min_fixed, max_fixed)) {
                return forest;
            }
            if (auto forest = FixedForest<uint64_t>::try_build(state, min_fixed, max_fixed)) {
                return forest;
            }
            if (auto forest = MultiWordForest::try_build(state)) {
                return forest;
            }
        }
    }
    return FastForest::UP();
}

double
FastForest::estimate_cost_us(const std::vector<double> &params, double budget) const
{
    auto ctx = create_context();
    std::vector<float> my_params(params.begin(), params.end());
    return BenchmarkTimer::benchmark([&](){ eval(*ctx, &my_params[0]); }, budget) * 1000.0 * 1000.0;
}

}

// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "function.h"
#include <vespa/vespalib/util/optimized.h>
#include <memory>
#include <cassert>
#include <cmath>

namespace vespalib::eval::gbdt {

/**
 * Use modern optimization strategies to improve evaluation
 * performance of GBDT forests.
 *
 * This model evaluation supports up to 65536 trees with up to 2048
 * leaf nodes in each tree. Comparisons must be on the form 'feature <
 * const' or '!(feature >= const)'. The inverted form is used to
 * signal that the true branch should be selected when the feature
 * values is missing (NaN).
 **/
class FastForest
{
public:
    class Context {
        friend class FastForest;
    private:
        const FastForest *_forest;
        size_t _bytes_per_tree;
        std::vector<uint8_t> _bits;
    public:
        explicit Context(const FastForest &ff);
        ~Context();
    };

private:
    friend struct FastForestBuilder;

    enum class MaskType : uint8_t { ONE, TWO, MANY };

    struct Mask {
        uint16_t tree_id;
        MaskType type;
        uint8_t false_is_default;
        uint8_t first_idx;
        uint8_t first_bits;
        uint8_t last_idx;
        uint8_t last_bits;
        Mask(uint16_t tree, MaskType mt, bool def_false,
             uint8_t idx1, uint8_t bits1, uint8_t idx2, uint8_t bits2)
            : tree_id(tree), type(mt), false_is_default(def_false),
              first_idx(idx1), first_bits(bits1), last_idx(idx2), last_bits(bits2) {}
    };

    std::vector<uint32_t>  _feature_sizes;
    std::vector<float>     _values;
    std::vector<Mask>      _masks;
    std::vector<uint32_t>  _tree_sizes;
    std::vector<float>     _leafs;

    FastForest();

    static void apply_mask(uint8_t *bits, size_t bytes_per_tree, const Mask &mask) {
        uint8_t *dst = (bits + (mask.tree_id * bytes_per_tree) + mask.first_idx);
        *dst &= mask.first_bits;
        if (__builtin_expect(mask.type != MaskType::ONE, false)) {
            if (__builtin_expect(mask.type == MaskType::MANY, false)) {
                size_t n = (mask.last_idx - mask.first_idx - 1);
                while (n-- > 0) {
                    *++dst = 0x00;
                }
            }
            *++dst &= mask.last_bits;
        }
    }

    static float find_leaf(const uint8_t *bits, const float *leafs) {
        while (__builtin_expect(*bits == 0, true)) {
            ++bits;
            leafs += 8;
        }
        return leafs[vespalib::Optimized::lsbIdx(uint32_t(*bits))];
    }

public:
    ~FastForest();
    size_t num_params() const;
    size_t num_trees() const;
    size_t max_leafs() const;
    using UP = std::unique_ptr<FastForest>;
    static UP try_convert(const Function &fun);

    template <typename F>
    double eval(Context &ctx, F &&f) const {
        assert(ctx._forest == this);
        size_t bytes_per_tree = ctx._bytes_per_tree;
        uint8_t *bits = &ctx._bits[0];
        const float *value_ptr = &_values[0];
        const Mask *mask_ptr = &_masks[0];
        memset(bits, 0xff, ctx._bits.size());
        for (size_t f_idx = 0; f_idx < _feature_sizes.size(); ++f_idx) {
            size_t feature_size = _feature_sizes[f_idx];
            float feature_value = f(f_idx); // get param
            if (__builtin_expect(std::isnan(feature_value), false)) {
                // handle 'missing' input feature
                for (size_t i = 0; i < feature_size; ++i) {
                    const Mask &mask = mask_ptr[i];
                    if (mask.false_is_default) {
                        apply_mask(bits, bytes_per_tree, mask);
                    }
                }
            } else {
                for (size_t i = 0; i < feature_size; ++i) {
                    if (__builtin_expect(feature_value < value_ptr[i], false)) {
                        break;
                    } else {
                        apply_mask(bits, bytes_per_tree, mask_ptr[i]);
                    }
                }
            }
            value_ptr += feature_size;
            mask_ptr += feature_size;
        }
        assert(value_ptr == &*_values.end());
        assert(mask_ptr == &*_masks.end());
        const float *leafs = &_leafs[0];
        double result = 0.0;
        for (size_t tree_size: _tree_sizes) {
            result += find_leaf(bits, leafs);
            bits += bytes_per_tree;
            leafs += tree_size;
        }
        assert(bits == &*ctx._bits.end());
        assert(leafs == &*_leafs.end());
        return result;
    }
    double estimate_cost_us(const std::vector<double> &params, double budget = 5.0) const;
};

}

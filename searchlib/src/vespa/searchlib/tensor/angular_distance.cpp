// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "angular_distance.h"

#include "quant_helpers.h"
#include "temporary_vector_store.h"

#include <vespa/vespalib/hwaccelerated/functions.h>
#include <vespa/vespalib/quant/eden.h>

#include <cmath>
#include <numbers>

using vespalib::typify_invoke;
using vespalib::eval::CellType;
using vespalib::eval::Int8Float;
using vespalib::eval::TypedCells;
using vespalib::eval::TypifyCellType;
using vespalib::quant::QuantizedVector;
namespace hwaccelerated = vespalib::hwaccelerated;

namespace search::tensor {

class BoundAngularDistanceBase : public BoundDistanceFunction {
public:
    double convert_threshold(double threshold) const noexcept override {
        if (threshold < 0.0) {
            return 0.0;
        }
        if (threshold > std::numbers::pi) {
            return 2.0;
        }
        double cosine_similarity = cos(threshold);
        return 1.0 - cosine_similarity;
    }
    double to_rawscore(double distance) const noexcept override {
        double cosine_similarity = 1.0 - distance;
        // should be in the range [-1,1] but roundoff may cause problems:
        cosine_similarity = std::min(1.0, cosine_similarity);
        cosine_similarity = std::max(-1.0, cosine_similarity);
        double angle_distance = acos(cosine_similarity); // in range [0,pi]
        double score = 1.0 / (1.0 + angle_distance);
        return score;
    }
    double calc_with_limit(TypedCells rhs, double) const noexcept override { return calc(rhs); }
};

template <typename VectorStoreType>
class BoundAngularDistance final : public BoundAngularDistanceBase {
    using FloatType = VectorStoreType::FloatType;
    mutable VectorStoreType          _tmpSpace;
    const std::span<const FloatType> _lhs;
    double                           _lhs_norm_sq;

public:
    explicit BoundAngularDistance(TypedCells lhs) : _tmpSpace(lhs.size), _lhs(_tmpSpace.storeLhs(lhs)) {
        auto a = _lhs.data();
        _lhs_norm_sq = hwaccelerated::dot_product(cast(a), cast(a), lhs.size);
    }
    double calc(TypedCells rhs) const noexcept override {
        size_t                     sz = _lhs.size();
        std::span<const FloatType> rhs_vector = _tmpSpace.convertRhs(rhs);
        auto                       a = _lhs.data();
        auto                       b = rhs_vector.data();
        double                     b_norm_sq = hwaccelerated::dot_product(cast(b), cast(b), sz);
        double                     squared_norms = _lhs_norm_sq * b_norm_sq;
        double                     dot_product = hwaccelerated::dot_product(cast(a), cast(b), sz);
        double                     div = (squared_norms > 0) ? sqrt(squared_norms) : 1.0;
        double                     cosine_similarity = dot_product / div;
        double                     distance = 1.0 - cosine_similarity; // in range [0,2]
        return distance;
    }
};

template class BoundAngularDistance<TemporaryVectorStore<float>>;
template class BoundAngularDistance<TemporaryVectorStore<double>>;
template class BoundAngularDistance<TemporaryVectorStore<Int8Float>>;
template class BoundAngularDistance<TemporaryVectorStore<vespalib::BFloat16>>;
template class BoundAngularDistance<ReferenceVectorStore<float>>;
template class BoundAngularDistance<ReferenceVectorStore<double>>;
template class BoundAngularDistance<ReferenceVectorStore<Int8Float>>;
template class BoundAngularDistance<ReferenceVectorStore<vespalib::BFloat16>>;

template <typename FloatType>
BoundDistanceFunction::UP AngularDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) const {
    using DFT = BoundAngularDistance<TemporaryVectorStore<FloatType>>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP AngularDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) const {
    if (_reference_insertion_vector) {
        using DFT = BoundAngularDistance<ReferenceVectorStore<FloatType>>;
        return std::make_unique<DFT>(lhs);
    } else {
        using DFT = BoundAngularDistance<TemporaryVectorStore<FloatType>>;
        return std::make_unique<DFT>(lhs);
    }
}

namespace {

struct Float32LhsQuantizedRhs {
    using VectorStoreType = MutableSingleTemporaryVectorStore<float>;
    using LhsFloatType = float;
    constexpr static bool pre_quantized_lhs = false;
};

struct QuantizedLhsAndRhs {
    using VectorStoreType = ReferenceVectorStore<Int8Float>;
    using LhsFloatType = Int8Float;
    constexpr static bool pre_quantized_lhs = true;
};

} // namespace

// TODO generalize for other quantized bound distance functions?
template <typename QuantParams>
class BoundQuantizedAngularDistance final : public BoundAngularDistanceBase {
    using LhsVectorStoreType = typename QuantParams::VectorStoreType;
    using LhsFloatType = typename QuantParams::LhsFloatType;
    // A distance function instance shall only be used by one thread at a time,
    // so this can be mutable without violating constness assumptions.
    // FIXME this one is relatively expensive to create and keep around...!
    //  Make as much as possible into free-standing functions and only create quantizer
    //  when pre-rotating.
    mutable vespalib::quant::EdenQuantizer _quantizer;
    LhsVectorStoreType                     _tmp_space;
    const std::span<const LhsFloatType>    _lhs;
    double                                 _lhs_norm_sq;

public:
    BoundQuantizedAngularDistance(TypedCells lhs, size_t dimensions, uint8_t bits, uint64_t seed)
        : _quantizer(dimensions, bits, seed), _tmp_space(lhs.size), _lhs(_tmp_space.storeLhs(lhs)) {
        if constexpr (!QuantParams::pre_quantized_lhs) { // full precision format
            assert(lhs.size == _quantizer.dimensions());
            // Quantized vectors are all in rotated space, so pre-rotate the query vector once
            // so that it is in the same frame of reference. This avoids having to rotate the
            // _quantized_ vectors when performing dot products. Note: this aliases `_lhs`.
            _quantizer.rotate_vector_inplace(_tmp_space.mutable_lhs_buf());
        } else {
            static_assert(std::is_same_v<LhsFloatType, Int8Float>);
            assert(lhs.size == _quantizer.quantized_size());
        }
        auto a = adapt_lhs();
        _lhs_norm_sq = dot(a, a); // TODO dedicated L2 norm function
    }
    ~BoundQuantizedAngularDistance() override;

    auto adapt_lhs() const noexcept {
        if constexpr (QuantParams::pre_quantized_lhs) {
            return as_quantized(_lhs);
        } else {
            return _lhs.data();
        }
    }

    // rhs is always a quantized vector representation
    double calc(TypedCells rhs) const noexcept override {
        assert(rhs.size == _quantizer.quantized_size());
        auto   a = adapt_lhs(); // f32 (raw query vector) or i8 (quantized insertion vector)
        auto   b = as_quantized(rhs);
        double b_norm_sq = dot(b, b); // TODO dedicated L2 norm function (avoids 2x codebook lookups)
        double squared_norms = _lhs_norm_sq * b_norm_sq;
        double dot_product = dot(a, b);
        double div = (squared_norms > 0) ? sqrt(squared_norms) : 1.0;
        double cosine_similarity = dot_product / div;
        double distance = 1.0 - cosine_similarity; // in range [0,2]
        return distance;
    }

private:
    [[nodiscard]] float dot(QuantizedVector lhs, QuantizedVector rhs) const noexcept {
        return _quantizer.quantized_lhs_rhs_dot_product(lhs, rhs);
    }

    [[nodiscard]] float dot(const float* lhs, QuantizedVector rhs) const noexcept {
        auto lhs_f32 = std::span<const float>(lhs, _quantizer.dimensions());
        return _quantizer.pre_rotated_query_dot_product(lhs_f32, rhs);
    }

    [[nodiscard]] float dot(const float* lhs, const float* rhs) const noexcept {
        return hwaccelerated::dot_product(lhs, rhs, _quantizer.dimensions());
    }
};

template <typename LhsVectorStoreType>
BoundQuantizedAngularDistance<LhsVectorStoreType>::~BoundQuantizedAngularDistance() = default;

BoundDistanceFunction::UP QuantizedAngularDistanceFunctionFactory::for_query_vector(TypedCells lhs) const {
    using DFT = BoundQuantizedAngularDistance<Float32LhsQuantizedRhs>;
    return std::make_unique<DFT>(lhs, _dimensions, _bits, _seed);
}

BoundDistanceFunction::UP QuantizedAngularDistanceFunctionFactory::for_insertion_vector(TypedCells lhs) const {
    // Insertion vectors are always in quantized int8 form
    using DFT = BoundQuantizedAngularDistance<QuantizedLhsAndRhs>;
    assert(lhs.type == CellType::INT8);
    return std::make_unique<DFT>(lhs, _dimensions, _bits, _seed);
}

template class AngularDistanceFunctionFactory<float>;
template class AngularDistanceFunctionFactory<double>;
template class AngularDistanceFunctionFactory<Int8Float>;
template class AngularDistanceFunctionFactory<vespalib::BFloat16>;

template class BoundQuantizedAngularDistance<Float32LhsQuantizedRhs>;
template class BoundQuantizedAngularDistance<QuantizedLhsAndRhs>;

} // namespace search::tensor

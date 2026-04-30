// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "turbo_quant_distance.h"
#include "temporary_vector_store.h"
#include <vespa/vespalib/hwaccelerated/functions.h>
#include <algorithm>
#include <cmath>
#include <limits>
#include <numbers>
#include <string>

using vespalib::eval::Int8Float;
namespace hwaccelerated = vespalib::hwaccelerated;

namespace search::tensor {

namespace {

const std::string hnsw_turbo_quant_version = "hnsw.turbo_quant.version";
const std::string hnsw_turbo_quant_levels = "hnsw.turbo_quant.levels";
constexpr uint32_t turbo_quant_state_version = 1;

constexpr double k_qjl_scale = 1.2533141373155002512; // sqrt(pi/2)
constexpr double k_eps = 1e-12;

inline int
residual_sign(double value) noexcept
{
    if (value > 0.0) {
        return 1;
    }
    if (value < 0.0) {
        return -1;
    }
    return 0;
}

inline double
dequantize_normalized(double value, uint32_t levels) noexcept
{
    if (levels <= 4) {
        // 2-bit base quantization centroids (TurboQuant-like low-bit stage).
        if (value < -0.9815) return -1.510;
        if (value < 0.0) return -0.453;
        if (value < 0.9815) return 0.453;
        return 1.510;
    }
    // 3-bit base quantization fallback using simple uniform bins.
    if (value < -2.0) return -2.5;
    if (value < -1.25) return -1.5;
    if (value < -0.75) return -1.0;
    if (value < -0.25) return -0.5;
    if (value < 0.25) return 0.5;
    if (value < 0.75) return 1.0;
    if (value < 1.25) return 1.5;
    return 2.5;
}

template <typename FloatType>
double
vector_scale(std::span<const FloatType> vector) noexcept
{
    if (vector.empty()) {
        return 1.0;
    }
    double norm_sq = 0.0;
    for (auto value : vector) {
        double v = value;
        norm_sq += (v * v);
    }
    if (norm_sq <= k_eps) {
        return 1.0;
    }
    return std::sqrt(norm_sq / static_cast<double>(vector.size()));
}

} // namespace

template <typename VectorStoreType>
class BoundTurboQuantDistance final : public BoundDistanceFunction {
    using FloatType = typename VectorStoreType::FloatType;
    mutable VectorStoreType _tmp_space;
    std::span<const FloatType> _lhs;
    MaximumSquaredNormStore* _sq_norm_store;
    bool _update_norm_store;
    uint32_t _mse_levels;

public:
    BoundTurboQuantDistance(TypedCells lhs, MaximumSquaredNormStore& sq_norm_store, bool update_norm_store, uint32_t mse_levels)
        : _tmp_space(lhs.size),
          _lhs(_tmp_space.storeLhs(lhs)),
          _sq_norm_store(&sq_norm_store),
          _update_norm_store(update_norm_store),
          _mse_levels(mse_levels)
    {
        const auto* data = _lhs.data();
        double lhs_sq_norm = hwaccelerated::dot_product(cast(data), cast(data), _lhs.size());
        if (_update_norm_store) {
            (void) _sq_norm_store->get_max(lhs_sq_norm);
        } else {
            (void) _sq_norm_store->get_max();
        }
    }

    double calc(TypedCells rhs) const noexcept override {
        auto rhs_vec = _tmp_space.convertRhs(rhs);
        const size_t sz = _lhs.size();
        if (sz == 0u) {
            return 0.0;
        }

        const double lhs_scale = vector_scale(_lhs);
        const double rhs_scale = vector_scale(rhs_vec);
        const double lhs_inv_scale = 1.0 / std::max(lhs_scale, k_eps);
        const double rhs_inv_scale = 1.0 / std::max(rhs_scale, k_eps);

        double quantized_dot = 0.0;
        double lhs_residual_norm_sq = 0.0;
        double rhs_residual_norm_sq = 0.0;
        int residual_sign_dot = 0;

        for (size_t i = 0; i < sz; ++i) {
            const double lhs_val = _lhs[i];
            const double rhs_val = rhs_vec[i];

            const double lhs_q = dequantize_normalized(lhs_val * lhs_inv_scale, _mse_levels) * lhs_scale;
            const double rhs_q = dequantize_normalized(rhs_val * rhs_inv_scale, _mse_levels) * rhs_scale;

            quantized_dot += (lhs_q * rhs_q);

            const double lhs_residual = lhs_val - lhs_q;
            const double rhs_residual = rhs_val - rhs_q;
            lhs_residual_norm_sq += lhs_residual * lhs_residual;
            rhs_residual_norm_sq += rhs_residual * rhs_residual;
            residual_sign_dot += residual_sign(lhs_residual) * residual_sign(rhs_residual);
        }

        double correction = 0.0;
        if ((lhs_residual_norm_sq > k_eps) && (rhs_residual_norm_sq > k_eps)) {
            const double residual_product_norm = std::sqrt(lhs_residual_norm_sq * rhs_residual_norm_sq);
            correction = (k_qjl_scale / static_cast<double>(sz)) * residual_product_norm * static_cast<double>(residual_sign_dot);
        }
        const double estimated_inner_product = quantized_dot + correction;
        return -estimated_inner_product;
    }

    double convert_threshold(double threshold) const noexcept override {
        return threshold;
    }

    double to_rawscore(double distance) const noexcept override {
        return -distance;
    }

    double to_distance(double rawscore) const noexcept override {
        return -rawscore;
    }

    double min_rawscore() const noexcept override {
        return std::numeric_limits<double>::lowest();
    }

    double calc_with_limit(TypedCells rhs, double) const noexcept override {
        return calc(rhs);
    }
};

template class BoundTurboQuantDistance<TemporaryVectorStore<Int8Float>>;
template class BoundTurboQuantDistance<TemporaryVectorStore<vespalib::BFloat16>>;
template class BoundTurboQuantDistance<TemporaryVectorStore<float>>;
template class BoundTurboQuantDistance<TemporaryVectorStore<double>>;
template class BoundTurboQuantDistance<ReferenceVectorStore<Int8Float>>;
template class BoundTurboQuantDistance<ReferenceVectorStore<vespalib::BFloat16>>;
template class BoundTurboQuantDistance<ReferenceVectorStore<float>>;
template class BoundTurboQuantDistance<ReferenceVectorStore<double>>;

template <typename FloatType>
BoundDistanceFunction::UP
TurboQuantDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) const {
    using DFT = BoundTurboQuantDistance<TemporaryVectorStore<FloatType>>;
    return std::make_unique<DFT>(lhs, *_sq_norm_store, false, _mse_levels);
}

template <typename FloatType>
BoundDistanceFunction::UP
TurboQuantDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) const {
    if (_reference_insertion_vector) {
        using DFT = BoundTurboQuantDistance<ReferenceVectorStore<FloatType>>;
        return std::make_unique<DFT>(lhs, *_sq_norm_store, true, _mse_levels);
    }
    using DFT = BoundTurboQuantDistance<TemporaryVectorStore<FloatType>>;
    return std::make_unique<DFT>(lhs, *_sq_norm_store, true, _mse_levels);
}

template <typename FloatType>
void
TurboQuantDistanceFunctionFactory<FloatType>::save_state(vespalib::GenericHeader& header) const
{
    MipsDistanceFunctionFactoryBase::save_state(header);
    header.putTag(vespalib::GenericHeader::Tag(hnsw_turbo_quant_version, static_cast<uint32_t>(turbo_quant_state_version)));
    header.putTag(vespalib::GenericHeader::Tag(hnsw_turbo_quant_levels, _mse_levels));
}

template <typename FloatType>
void
TurboQuantDistanceFunctionFactory<FloatType>::load_state(const vespalib::GenericHeader& header)
{
    MipsDistanceFunctionFactoryBase::load_state(header);
    if (header.hasTag(hnsw_turbo_quant_levels)) {
        const auto& tag = header.getTag(hnsw_turbo_quant_levels);
        if (tag.getType() == vespalib::GenericHeader::Tag::Type::TYPE_INTEGER) {
            auto levels = static_cast<uint32_t>(tag.asInteger());
            if ((levels == 4u) || (levels == 8u)) {
                _mse_levels = levels;
            }
        }
    }
}

template class TurboQuantDistanceFunctionFactory<Int8Float>;
template class TurboQuantDistanceFunctionFactory<vespalib::BFloat16>;
template class TurboQuantDistanceFunctionFactory<float>;
template class TurboQuantDistanceFunctionFactory<double>;

}
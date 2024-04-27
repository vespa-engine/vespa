// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distance_function.h"
#include "distance_function_factory.h"
#include "i_tensor_attribute.h"
#include "vector_bundle.h"
#include <vespa/eval/eval/value_type.h>
#include <optional>

namespace vespalib::eval { struct Value; }

namespace search::attribute { class IAttributeVector; }

namespace search::tensor {

/**
 * Class used to calculate the distance between two n-dimensional vectors,
 * where one is stored in a TensorAttribute and the other comes from the query.
 *
 * The distance function to use is defined in the TensorAttribute.
 */
class DistanceCalculator {
private:
    const tensor::ITensorAttribute& _attr_tensor;
    const vespalib::eval::Value* _query_tensor;
    std::unique_ptr<BoundDistanceFunction> _dist_fun;

public:
    DistanceCalculator(const tensor::ITensorAttribute& attr_tensor,
                       const vespalib::eval::Value& query_tensor_in);

    ~DistanceCalculator();

    const tensor::ITensorAttribute& attribute_tensor() const { return _attr_tensor; }
    const vespalib::eval::Value& query_tensor() const noexcept{
        assert(_query_tensor != nullptr);
        return *_query_tensor;
    }
    const BoundDistanceFunction& function() const noexcept { return *_dist_fun; }
    bool has_single_subspace() const noexcept { return _attr_tensor.getTensorType().is_dense(); }

    template<bool has_single_subspace>
    double calc_raw_score(uint32_t docid) const noexcept {
        if (has_single_subspace) {
            auto cells = _attr_tensor.get_vector(docid, 0);
            double min_rawscore = _dist_fun->min_rawscore();
            if ( cells.non_existing_attribute_value() ) [[unlikely]] {
                return min_rawscore;
            }
            return std::max(min_rawscore, _dist_fun->to_rawscore(_dist_fun->calc(cells)));
        } else {
            auto vectors = _attr_tensor.get_vectors(docid);
            double result = _dist_fun->min_rawscore();
            for (uint32_t i = 0; i < vectors.subspaces(); ++i) {
                double distance = _dist_fun->calc(vectors.cells(i));
                double score = _dist_fun->to_rawscore(distance);
                result = std::max(result, score);
            }
            return result;
        }

    }

    template<bool has_single_subspace>
    double calc_with_limit(uint32_t docid, double limit) const noexcept {
        if (has_single_subspace) {
            auto cells = _attr_tensor.get_vector(docid, 0);
            if ( cells.non_existing_attribute_value() ) [[unlikely]] {
                return std::numeric_limits<double>::max();
            }
            return _dist_fun->calc_with_limit(cells, limit);
        } else {
            auto vectors = _attr_tensor.get_vectors(docid);
            double result = std::numeric_limits<double>::max();
            for (uint32_t i = 0; i < vectors.subspaces(); ++i) {
                double distance = _dist_fun->calc_with_limit(vectors.cells(i), limit);
                result = std::min(result, distance);
            }
            return result;
        }
    }

    void calc_closest_subspace(VectorBundle vectors, std::optional<uint32_t>& closest_subspace, double& best_distance) noexcept {
        for (uint32_t i = 0; i < vectors.subspaces(); ++i) {
            double distance = _dist_fun->calc(vectors.cells(i));
            if (!closest_subspace.has_value() || distance < best_distance) {
                best_distance = distance;
                closest_subspace = i;
            }
        }
    }

    std::optional<uint32_t> calc_closest_subspace(VectorBundle vectors) noexcept {
        double best_distance = 0.0;
        std::optional<uint32_t> closest_subspace;
        calc_closest_subspace(vectors, closest_subspace, best_distance);
        return closest_subspace;
    }

    /**
     * Create a calculator for the given attribute tensor and query tensor, if possible.
     *
     * Throws vespalib::IllegalArgumentException if the inputs are not supported or incompatible.
     */
    static std::unique_ptr<DistanceCalculator> make_with_validation(const search::attribute::IAttributeVector& attr,
                                                                    const vespalib::eval::Value& query_tensor_in);

};

}

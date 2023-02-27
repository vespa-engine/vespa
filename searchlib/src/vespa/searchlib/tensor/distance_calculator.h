// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distance_function.h"
#include "i_tensor_attribute.h"
#include "vector_bundle.h"
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
    std::unique_ptr<vespalib::eval::Value> _query_tensor_uptr;
    const vespalib::eval::Value* _query_tensor;
    vespalib::eval::TypedCells _query_tensor_cells;
    std::unique_ptr<DistanceFunction> _dist_fun_uptr;
    const DistanceFunction* _dist_fun;

public:
    DistanceCalculator(const tensor::ITensorAttribute& attr_tensor,
                       const vespalib::eval::Value& query_tensor_in);

    /**
     * Only used by unit tests where ownership of query tensor and distance function is handled outside.
     */
    DistanceCalculator(const tensor::ITensorAttribute& attr_tensor,
                       const vespalib::eval::Value& query_tensor_in,
                       const DistanceFunction& function_in);

    ~DistanceCalculator();

    const tensor::ITensorAttribute& attribute_tensor() const { return _attr_tensor; }
    const vespalib::eval::Value& query_tensor() const { return *_query_tensor; }
    const DistanceFunction& function() const { return *_dist_fun; }

    double calc_raw_score(uint32_t docid) const {
        auto vectors = _attr_tensor.get_vectors(docid);
        double result = 0.0;
        for (uint32_t i = 0; i < vectors.subspaces(); ++i) {
            double distance = _dist_fun->calc(_query_tensor_cells, vectors.cells(i));
            double score = _dist_fun->to_rawscore(distance);
            result = std::max(result, score);
        }
        return result;
    }

    double calc_with_limit(uint32_t docid, double limit) const {
        auto vectors = _attr_tensor.get_vectors(docid);
        double result = std::numeric_limits<double>::max();
        for (uint32_t i = 0; i < vectors.subspaces(); ++i) {
            double distance = _dist_fun->calc_with_limit(_query_tensor_cells, vectors.cells(i), limit);
            result = std::min(result, distance);
        }
        return result;
    }

    void calc_closest_subspace(VectorBundle vectors, std::optional<uint32_t>& closest_subspace, double& best_distance) {
        for (uint32_t i = 0; i < vectors.subspaces(); ++i) {
            double distance = _dist_fun->calc(_query_tensor_cells, vectors.cells(i));
            if (!closest_subspace.has_value() || distance < best_distance) {
                best_distance = distance;
                closest_subspace = i;
            }
        }
    }

    std::optional<uint32_t> calc_closest_subspace(VectorBundle vectors) {
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

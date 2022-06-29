// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/eval/eval/typed_cells.h>
#include <memory>

namespace vespalib::eval { struct Value; }

namespace search::tensor {

class DistanceFunction;
class ITensorAttribute;

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
                       std::unique_ptr<vespalib::eval::Value> query_tensor_in);

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

    double calc_with_limit(uint32_t docid, double limit) const;
};

}

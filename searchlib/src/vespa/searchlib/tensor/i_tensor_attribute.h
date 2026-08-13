// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "doc_vector_access.h"

#include <vespa/eval/eval/typed_cells.h>
#include <vespa/searchcommon/attribute/distance_metric.h>

#include <memory>

namespace vespalib {
struct StateExplorer;
}
namespace vespalib::eval {
class ValueType;
struct Value;
} // namespace vespalib::eval
namespace vespalib::slime {
struct Inserter;
}

namespace search::tensor {

struct DistanceFunctionFactory;
class NearestNeighborIndex;
class SerializedTensorRef;
class TensorDequantizer;

/**
 * Interface for tensor attribute used by feature executors to get information.
 */
class ITensorAttribute : public DocVectorAccess {
public:
    ~ITensorAttribute() override = default;

    /*
     * Returns whether the tensor data stored in this attribute is a quantized representation
     * of full precision tensor data kept in the document store. If so, the tensor type of the
     * attribute will be a transformed version of the original full precision type that is
     * suitable for storing quantized dense subspaces. The original type can be retrieved from
     * `unquantized_tensor_type()`.
     *
     * Tensor attribute users must _explicitly_ check for, and handle, the presence of
     * quantized tensors when setting or getting tensors, as the input and output tensors must
     * be quantized and dequantized, respectively.
     */
    [[nodiscard]] virtual bool is_quantized() const noexcept = 0;
    /*
     * Iff `is_quantized() == true`, this returns the original, unquantized tensor type as
     * defined in the document type schema. Otherwise, returns the same type as getTensorType().
     */
    [[nodiscard]] virtual const vespalib::eval::ValueType& unquantized_tensor_type() const noexcept = 0;
    /*
     * Returns a new TensorDequantizer instance that can be used to transform stored, quantized
     * tensors to a shape and type matching `unquantized_tensor_type()`.
     *
     * A TensorDequantizer constructed from one TensorAttribute must never be attempted used
     * on the tensor data retrieved from another, unrelated TensorAttribute.
     *
     * Important: individual TensorDequantizer instances are _not_ thread safe.
     *
     * Precondition: `is_quantized() == true`
     */
    [[nodiscard]] virtual std::unique_ptr<TensorDequantizer> make_dequantizer() const = 0;

    [[nodiscard]] virtual std::unique_ptr<vespalib::eval::Value> getTensor(uint32_t docId) const = 0;
    [[nodiscard]] virtual std::unique_ptr<vespalib::eval::Value> getEmptyTensor() const = 0;
    [[nodiscard]] virtual vespalib::eval::TypedCells extract_cells_ref(uint32_t docid) const = 0;
    [[nodiscard]] virtual const vespalib::eval::Value& get_tensor_ref(uint32_t docid) const = 0;
    [[nodiscard]] virtual SerializedTensorRef get_serialized_tensor_ref(uint32_t docid) const = 0;
    [[nodiscard]] virtual bool supports_extract_cells_ref() const = 0;
    [[nodiscard]] virtual bool supports_get_tensor_ref() const = 0;
    [[nodiscard]] virtual bool supports_get_serialized_tensor_ref() const = 0;

    [[nodiscard]] virtual const vespalib::eval::ValueType& getTensorType() const = 0;

    [[nodiscard]] virtual DistanceFunctionFactory& distance_function_factory() const = 0;
    [[nodiscard]] virtual const NearestNeighborIndex* nearest_neighbor_index() const { return nullptr; }
    using DistanceMetric = search::attribute::DistanceMetric;
    [[nodiscard]] virtual DistanceMetric distance_metric() const = 0;
    [[nodiscard]] virtual uint32_t get_num_docs() const = 0;

    /**
     * Creates a state explorer for this tensor attribute.
     */
    [[nodiscard]] virtual std::unique_ptr<vespalib::StateExplorer> make_state_explorer() const = 0;
};

} // namespace search::tensor

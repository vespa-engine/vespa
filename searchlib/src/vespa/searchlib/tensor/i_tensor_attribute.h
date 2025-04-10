// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "doc_vector_access.h"
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/searchcommon/attribute/distance_metric.h>
#include <memory>

namespace vespalib { struct StateExplorer; }
namespace vespalib::eval { class ValueType; struct Value; }
namespace vespalib::slime { struct Inserter; }

namespace search::tensor {

struct DistanceFunctionFactory;
class NearestNeighborIndex;
class SerializedTensorRef;

/**
 * Interface for tensor attribute used by feature executors to get information.
 */
class ITensorAttribute : public DocVectorAccess {
public:
    virtual ~ITensorAttribute() = default;
    virtual std::unique_ptr<vespalib::eval::Value> getTensor(uint32_t docId) const = 0;
    virtual std::unique_ptr<vespalib::eval::Value> getEmptyTensor() const = 0;
    virtual vespalib::eval::TypedCells extract_cells_ref(uint32_t docid) const = 0;
    virtual const vespalib::eval::Value& get_tensor_ref(uint32_t docid) const = 0;
    virtual SerializedTensorRef get_serialized_tensor_ref(uint32_t docid) const = 0;
    virtual bool supports_extract_cells_ref() const = 0;
    virtual bool supports_get_tensor_ref() const = 0;
    virtual bool supports_get_serialized_tensor_ref() const = 0;

    virtual const vespalib::eval::ValueType & getTensorType() const = 0;

    virtual DistanceFunctionFactory& distance_function_factory() const = 0;
    virtual const NearestNeighborIndex* nearest_neighbor_index() const { return nullptr; }
    using DistanceMetric = search::attribute::DistanceMetric;
    virtual DistanceMetric distance_metric() const = 0;
    virtual uint32_t get_num_docs() const = 0;

    /**
     * Creates a state explorer for this tensor attribute.
     */
    virtual std::unique_ptr<vespalib::StateExplorer> make_state_explorer() const = 0;
};

}  // namespace search::tensor

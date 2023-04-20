// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_tensor_attribute.h"
#include "empty_subspace.h"
#include "subspace_type.h"
#include "distance_function_factory.h"
#include <vespa/searchlib/attribute/not_implemented_attribute.h>
#include <vespa/vespalib/stllike/allocator.h>

namespace search::tensor {

/**
 * Attribute vector storing a pointer to single tensor value per
 * document in streaming search. The tensor is not owned by this
 * attribute vector.
 */
class TensorExtAttribute : public NotImplementedAttribute,
                           public ITensorAttribute,
                           public IExtendAttribute
{
    std::vector<const vespalib::eval::Value*> _data;
    // XXX this should probably be longer-lived:
    std::unique_ptr<DistanceFunctionFactory>  _distance_function_factory;
    SubspaceType                              _subspace_type;
    EmptySubspace                             _empty;
    std::unique_ptr<vespalib::eval::Value>    _empty_tensor;

public:
    TensorExtAttribute(const vespalib::string& name, const Config& cfg);
    ~TensorExtAttribute() override;
    const ITensorAttribute* asTensorAttribute() const override;
    void onCommit() override;
    void onUpdateStat() override;
    bool addDoc(DocId& docId) override;
    bool add(const vespalib::eval::Value& v, int32_t) override;
    IExtendAttribute* getExtendInterface() override;
    // DocVectorAccess API
    vespalib::eval::TypedCells get_vector(uint32_t docid, uint32_t subspace) const override;
    VectorBundle get_vectors(uint32_t docid) const override;

    // ITensorAttribute API
    std::unique_ptr<vespalib::eval::Value> getTensor(uint32_t docid) const override;
    std::unique_ptr<vespalib::eval::Value> getEmptyTensor() const override;
    vespalib::eval::TypedCells extract_cells_ref(uint32_t docid) const override;
    const vespalib::eval::Value& get_tensor_ref(uint32_t docid) const override;
    SerializedTensorRef get_serialized_tensor_ref(uint32_t docid) const override;
    bool supports_extract_cells_ref() const override;
    bool supports_get_tensor_ref() const override;
    bool supports_get_serialized_tensor_ref() const override;
    const vespalib::eval::ValueType & getTensorType() const override;
    DistanceFunctionFactory& distance_function_factory() const override {
        return *_distance_function_factory;
    }
    search::attribute::DistanceMetric distance_metric() const override;
    uint32_t get_num_docs() const override;
    void get_state(const vespalib::slime::Inserter& inserter) const override;
};

}

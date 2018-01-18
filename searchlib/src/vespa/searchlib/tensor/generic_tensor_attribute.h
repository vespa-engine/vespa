// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_attribute.h"
#include "generic_tensor_store.h"

namespace search {

namespace tensor {

/**
 * Attribute vector class used to store tensors for all documents in memory.
 */
class GenericTensorAttribute : public TensorAttribute
{
    GenericTensorStore _genericTensorStore; // data store for serialized tensors
public:
    GenericTensorAttribute(const vespalib::stringref &baseFileName, const Config &cfg);
    virtual ~GenericTensorAttribute();
    virtual void setTensor(DocId docId, const Tensor &tensor) override;
    virtual std::unique_ptr<Tensor> getTensor(DocId docId) const override;
    virtual void getTensor(DocId docId, vespalib::tensor::MutableDenseTensorView &tensor) const override;
    virtual bool onLoad() override;
    virtual std::unique_ptr<AttributeSaver> onInitSave() override;
    virtual void compactWorst() override;
};


}  // namespace search::tensor

}  // namespace search

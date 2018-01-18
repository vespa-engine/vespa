// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_attribute.h"
#include "dense_tensor_store.h"

namespace vespalib { namespace tensor { class MutableDenseTensorView; }}

namespace search {

namespace tensor {

/**
 * Attribute vector class used to store dense tensors for all
 * documents in memory.
 */
class DenseTensorAttribute : public TensorAttribute
{
    DenseTensorStore _denseTensorStore;
public:
    DenseTensorAttribute(const vespalib::stringref &baseFileName, const Config &cfg);
    virtual ~DenseTensorAttribute();
    virtual void setTensor(DocId docId, const Tensor &tensor) override;
    virtual std::unique_ptr<Tensor> getTensor(DocId docId) const override;
    virtual void getTensor(DocId docId, vespalib::tensor::MutableDenseTensorView &tensor) const override;
    virtual bool onLoad() override;
    virtual std::unique_ptr<AttributeSaver> onInitSave() override;
    virtual void compactWorst() override;
    virtual uint32_t getVersion() const override;
};


}  // namespace search::tensor

}  // namespace search

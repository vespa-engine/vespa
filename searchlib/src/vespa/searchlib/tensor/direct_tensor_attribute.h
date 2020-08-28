// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_attribute.h"

namespace search::tensor {

class DirectTensorAttribute : public TensorAttribute
{
    // XXX must have some sort of TensorStore here
public:
    DirectTensorAttribute(vespalib::stringref baseFileName, const Config &cfg);
    virtual ~DirectTensorAttribute();
    virtual void setTensor(DocId docId, const Tensor &tensor) override;
    virtual std::unique_ptr<Tensor> getTensor(DocId docId) const override;
    virtual void getTensor(DocId docId, vespalib::tensor::MutableDenseTensorView &tensor) const override;
    virtual bool onLoad() override;
    virtual std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    virtual void compactWorst() override;

    void setTensor(DocId docId, std::unique_ptr<Tensor> tensor);
};

}  // namespace search::tensor

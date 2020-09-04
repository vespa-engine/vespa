// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_attribute.h"
#include "direct_tensor_store.h"

namespace search::tensor {

class DirectTensorAttribute : public TensorAttribute
{
    DirectTensorStore _direct_store;
public:
    DirectTensorAttribute(vespalib::stringref baseFileName, const Config &cfg);
    virtual ~DirectTensorAttribute();
    virtual void setTensor(DocId docId, const Tensor &tensor) override;
    virtual std::unique_ptr<Tensor> getTensor(DocId docId) const override;
    virtual bool onLoad() override;
    virtual std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    virtual void compactWorst() override;

    void set_tensor(DocId docId, std::unique_ptr<Tensor> tensor);
    const Tensor &get_tensor_ref(DocId docId) const override;
    virtual bool supports_get_tensor_ref() const override { return true; }
};

}  // namespace search::tensor

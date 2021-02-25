// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_attribute.h"
#include "direct_tensor_store.h"

namespace vespalib::eval { struct Value; }

namespace search::tensor {

class DirectTensorAttribute : public TensorAttribute
{
    DirectTensorStore _direct_store;

public:
    DirectTensorAttribute(vespalib::stringref baseFileName, const Config &cfg);
    virtual ~DirectTensorAttribute();
    virtual void setTensor(DocId docId, const vespalib::eval::Value &tensor) override;
    void update_tensor(DocId docId,
                       const document::TensorUpdate &update,
                       bool create_empty_if_non_existing) override;
    virtual std::unique_ptr<vespalib::eval::Value> getTensor(DocId docId) const override;
    virtual bool onLoad() override;
    virtual std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    virtual void compactWorst() override;

    void set_tensor(DocId docId, std::unique_ptr<vespalib::eval::Value> tensor);
    const vespalib::eval::Value &get_tensor_ref(DocId docId) const override;
    virtual bool supports_get_tensor_ref() const override { return true; }
};

}  // namespace search::tensor

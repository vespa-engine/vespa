// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_attribute.h"
#include "streamed_value_store.h"

namespace search::tensor {

/**
 * Attribute vector class storing serialized tensors for all documents in memory.
 *
 * When fetching a tensor with getTensor(docId) the returned Value
 * will have a FastValueIndex (constructed on the fly) for its sparse
 * mapping, but refer to a common type, while cells() will refer to
 * memory in the serialized store without copying.
 *
 */
class SerializedFastValueAttribute : public TensorAttribute {
    vespalib::eval::ValueType _tensor_type;
    StreamedValueStore _streamedValueStore; // data store for serialized tensors
    const StreamedValueStore::DataFromType _data_from_type;
public:
    SerializedFastValueAttribute(vespalib::stringref baseFileName, const Config &cfg);
    virtual ~SerializedFastValueAttribute();
    virtual void setTensor(DocId docId, const vespalib::eval::Value &tensor) override;
    virtual std::unique_ptr<vespalib::eval::Value> getTensor(DocId docId) const override;
    virtual bool onLoad() override;
    virtual std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    virtual void compactWorst() override;
};

}

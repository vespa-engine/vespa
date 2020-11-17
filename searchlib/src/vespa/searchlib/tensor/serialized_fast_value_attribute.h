
#pragma once

#include "tensor_attribute.h"
#include "streamed_value_store.h"

namespace search::tensor {

/**
 * Attribute vector class storing FastValue-like mixed tensors for all documents in memory.
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

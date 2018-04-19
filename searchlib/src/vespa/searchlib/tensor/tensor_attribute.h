// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_tensor_attribute.h"
#include <vespa/searchlib/attribute/not_implemented_attribute.h>
#include "tensor_store.h"
#include <vespa/searchlib/common/rcuvector.h>
#include <vespa/eval/tensor/tensor_mapper.h>

namespace search {

namespace tensor {

/**
 * Attribute vector class used to store tensors for all documents in memory.
 */
class TensorAttribute : public NotImplementedAttribute, public ITensorAttribute
{
protected:
    using RefType = TensorStore::EntryRef;
    using RefVector = attribute::RcuVectorBase<RefType>;

    RefVector _refVector; // docId -> ref in data store for serialized tensor
    TensorStore &_tensorStore; // data store for serialized tensors
    std::unique_ptr<vespalib::tensor::TensorMapper> _tensorMapper; // mapper to our tensor type
    uint64_t    _compactGeneration; // Generation when last compact occurred

    template <typename RefType>
    void doCompactWorst();
    void setTensorRef(DocId docId, RefType ref);
public:
    DECLARE_IDENTIFIABLE_ABSTRACT(TensorAttribute);
    using RefCopyVector = vespalib::Array<RefType>;
    TensorAttribute(const vespalib::stringref &baseFileName, const Config &cfg,
                    TensorStore &tensorStore);
    virtual ~TensorAttribute();
    virtual const ITensorAttribute *asTensorAttribute() const override;

    virtual uint32_t clearDoc(DocId docId) override;
    virtual void onCommit() override;
    virtual void onUpdateStat() override;
    virtual void removeOldGenerations(generation_t firstUsed) override;
    virtual void onGenerationChange(generation_t generation) override;
    virtual bool addDoc(DocId &docId) override;
    virtual std::unique_ptr<Tensor> getEmptyTensor() const override;
    virtual vespalib::eval::ValueType getTensorType() const override;
    virtual void clearDocs(DocId lidLow, DocId lidLimit) override;
    virtual void onShrinkLidSpace() override;
    virtual uint32_t getVersion() const override;
    RefCopyVector getRefCopy() const;
    virtual void setTensor(DocId docId, const Tensor &tensor) = 0;
    virtual void compactWorst() = 0;
};


}  // namespace search::tensor

}  // namespace search

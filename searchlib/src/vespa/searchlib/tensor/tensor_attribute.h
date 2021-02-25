// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_tensor_attribute.h"
#include "prepare_result.h"
#include "tensor_store.h"
#include <vespa/searchlib/attribute/not_implemented_attribute.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <vespa/document/update/tensor_update.h>

namespace vespalib::eval { struct Value; struct ValueBuilderFactory; }

namespace search::tensor {

/**
 * Attribute vector class used to store tensors for all documents in memory.
 */
class TensorAttribute : public NotImplementedAttribute, public ITensorAttribute
{
protected:
    using EntryRef = TensorStore::EntryRef;
    using RefVector = vespalib::RcuVectorBase<EntryRef>;

    RefVector _refVector; // docId -> ref in data store for serialized tensor
    TensorStore &_tensorStore; // data store for serialized tensors
    std::unique_ptr<vespalib::eval::Value> _emptyTensor;
    uint64_t    _compactGeneration; // Generation when last compact occurred

    template <typename RefType>
    void doCompactWorst();
    void checkTensorType(const vespalib::eval::Value &tensor);
    void setTensorRef(DocId docId, EntryRef ref);
    virtual vespalib::MemoryUsage memory_usage() const;
    void populate_state(vespalib::slime::Cursor& object) const;

public:
    DECLARE_IDENTIFIABLE_ABSTRACT(TensorAttribute);
    using RefCopyVector = vespalib::Array<EntryRef>;
    TensorAttribute(vespalib::stringref name, const Config &cfg, TensorStore &tensorStore);
    ~TensorAttribute() override;
    const ITensorAttribute *asTensorAttribute() const override;

    uint32_t clearDoc(DocId docId) override;
    void onCommit() override;
    void onUpdateStat() override;
    void removeOldGenerations(generation_t firstUsed) override;
    void onGenerationChange(generation_t generation) override;
    bool addDoc(DocId &docId) override;
    std::unique_ptr<vespalib::eval::Value> getEmptyTensor() const override;
    vespalib::eval::TypedCells extract_cells_ref(uint32_t docid) const override;
    const vespalib::eval::Value& get_tensor_ref(uint32_t docid) const override;
    bool supports_extract_cells_ref() const override { return false; }
    bool supports_get_tensor_ref() const override { return false; }
    const vespalib::eval::ValueType & getTensorType() const override;
    void get_state(const vespalib::slime::Inserter& inserter) const override;
    void clearDocs(DocId lidLow, DocId lidLimit) override;
    void onShrinkLidSpace() override;
    uint32_t getVersion() const override;
    RefCopyVector getRefCopy() const;
    virtual void setTensor(DocId docId, const vespalib::eval::Value &tensor) = 0;
    virtual void update_tensor(DocId docId,
                               const document::TensorUpdate &update,
                               bool create_empty_if_non_existing);
    /**
     * Performs the prepare step in a two-phase operation to set a tensor for a document.
     *
     * This function can be called by any thread.
     * It should return the result of the costly and non-modifying part of such operation.
     */
    virtual std::unique_ptr<PrepareResult> prepare_set_tensor(DocId docid, const vespalib::eval::Value& tensor) const;

    /**
     * Performs the complete step in a two-phase operation to set a tensor for a document.
     *
     * This function is only called by the attribute writer thread.
     * It uses the result from the prepare step to do the modifying changes.
     */
    virtual void complete_set_tensor(DocId docid, const vespalib::eval::Value& tensor, std::unique_ptr<PrepareResult> prepare_result);

    virtual void compactWorst() = 0;
};

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_tensor_attribute.h"
#include "doc_vector_access.h"
#include "prepare_result.h"
#include "subspace_type.h"
#include "tensor_store.h"
#include "typed_cells_comparator.h"
#include <vespa/searchlib/attribute/not_implemented_attribute.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <vespa/document/update/tensor_update.h>

namespace vespalib::eval { struct Value; struct ValueBuilderFactory; }

namespace search::tensor {

/**
 * Attribute vector class used to store tensors for all documents in memory.
 */
class TensorAttribute : public NotImplementedAttribute, public ITensorAttribute, public DocVectorAccess {
protected:
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using EntryRef = TensorStore::EntryRef;
    using RefVector = vespalib::RcuVectorBase<AtomicEntryRef>;

    RefVector _refVector; // docId -> ref in data store for serialized tensor
    TensorStore &_tensorStore; // data store for serialized tensors
    std::unique_ptr<NearestNeighborIndex> _index;
    bool _is_dense;
    std::unique_ptr<vespalib::eval::Value> _emptyTensor;
    uint64_t    _compactGeneration; // Generation when last compact occurred
    SubspaceType         _subspace_type;
    TypedCellsComparator _comp;

    void checkTensorType(const vespalib::eval::Value &tensor) const;
    void setTensorRef(DocId docId, EntryRef ref);
    void internal_set_tensor(DocId docid, const vespalib::eval::Value& tensor);
    void consider_remove_from_index(DocId docid);
    virtual vespalib::MemoryUsage update_stat();
    void populate_state(vespalib::slime::Cursor& object) const;
    void populate_address_space_usage(AddressSpaceUsage& usage) const override;
    EntryRef acquire_entry_ref(DocId doc_id) const noexcept { return _refVector.acquire_elem_ref(doc_id).load_acquire(); }
    bool onLoad(vespalib::Executor *executor) override;
    std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    bool tensor_cells_are_unchanged(DocId docid, VectorBundle vectors) const;

public:
    using RefCopyVector = vespalib::Array<EntryRef>;
    TensorAttribute(vespalib::stringref name, const Config &cfg, TensorStore &tensorStore);
    ~TensorAttribute() override;
    const ITensorAttribute *asTensorAttribute() const override;

    uint32_t clearDoc(DocId docId) override;
    void onCommit() override;
    void onUpdateStat() override;
    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;
    bool addDoc(DocId &docId) override;
    std::unique_ptr<vespalib::eval::Value> getTensor(DocId docId) const override;
    std::unique_ptr<vespalib::eval::Value> getEmptyTensor() const override;
    vespalib::eval::TypedCells extract_cells_ref(uint32_t docid) const override;
    const vespalib::eval::Value& get_tensor_ref(uint32_t docid) const override;
    bool supports_extract_cells_ref() const override { return false; }
    bool supports_get_tensor_ref() const override { return false; }
    const vespalib::eval::ValueType & getTensorType() const override;
    const NearestNeighborIndex* nearest_neighbor_index() const override;
    void get_state(const vespalib::slime::Inserter& inserter) const override;
    void clearDocs(DocId lidLow, DocId lidLimit, bool in_shrink_lid_space) override;
    void onShrinkLidSpace() override;
    uint32_t getVersion() const override;
    RefCopyVector getRefCopy() const;
    virtual void setTensor(DocId docId, const vespalib::eval::Value &tensor);
    virtual void update_tensor(DocId docId,
                               const document::TensorUpdate &update,
                               bool create_empty_if_non_existing);
    DistanceMetric distance_metric() const override;
    uint32_t get_num_docs() const override { return getNumDocs(); }

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
};

}

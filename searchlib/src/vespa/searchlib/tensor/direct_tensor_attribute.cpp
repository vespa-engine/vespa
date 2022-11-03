// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_tensor_attribute.h"
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/config.h>

using vespalib::eval::FastValueBuilderFactory;

namespace search::tensor {

DirectTensorAttribute::DirectTensorAttribute(stringref name, const Config &cfg)
    : TensorAttribute(name, cfg, _direct_store),
      _direct_store(cfg.tensorType())
{
}

DirectTensorAttribute::~DirectTensorAttribute()
{
    getGenerationHolder().reclaim_all();
    _tensorStore.reclaim_all_memory();
}

void
DirectTensorAttribute::set_tensor(DocId lid, std::unique_ptr<vespalib::eval::Value> tensor)
{
    checkTensorType(*tensor);
    EntryRef ref = _direct_store.store_tensor(std::move(tensor));
    setTensorRef(lid, ref);
}

void
DirectTensorAttribute::setTensor(DocId lid, const vespalib::eval::Value &tensor)
{
    set_tensor(lid, FastValueBuilderFactory::get().copy(tensor));
}

void
DirectTensorAttribute::update_tensor(DocId docId,
                                     const document::TensorUpdate &update,
                                     bool create_if_non_existing)
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId].load_relaxed();
    }
    if (ref.valid()) {
        auto ptr = _direct_store.get_tensor_ptr(ref);
        if (ptr) {
            auto new_value = update.apply_to(*ptr, FastValueBuilderFactory::get());
            if (new_value) {
                set_tensor(docId, std::move(new_value));
            }
            return;
        }
    }
    if (create_if_non_existing) {
        auto new_value = update.apply_to(*_emptyTensor, FastValueBuilderFactory::get());
        if (new_value) {
            set_tensor(docId, std::move(new_value));
        }
    }
}

const vespalib::eval::Value &
DirectTensorAttribute::get_tensor_ref(DocId docId) const
{
    if (docId >= getCommittedDocIdLimit()) { return *_emptyTensor; }

    auto ptr = _direct_store.get_tensor_ptr(acquire_entry_ref(docId));
    if ( ptr == nullptr) { return *_emptyTensor; }

    return *ptr;
}

vespalib::eval::TypedCells
DirectTensorAttribute::get_vector(uint32_t docid, uint32_t subspace) const
{
    EntryRef ref = acquire_entry_ref(docid);
    auto vectors = _direct_store.get_vectors(ref);
    return (subspace < vectors.subspaces()) ? vectors.cells(subspace) : _direct_store.get_empty_subspace();
}

VectorBundle
DirectTensorAttribute::get_vectors(uint32_t docid) const
{
    EntryRef ref = acquire_entry_ref(docid);
    return _direct_store.get_vectors(ref);
}

} // namespace

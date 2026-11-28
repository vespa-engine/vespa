// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "direct_tensor_attribute.h"

#include <vespa/searchlib/attribute/enumstore.h>
#include <vespa/searchlib/attribute/i_docid_with_float_weight_posting_store.h>
#include <vespa/searchlib/attribute/postingstore.h>
#include <vespa/searchlib/attribute/string_direct_posting_store_adapter.h>
#include <vespa/vespalib/util/string_id.h>

#include <vector>

namespace search::tensor {

/**
 * Tensor attribute storing full tensors (like DirectTensorAttribute), that also
 * keeps a dictionary of all tensor labels with a posting list per label.
 * The posting lists contain {docid, weight} where weight is the tensor cell value
 * (assumed to be "float" and converted otherwise; capped to finite values, and
 * NaN is stored as 0).
 *
 * Only sparse tensor types with exactly one mapped dimension are supported.
 * Labels are matched exactly (cased). Labels containing NUL get no postings.
 */
class DirectTensorPostingAttribute : public DirectTensorAttribute {
public:
    using LabelStore = EnumStoreT<const char*>;
    using LabelPostingStore = attribute::PostingStore<float>;
    using PostingStoreAdapter =
        attribute::StringDirectPostingStoreAdapter<IDocidWithFloatWeightPostingStore, LabelPostingStore, LabelStore>;
    using LabelWeight = std::pair<vespalib::string_id, float>;
    // Temporary (label, posting) entry used when rebuilding postings on load.
    using RebuildEntry = std::pair<vespalib::string_id, LabelPostingStore::KeyDataType>;

private:
    LabelStore          _label_store;
    LabelPostingStore   _posting_store;
    PostingStoreAdapter _posting_store_adapter;
    uint64_t            _total_subspaces;

    void collect_labels(EntryRef ref, std::vector<LabelWeight>& labels) const;
    void add_to_posting_list(IEnumStore::Index idx, DocId docid, float weight);
    void remove_from_posting_list(IEnumStore::Index idx, DocId docid);
    void clear_all_postings();
    void rebuild_postings();

protected:
    void on_tensor_ref_change(DocId docid, EntryRef old_ref, EntryRef new_ref) override;
    vespalib::MemoryUsage update_stat() override;
    bool onLoad(vespalib::Executor* executor) override;

public:
    DirectTensorPostingAttribute(std::string_view baseFileName, const Config& cfg);
    ~DirectTensorPostingAttribute() override;

    bool addDoc(DocId& docId) override;
    void onCommit() override;
    void onShrinkLidSpace() override;
    void reclaim_memory(vespalib::Generation oldest_used_gen) override;
    void before_inc_generation(vespalib::Generation current_gen) override;
    const IDocidWithFloatWeightPostingStore* label_posting_store() const override { return &_posting_store_adapter; }
    void populate_address_space_usage(AddressSpaceUsage& usage) const override;

    const LabelStore& get_label_store() const noexcept { return _label_store; }
    /*
     * Total number of subspaces (labels) over all stored tensors, including
     * labels that get no postings. Only to be used by the writer thread.
     */
    uint64_t get_total_subspaces() const noexcept { return _total_subspaces; }
    /*
     * Transient memory used when rebuilding postings on load, given the
     * total number of subspaces over all stored tensors.
     */
    static constexpr size_t rebuild_transient_memory(uint64_t total_subspaces) noexcept {
        return total_subspaces * sizeof(RebuildEntry);
    }
    const LabelPostingStore& get_posting_store() const noexcept { return _posting_store; }
};

} // namespace search::tensor

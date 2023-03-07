// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "not_implemented_attribute.h"
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace search::attribute {

/**
 * Attribute vector storing a single raw value per document.
 */
class SingleRawAttribute : public NotImplementedAttribute
{
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using EntryRef = vespalib::datastore::EntryRef;
    using RefVector = vespalib::RcuVectorBase<AtomicEntryRef>;
    using RefType = vespalib::datastore::EntryRefT<19>;
    using ArrayStoreType = vespalib::datastore::ArrayStore<char, RefType>;

    RefVector                           _ref_vector;
    ArrayStoreType                      _array_store;
    vespalib::datastore::CompactionSpec _compaction_spec;

    vespalib::MemoryUsage update_stat();
    EntryRef acquire_entry_ref(DocId docid) const noexcept { return _ref_vector.acquire_elem_ref(docid).load_acquire(); }
    EntryRef set_raw(vespalib::ConstArrayRef<char> raw);
    vespalib::ConstArrayRef<char> get_raw(EntryRef ref) const;
public:
    SingleRawAttribute(const vespalib::string& name, const Config& config);
    ~SingleRawAttribute() override;
    void onCommit() override;
    void onUpdateStat() override;
    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;
    bool addDoc(DocId &docId) override;
    vespalib::ConstArrayRef<char> get_raw(DocId docid) const override;
    void set_raw(DocId docid, vespalib::ConstArrayRef<char> raw);
    uint32_t clearDoc(DocId docId) override;
};

}

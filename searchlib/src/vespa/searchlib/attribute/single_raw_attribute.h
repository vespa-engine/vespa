// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "raw_attribute.h"
#include "raw_buffer_store.h"
#include <vespa/vespalib/util/rcuvector.h>

namespace search::attribute {

/**
 * Attribute vector storing a single raw value per document.
 */
class SingleRawAttribute : public RawAttribute
{
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using EntryRef = vespalib::datastore::EntryRef;
    using RefVector = vespalib::RcuVectorBase<AtomicEntryRef>;

    RefVector                           _ref_vector;
    RawBufferStore                      _raw_store;

    vespalib::MemoryUsage update_stat();
    EntryRef acquire_entry_ref(DocId docid) const noexcept { return _ref_vector.acquire_elem_ref(docid).load_acquire(); }
    bool onLoad(vespalib::Executor *executor) override;
    std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    void populate_address_space_usage(AddressSpaceUsage& usage) const override;
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
    bool update(DocId docid, vespalib::ConstArrayRef<char> raw) { set_raw(docid, raw); return true; }
    bool append(DocId docid, vespalib::ConstArrayRef<char> raw, int32_t weight) {
        (void) docid;
        (void) raw;
        (void) weight;
        return false;
    }
    bool isUndefined(DocId docid) const override;
    uint32_t clearDoc(DocId docId) override;
    std::unique_ptr<attribute::SearchContext> getSearch(std::unique_ptr<QueryTermSimple>, const attribute::SearchContextParams&) const override;
};

}

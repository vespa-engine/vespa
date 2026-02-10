// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "not_implemented_attribute.h"
#include "raw_buffer_store.h"
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace search::attribute {

/**
 * Attribute vector storing an array of bool values per document,
 * using bit-packed storage (8 bools per byte).
 *
 * Storage format per document in raw store:
 *   [padding_byte, packed_data_bytes...]
 * where padding_byte = number of unused bits in the last data byte (0-7).
 *
 * Values are set directly per document (no change vector), similar to
 * SingleRawAttribute and tensor attributes.
 */
class ArrayBoolAttribute : public NotImplementedAttribute,
                           public IMultiValueAttribute
{
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using EntryRef = vespalib::datastore::EntryRef;
    using RefVector = vespalib::RcuVectorBase<AtomicEntryRef>;

    RefVector        _ref_vector;
    RawBufferStore   _raw_store;

    vespalib::MemoryUsage update_stat();
    EntryRef acquire_entry_ref(DocId docid) const noexcept { return _ref_vector.acquire_elem_ref(docid).load_acquire(); }

    bool onLoad(vespalib::Executor* executor) override;
    std::unique_ptr<AttributeSaver> onInitSave(std::string_view fileName) override;
    void populate_address_space_usage(AddressSpaceUsage& usage) const override;

public:
    ArrayBoolAttribute(const std::string& name, const Config& config);
    ~ArrayBoolAttribute() override;

    vespalib::BitSpan get_bools(DocId docid) const;
    void set_bools(DocId docid, std::span<const int8_t> bools);

    // AttributeVector overrides
    bool addDoc(DocId& docId) override;
    void onCommit() override;
    void onUpdateStat(CommitParam::UpdateStats updateStats) override;
    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;
    uint32_t clearDoc(DocId docId) override;
    void onAddDocs(DocId lidLimit) override;
    void onShrinkLidSpace() override;

    // Value access
    uint32_t getValueCount(DocId doc) const override;
    largeint_t getInt(DocId doc) const override;
    double getFloat(DocId doc) const override;
    uint32_t get(DocId doc, largeint_t* v, uint32_t sz) const override;
    uint32_t get(DocId doc, double* v, uint32_t sz) const override;
    uint32_t get(DocId doc, EnumHandle* e, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedInt* v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedFloat* v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedEnum* v, uint32_t sz) const override;

    // Search
    std::unique_ptr<SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams& params) const override;

    // IMultiValueAttribute
    const IMultiValueAttribute* as_multi_value_attribute() const override;
    const IArrayBoolReadView* make_read_view(ArrayBoolTag, vespalib::Stash&) const override;
};

}

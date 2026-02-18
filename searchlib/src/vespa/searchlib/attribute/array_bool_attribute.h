// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"
#include "raw_buffer_store.h"
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchlib/attribute/search_context.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <cstring>

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
class ArrayBoolAttribute : public AttributeVector,
                           public IMultiValueAttribute
{
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using EntryRef = vespalib::datastore::EntryRef;
    using RefVector = vespalib::RcuVectorBase<AtomicEntryRef>;

    RefVector        _ref_vector;
    RawBufferStore   _raw_store;
    uint64_t         _total_values;

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
    uint64_t getTotalValueCount() const override;
    uint64_t getEstimatedSaveByteSize() const override;

    // Value access
    uint32_t getValueCount(DocId doc) const override;
    largeint_t getInt(DocId doc) const override;
    double getFloat(DocId doc) const override;
    std::span<const char> get_raw(DocId doc) const override;
    uint32_t get(DocId doc, largeint_t* v, uint32_t sz) const override;
    uint32_t get(DocId doc, double* v, uint32_t sz) const override;
    uint32_t get(DocId doc, std::string* v, uint32_t sz) const override;
    uint32_t get(DocId doc, const char** v, uint32_t sz) const override;
    uint32_t get(DocId doc, EnumHandle* e, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedInt* v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedFloat* v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedString* v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedConstChar* v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedEnum* v, uint32_t sz) const override;
    uint32_t getEnum(DocId doc) const override;
    bool is_sortable() const noexcept override;
    std::unique_ptr<attribute::ISortBlobWriter> make_sort_blob_writer(bool ascending, const common::BlobConverter* converter,
                                                                      common::sortspec::MissingPolicy policy,
                                                                      std::string_view missing_value) const override;

    // Search
    std::unique_ptr<SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams& params) const override;

    // IMultiValueAttribute
    const IMultiValueAttribute* as_multi_value_attribute() const override;
    const IArrayBoolReadView* make_read_view(ArrayBoolTag, vespalib::Stash&) const override;
};

class ArrayBoolSearchContext : public SearchContext {
    const ArrayBoolAttribute& _attr;
    bool _want_true;
    bool _valid;

    bool valid() const override { return _valid; }

    int32_t onFind(DocId docId, int32_t elemId, int32_t& weight) const override {
        int32_t result = onFind(docId, elemId);
        weight = (result >= 0) ? 1 : 0;
        return result;
    }

    int32_t onFind(DocId docId, int32_t elemId) const override {
        auto bools = _attr.get_bools(docId);
        for (uint32_t i = static_cast<uint32_t>(elemId); i < bools.size(); ++i) {
            if (bools[i] == _want_true) {
                return static_cast<int32_t>(i);
            }
        }
        return -1;
    }

public:
    ArrayBoolSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const ArrayBoolAttribute& attr)
        : SearchContext(attr),
          _attr(attr),
          _want_true(true),
          _valid(qTerm->isValid())
    {
        if ((strcmp("0", qTerm->getTerm()) == 0) || (strcasecmp("false", qTerm->getTerm()) == 0)) {
            _want_true = false;
        } else if ((strcmp("1", qTerm->getTerm()) != 0) && (strcasecmp("true", qTerm->getTerm()) != 0)) {
            _valid = false;
        }
    }

    HitEstimate calc_hit_estimate() const override {
        return _valid ? HitEstimate(_attr.getCommittedDocIdLimit()) : HitEstimate(0);
    }

    uint32_t get_committed_docid_limit() const noexcept override {
        return _attr.getCommittedDocIdLimit();
    }

    const ArrayBoolSearchContext* as_array_bool_search_context() const override { return this; }

    const ArrayBoolAttribute& get_attribute() const { return _attr; }
    bool get_want_true() const { return _want_true; }
    bool get_valid() const { return _valid; }
};

}

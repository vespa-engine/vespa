// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_bool_attribute.h"
#include "address_space_components.h"
#include "search_context.h"
#include "single_raw_attribute_loader.h"
#include "single_raw_attribute_saver.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/vespalib/util/stash.h>
#include <cassert>

using vespalib::datastore::EntryRef;

namespace search::attribute {

using largeint_t = IAttributeVector::largeint_t;

namespace {

std::span<const char> pack_bools(std::span<const int8_t> bools, std::vector<char>& buf) {
    uint32_t count = bools.size();
    if (count == 0) {
        return {};
    }
    buf.assign(1 + (count + 7) / 8, 0);
    buf[0] = static_cast<char>((8 - count % 8) % 8);
    for (uint32_t i = 0; i < count; ++i) {
        if (bools[i]) {
            buf[1 + i / 8] |= static_cast<char>(1 << (i % 8));
        }
    }
    return {buf.data(), buf.size()};
}

vespalib::BitSpan decode_bools(std::span<const char> raw) noexcept {
    if (raw.size() <= 1) {
        return {};
    }
    uint32_t padding = static_cast<uint8_t>(raw[0]) & 7;
    uint32_t count = (raw.size() - 1) * 8 - padding;
    return vespalib::BitSpan(raw.data() + 1, count);
}

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
};

class ArrayBoolReadView : public IArrayBoolReadView {
    const vespalib::RcuVectorBase<vespalib::datastore::AtomicEntryRef>& _ref_vector;
    const RawBufferStore& _raw_store;
    uint32_t _committed_doc_id_limit;
public:
    ArrayBoolReadView(const vespalib::RcuVectorBase<vespalib::datastore::AtomicEntryRef>& ref_vector,
                      const RawBufferStore& raw_store,
                      uint32_t committed_doc_id_limit)
        : _ref_vector(ref_vector),
          _raw_store(raw_store),
          _committed_doc_id_limit(committed_doc_id_limit)
    {}

    vespalib::BitSpan get_values(uint32_t docid) const override {
        if (docid >= _committed_doc_id_limit) {
            return {};
        }
        EntryRef ref = _ref_vector.acquire_elem_ref(docid).load_acquire();
        if (!ref.valid()) {
            return {};
        }
        return decode_bools(_raw_store.get(ref));
    }
};

} // anonymous namespace

ArrayBoolAttribute::ArrayBoolAttribute(const std::string& name, const Config& config)
    : AttributeVector(name, config),
      _ref_vector(config.getGrowStrategy(), getGenerationHolder()),
      _raw_store(get_memory_allocator(), RawBufferStore::array_store_max_type_id, RawBufferStore::array_store_grow_factor),
      _total_values(0)
{
}

ArrayBoolAttribute::~ArrayBoolAttribute()
{
    getGenerationHolder().reclaim_all();
}

vespalib::BitSpan
ArrayBoolAttribute::get_bools(DocId docid) const
{
    EntryRef ref;
    if (docid < getCommittedDocIdLimit()) {
        ref = acquire_entry_ref(docid);
    }
    if (!ref.valid()) {
        return {};
    }
    return decode_bools(_raw_store.get(ref));
}

void
ArrayBoolAttribute::set_bools(DocId docid, std::span<const int8_t> bools)
{
    std::vector<char> buf;
    auto packed = pack_bools(bools, buf);
    EntryRef ref;
    if (!packed.empty()) {
        ref = _raw_store.set(packed);
    }
    assert(docid < _ref_vector.size());
    updateUncommittedDocIdLimit(docid);
    auto& elem_ref = _ref_vector[docid];
    EntryRef old_ref(elem_ref.load_relaxed());
    size_t old_count = old_ref.valid() ? decode_bools(_raw_store.get(old_ref)).size() : 0;
    elem_ref.store_release(ref);
    _total_values += bools.size() - old_count;
    if (old_ref.valid()) {
        _raw_store.remove(old_ref);
    }
}

bool
ArrayBoolAttribute::addDoc(DocId& docId)
{
    bool incGen = _ref_vector.isFull();
    _ref_vector.push_back(AtomicEntryRef());
    AttributeVector::incNumDocs();
    docId = AttributeVector::getNumDocs() - 1;
    updateUncommittedDocIdLimit(docId);
    if (incGen) {
        incGeneration();
    } else {
        reclaim_unused_memory();
    }
    return true;
}

void
ArrayBoolAttribute::onCommit()
{
    incGeneration();
    if (_raw_store.consider_compact()) {
        auto context = _raw_store.start_compact(getConfig().getCompactionStrategy());
        if (context) {
            context->compact(std::span<AtomicEntryRef>(&_ref_vector[0], _ref_vector.size()));
        }
        incGeneration();
        updateStat(CommitParam::UpdateStats::FORCE);
    }
}

void
ArrayBoolAttribute::onUpdateStat(CommitParam::UpdateStats updateStats)
{
    if (updateStats == CommitParam::UpdateStats::SKIP) {
        return;
    }
    if (updateStats == CommitParam::UpdateStats::SIZES_ONLY) {
        this->updateSizes(_total_values, _total_values);
        return;
    }
    vespalib::MemoryUsage total = update_stat();
    this->updateStatistics(_total_values,
                           _total_values,
                           total.allocatedBytes(),
                           total.usedBytes(),
                           total.deadBytes(),
                           total.allocatedBytesOnHold());
}

vespalib::MemoryUsage
ArrayBoolAttribute::update_stat()
{
    vespalib::MemoryUsage result = _ref_vector.getMemoryUsage();
    result.merge(_raw_store.update_stat(getConfig().getCompactionStrategy()));
    result.mergeGenerationHeldBytes(getGenerationHolder().get_held_bytes());
    return result;
}

void
ArrayBoolAttribute::reclaim_memory(generation_t oldest_used_gen)
{
    _raw_store.reclaim_memory(oldest_used_gen);
    getGenerationHolder().reclaim(oldest_used_gen);
}

void
ArrayBoolAttribute::before_inc_generation(generation_t current_gen)
{
    getGenerationHolder().assign_generation(current_gen);
    _raw_store.assign_generation(current_gen);
}

uint32_t
ArrayBoolAttribute::clearDoc(DocId docId)
{
    updateUncommittedDocIdLimit(docId);
    auto& elem_ref = _ref_vector[docId];
    EntryRef old_ref(elem_ref.load_relaxed());
    elem_ref.store_relaxed(EntryRef());
    if (old_ref.valid()) {
        uint32_t old_count = decode_bools(_raw_store.get(old_ref)).size();
        _total_values -= old_count;
        _raw_store.remove(old_ref);
        return old_count;
    }
    return 0u;
}

void
ArrayBoolAttribute::onAddDocs(DocId) { }

void
ArrayBoolAttribute::onShrinkLidSpace()
{
    uint32_t committed_doc_id_limit = getCommittedDocIdLimit();
    assert(committed_doc_id_limit < getNumDocs());
    _ref_vector.shrink(committed_doc_id_limit);
    setNumDocs(committed_doc_id_limit);
}

uint32_t
ArrayBoolAttribute::getValueCount(DocId doc) const
{
    return get_bools(doc).size();
}

largeint_t
ArrayBoolAttribute::getInt(DocId doc) const
{
    auto bools = get_bools(doc);
    return (bools.size() > 0 && bools[0]) ? 1 : 0;
}

double
ArrayBoolAttribute::getFloat(DocId doc) const
{
    return static_cast<double>(getInt(doc));
}

std::span<const char>
ArrayBoolAttribute::get_raw(DocId) const
{
    return {};
}

uint32_t
ArrayBoolAttribute::get(DocId doc, largeint_t* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = bools[i] ? 1 : 0;
    }
    return bools.size();
}

uint32_t
ArrayBoolAttribute::get(DocId doc, double* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = bools[i] ? 1.0 : 0.0;
    }
    return bools.size();
}

uint32_t
ArrayBoolAttribute::get(DocId doc, std::string* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = bools[i] ? "1" : "0";
    }
    return bools.size();
}

uint32_t
ArrayBoolAttribute::get(DocId, const char**, uint32_t) const
{
    return 0;
}

uint32_t
ArrayBoolAttribute::get(DocId, EnumHandle*, uint32_t) const
{
    return 0;
}

uint32_t
ArrayBoolAttribute::get(DocId doc, WeightedInt* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = WeightedInt(bools[i] ? 1 : 0);
    }
    return bools.size();
}

uint32_t
ArrayBoolAttribute::get(DocId doc, WeightedFloat* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = WeightedFloat(bools[i] ? 1.0 : 0.0);
    }
    return bools.size();
}

uint32_t
ArrayBoolAttribute::get(DocId doc, WeightedString* v, uint32_t sz) const
{
    auto bools = get_bools(doc);
    uint32_t n = std::min(bools.size(), sz);
    for (uint32_t i = 0; i < n; ++i) {
        v[i] = WeightedString(bools[i] ? "1" : "0");
    }
    return bools.size();
}

uint32_t
ArrayBoolAttribute::get(DocId, WeightedConstChar*, uint32_t) const
{
    return 0;
}

uint32_t
ArrayBoolAttribute::get(DocId, WeightedEnum*, uint32_t) const
{
    return 0;
}

std::unique_ptr<attribute::SearchContext>
ArrayBoolAttribute::getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams&) const
{
    return std::make_unique<ArrayBoolSearchContext>(std::move(term), *this);
}

const IMultiValueAttribute*
ArrayBoolAttribute::as_multi_value_attribute() const
{
    return this;
}

const IArrayBoolReadView*
ArrayBoolAttribute::make_read_view(ArrayBoolTag, vespalib::Stash& stash) const
{
    return &stash.create<ArrayBoolReadView>(_ref_vector, _raw_store, getCommittedDocIdLimit());
}

std::unique_ptr<AttributeSaver>
ArrayBoolAttribute::onInitSave(std::string_view fileName)
{
    vespalib::GenerationHandler::Guard guard(getGenerationHandler().takeGuard());
    return std::make_unique<SingleRawAttributeSaver>
        (std::move(guard),
         this->createAttributeHeader(fileName),
         make_entry_ref_vector_snapshot(_ref_vector, getCommittedDocIdLimit()),
         _raw_store);
}

bool
ArrayBoolAttribute::onLoad(vespalib::Executor* executor)
{
    SingleRawAttributeLoader loader(*this, _ref_vector, _raw_store);
    if (!loader.on_load(executor)) {
        return false;
    }
    uint64_t total = 0;
    uint32_t doc_id_limit = getCommittedDocIdLimit();
    for (uint32_t docid = 0; docid < doc_id_limit; ++docid) {
        EntryRef ref = _ref_vector.acquire_elem_ref(docid).load_acquire();
        if (ref.valid()) {
            total += decode_bools(_raw_store.get(ref)).size();
        }
    }
    _total_values = total;
    return true;
}

void
ArrayBoolAttribute::populate_address_space_usage(AddressSpaceUsage& usage) const
{
    usage.set(AddressSpaceComponents::raw_store, _raw_store.get_address_space_usage());
}

uint64_t
ArrayBoolAttribute::getTotalValueCount() const
{
    return _total_values;
}

uint64_t
ArrayBoolAttribute::getEstimatedSaveByteSize() const
{
    uint64_t headerSize = FileSettings::DIRECTIO_ALIGNMENT;
    uint64_t numDocs = getCommittedDocIdLimit();
    uint64_t totalBits = _total_values;
    return headerSize + (totalBits + 7) / 8 + numDocs * 5;
}

uint32_t
ArrayBoolAttribute::getEnum(DocId) const
{
    return std::numeric_limits<uint32_t>::max();
}

bool
ArrayBoolAttribute::is_sortable() const noexcept
{
    return false;
}

std::unique_ptr<attribute::ISortBlobWriter>
ArrayBoolAttribute::make_sort_blob_writer(bool, const common::BlobConverter*,
                                          common::sortspec::MissingPolicy,
                                          std::string_view) const
{
    assert(false && "ArrayBoolAttribute is not sortable");
    return {};
}

}

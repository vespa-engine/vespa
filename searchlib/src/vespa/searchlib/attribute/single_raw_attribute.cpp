// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_raw_attribute.h"
#include "single_raw_attribute_loader.h"
#include "single_raw_attribute_saver.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/vespalib/datastore/array_store.hpp>

using vespalib::alloc::MemoryAllocator;
using vespalib::datastore::EntryRef;

namespace search::attribute {

SingleRawAttribute::SingleRawAttribute(const std::string& name, const Config& config)
    : RawAttribute(name, config),
      _ref_vector(config.getGrowStrategy(), getGenerationHolder()),
      _raw_store(get_memory_allocator(), RawBufferStore::array_store_max_type_id, RawBufferStore::array_store_grow_factor)
{
}

SingleRawAttribute::~SingleRawAttribute()
{
    getGenerationHolder().reclaim_all();
}

void
SingleRawAttribute::reclaim_memory(generation_t oldest_used_gen)
{
    _raw_store.reclaim_memory(oldest_used_gen);
    getGenerationHolder().reclaim(oldest_used_gen);
}

void
SingleRawAttribute::before_inc_generation(generation_t current_gen)
{
    getGenerationHolder().assign_generation(current_gen);
    _raw_store.assign_generation(current_gen);
}

bool
SingleRawAttribute::addDoc(DocId &docId)
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
SingleRawAttribute::onCommit()
{
    incGeneration();
    if (_raw_store.consider_compact()) {
        auto context = _raw_store.start_compact(getConfig().getCompactionStrategy());
        if (context) {
            context->compact(std::span<AtomicEntryRef>(&_ref_vector[0], _ref_vector.size()));
        }
        incGeneration();
        updateStat(true);
    }
}

void
SingleRawAttribute::onUpdateStat()
{
    vespalib::MemoryUsage total = update_stat();
    this->updateStatistics(_ref_vector.size(),
                           _ref_vector.size(),
                           total.allocatedBytes(),
                           total.usedBytes(),
                           total.deadBytes(),
                           total.allocatedBytesOnHold());
}

vespalib::MemoryUsage
SingleRawAttribute::update_stat()
{
    vespalib::MemoryUsage result = _ref_vector.getMemoryUsage();
    result.merge(_raw_store.update_stat(getConfig().getCompactionStrategy()));
    result.mergeGenerationHeldBytes(getGenerationHolder().get_held_bytes());
    return result;
}

std::span<const char>
SingleRawAttribute::get_raw(DocId docid) const
{
    EntryRef ref;
    if (docid < getCommittedDocIdLimit()) {
        ref = acquire_entry_ref(docid);
    }
    if (!ref.valid()) {
        return {};
    }
    return _raw_store.get(ref);
}

void
SingleRawAttribute::set_raw(DocId docid, std::span<const char> raw)
{
    auto ref = _raw_store.set(raw);
    assert(docid < _ref_vector.size());
    updateUncommittedDocIdLimit(docid);
    auto& elem_ref = _ref_vector[docid];
    EntryRef old_ref(elem_ref.load_relaxed());
    elem_ref.store_release(ref);
    if (old_ref.valid()) {
        _raw_store.remove(old_ref);
    }
}

uint32_t
SingleRawAttribute::clearDoc(DocId docId)
{
    updateUncommittedDocIdLimit(docId);
    auto& elem_ref = _ref_vector[docId];
    EntryRef old_ref(elem_ref.load_relaxed());
    elem_ref.store_relaxed(EntryRef());
    if (old_ref.valid()) {
        _raw_store.remove(old_ref);
        return 1u;
    }
    return 0u;
}

std::unique_ptr<AttributeSaver>
SingleRawAttribute::onInitSave(std::string_view fileName)
{
    vespalib::GenerationHandler::Guard guard(getGenerationHandler().takeGuard());
    return std::make_unique<SingleRawAttributeSaver>
        (std::move(guard),
         this->createAttributeHeader(fileName),
         make_entry_ref_vector_snapshot(_ref_vector, getCommittedDocIdLimit()),
         _raw_store);
}

bool
SingleRawAttribute::onLoad(vespalib::Executor* executor)
{
    SingleRawAttributeLoader loader(*this, _ref_vector, _raw_store);
    return loader.on_load(executor);
}

bool
SingleRawAttribute::isUndefined(DocId docid) const
{
    auto raw = get_raw(docid);
    return raw.empty();
}

void
SingleRawAttribute::populate_address_space_usage(AddressSpaceUsage& usage) const
{
    usage.set(AddressSpaceComponents::raw_store, _raw_store.get_address_space_usage());
}

}

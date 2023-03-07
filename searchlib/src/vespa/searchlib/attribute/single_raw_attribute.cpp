// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_raw_attribute.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/datastore/array_store.hpp>

using vespalib::alloc::MemoryAllocator;
using vespalib::datastore::EntryRef;

namespace {

constexpr float ALLOC_GROW_FACTOR = 0.2;

}

namespace search::attribute {

SingleRawAttribute::SingleRawAttribute(const vespalib::string& name, const Config& config)
    : NotImplementedAttribute(name, config),
      _ref_vector(config.getGrowStrategy(), getGenerationHolder()),
      _array_store(ArrayStoreType::optimizedConfigForHugePage(1000u,
                                                              MemoryAllocator::HUGEPAGE_SIZE,
                                                              MemoryAllocator::PAGE_SIZE,
                                                              8_Ki, ALLOC_GROW_FACTOR),
                   get_memory_allocator())
{
}

SingleRawAttribute::~SingleRawAttribute()
{
    getGenerationHolder().reclaim_all();
}

void
SingleRawAttribute::reclaim_memory(generation_t oldest_used_gen)
{
    _array_store.reclaim_memory(oldest_used_gen);
    getGenerationHolder().reclaim(oldest_used_gen);
}

void
SingleRawAttribute::before_inc_generation(generation_t current_gen)
{
    getGenerationHolder().assign_generation(current_gen);
    _array_store.assign_generation(current_gen);
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
    if (_array_store.consider_compact()) {
        auto context = _array_store.compact_worst(getConfig().getCompactionStrategy());
        if (context) {
            context->compact(vespalib::ArrayRef<AtomicEntryRef>(&_ref_vector[0], _ref_vector.size()));
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
    result.merge(_array_store.update_stat(getConfig().getCompactionStrategy()));
    result.mergeGenerationHeldBytes(getGenerationHolder().get_held_bytes());
    return result;
}

vespalib::ConstArrayRef<char>
SingleRawAttribute::get_raw(EntryRef ref) const
{
    auto array = _array_store.get(ref);
    uint32_t size = 0;
    assert(array.size() >= sizeof(size));
    memcpy(&size, array.data(), sizeof(size));
    assert(array.size() >= sizeof(size) + size);
    return {array.data() + sizeof(size), size};
}

vespalib::ConstArrayRef<char>
SingleRawAttribute::get_raw(DocId docid) const
{
    EntryRef ref;
    if (docid < getCommittedDocIdLimit()) {
        ref = acquire_entry_ref(docid);
    }
    if (!ref.valid()) {
        return {};
    }
    return get_raw(ref);
}

EntryRef
SingleRawAttribute::set_raw(vespalib::ConstArrayRef<char> raw)
{
    uint32_t size = raw.size();
    size_t buffer_size = raw.size() + sizeof(size);
    auto& mapper = _array_store.get_mapper();
    auto type_id = mapper.get_type_id(buffer_size);
    auto array_size = (type_id != 0) ? mapper.get_array_size(type_id) : buffer_size;
    assert(array_size >= buffer_size);
    auto ref = _array_store.allocate(array_size);
    auto buf = _array_store.get_writable(ref);
    memcpy(buf.data(), &size, sizeof(size));
    memcpy(buf.data() + sizeof(size), raw.data(), size);
    if (array_size > buffer_size) {
        memset(buf.data() + buffer_size, 0, array_size - buffer_size);
    }
    return ref;
}

void
SingleRawAttribute::set_raw(DocId docid, vespalib::ConstArrayRef<char> raw)
{
    auto ref = set_raw(raw);
    assert(docid < _ref_vector.size());
    updateUncommittedDocIdLimit(docid);
    auto& elem_ref = _ref_vector[docid];
    EntryRef old_ref(elem_ref.load_relaxed());
    elem_ref.store_release(ref);
    if (old_ref.valid()) {
        _array_store.remove(old_ref);
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
        _array_store.remove(old_ref);
        return 1u;
    }
    return 0u;
}

long
SingleRawAttribute::onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const
{
    auto raw = get_raw(doc);
    vespalib::ConstBufferRef buf(raw.data(), raw.size());
    if (bc != nullptr) {
        buf = bc->convert(buf);
    }
    if (available >= (long)buf.size()) {
        memcpy(serTo, buf.data(), buf.size());
    } else {
        return -1;
    }
    return buf.size();
}

long
SingleRawAttribute::onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const
{
    auto raw = get_raw(doc);
    vespalib::ConstBufferRef buf(raw.data(), raw.size());
    if (bc != nullptr) {
        buf = bc->convert(buf);
    }
    if (available >= (long)buf.size()) {
        auto *dst = static_cast<unsigned char *>(serTo);
        const auto * src(static_cast<const uint8_t *>(buf.data()));
        for (size_t i(0); i < buf.size(); ++i) {
            dst[i] = 0xff - src[i];
        }
    } else {
        return -1;
    }
    return buf.size();
}

}

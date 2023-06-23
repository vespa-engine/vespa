// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multivalueattribute.h"
#include "address_space_components.h"
#include "raw_multi_value_read_view.h"
#include "copy_multi_value_read_view.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/util/stash.h>

namespace search {

namespace multivalueattribute {

constexpr bool enable_free_lists = true;

}

template <typename B, typename M>
MultiValueAttribute<B, M>::
MultiValueAttribute(const vespalib::string &baseFileName,
                    const AttributeVector::Config &cfg)
    : B(baseFileName, cfg),
      _mvMapping(MultiValueMapping::optimizedConfigForHugePage(MultiValueMapping::array_store_max_type_id,
                                                               vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                               vespalib::alloc::MemoryAllocator::PAGE_SIZE,
                                                               vespalib::datastore::ArrayStoreConfig::default_max_buffer_size,
                                                               8 * 1024,
                                                               cfg.getGrowStrategy().getMultiValueAllocGrowFactor(),
                                                               multivalueattribute::enable_free_lists),
                 cfg.getGrowStrategy(), this->get_memory_allocator())
{
}

template <typename B, typename M>
MultiValueAttribute<B, M>::~MultiValueAttribute() = default;

template <typename B, typename M>
int32_t MultiValueAttribute<B, M>::getWeight(DocId doc, uint32_t idx) const
{
    MultiValueArrayRef values(this->_mvMapping.get(doc));
    return ((idx < values.size()) ? multivalue::get_weight(values[idx]) : 1);
}

namespace {

template <typename T>
struct HashFn {
    using type = vespalib::hash<T>;
};

template <>
struct HashFn<vespalib::datastore::EntryRef> {
    struct EntryRefHasher {
        size_t operator() (const vespalib::datastore::EntryRef& v) const noexcept {
            return v.ref();
        }
    };
    using type = EntryRefHasher;
};

}

template <typename B, typename M>
void
MultiValueAttribute<B, M>::applyAttributeChanges(DocumentValues & docValues)
{
    if (this->hasArrayType()) {
        apply_attribute_changes_to_array(docValues);
        return;
    } else if (this->hasWeightedSetType()) {
        apply_attribute_changes_to_wset(docValues);
        return;
    }
}

template <typename B, typename M>
void
MultiValueAttribute<B, M>::apply_attribute_changes_to_array(DocumentValues& docValues)
{
    // compute new values for each document with changes
    auto iterable = this->_changes.getDocIdInsertOrder();
    for (auto current(iterable.begin()), end(iterable.end()); (current != end); ) {
        DocId doc = current->_doc;
        // find last clear doc
        auto last_clear_doc = end;
        for (auto iter = current; (iter != end) && (iter->_doc == doc); ++iter) {
            if (iter->_type == ChangeBase::CLEARDOC) {
                last_clear_doc = iter;
            }
        }
        // use last clear doc if found
        if (last_clear_doc != end) {
            current = last_clear_doc;
        }
        MultiValueArrayRef old_values(_mvMapping.get(doc));
        ValueVector new_values(old_values.cbegin(), old_values.cend());
        vespalib::hash_map<NonAtomicValueType, size_t, typename HashFn<NonAtomicValueType>::type> tombstones;

        // iterate through all changes for this document
        for (; (current != end) && (current->_doc == doc); ++current) {
            if (current->_type == ChangeBase::CLEARDOC) {
                new_values.clear();
                tombstones.clear();
                continue;
            }
            NonAtomicValueType data;
            bool hasData = extractChangeData(*current, data);
            if (!hasData) {
                continue;
            }
            if (current->_type == ChangeBase::APPEND) {
                if constexpr (std::is_same_v<ValueType, NonAtomicValueType>) {
                    new_values.emplace_back(multivalue::ValueBuilder<MultiValueType>::build(data, current->_weight));
                } else {
                    new_values.emplace_back(multivalue::ValueBuilder<MultiValueType>::build(ValueType(data), current->_weight));
                }
            } else if (current->_type == ChangeBase::REMOVE) {
                // Defer all removals to the very end by tracking when, during value vector build time,
                // a removal was encountered for a particular value. All values < this index will be ignored.
                tombstones[data] = new_values.size();
            }
        }
        // Optimize for the case where nothing was explicitly removed.
        if (!tombstones.empty()) {
            ValueVector culled;
            culled.reserve(new_values.size());
            if constexpr (std::is_same_v<ValueType, NonAtomicValueType>) {
                for (size_t i = 0; i < new_values.size(); ++i) {
                    auto iter = tombstones.find(multivalue::get_value(new_values[i]));
                    if (iter == tombstones.end() || (iter->second <= i)) {
                        culled.emplace_back(new_values[i]);
                    }
                }
            } else {
                for (size_t i = 0; i < new_values.size(); ++i) {
                    auto iter = tombstones.find(multivalue::get_value_ref(new_values[i]).load_relaxed());
                    if (iter == tombstones.end() || (iter->second <= i)) {
                        culled.emplace_back(new_values[i]);
                    }
                }
            }
            culled.swap(new_values);
        }
        this->checkSetMaxValueCount(new_values.size());
        docValues.emplace_back(doc, std::move(new_values));
    }
}

template <typename B, typename M>
void
MultiValueAttribute<B, M>::apply_attribute_changes_to_wset(DocumentValues& docValues)
{
    // compute new values for each document with changes
    auto iterable = this->_changes.getDocIdInsertOrder();
    for (auto current(iterable.begin()), end(iterable.end()); (current != end); ) {
        const DocId doc = current->_doc;
        // find last clear doc
        auto last_clear_doc = end;
        size_t max_elems_inserted = 0;
        for (auto iter = current; (iter != end) && (iter->_doc == doc); ++iter) {
            if (iter->_type == ChangeBase::CLEARDOC) {
                last_clear_doc = iter;
            }
            ++max_elems_inserted;
        }
        // use last clear doc if found
        if (last_clear_doc != end) {
            current = last_clear_doc;
        }
        MultiValueArrayRef old_values(_mvMapping.get(doc));
        vespalib::hash_map<NonAtomicValueType, int32_t, typename HashFn<NonAtomicValueType>::type> wset_inserted;
        wset_inserted.resize((old_values.size() + max_elems_inserted) * 2);
        for (const auto& e : old_values) {
            if constexpr (std::is_same_v<ValueType, NonAtomicValueType>) {
                wset_inserted[multivalue::get_value(e)] = multivalue::get_weight(e);
            } else {
                wset_inserted[multivalue::get_value_ref(e).load_relaxed()] = multivalue::get_weight(e);
            }
        }
        // iterate through all changes for this document
        for (; (current != end) && (current->_doc == doc); ++current) {
            if (current->_type == ChangeBase::CLEARDOC) {
                wset_inserted.clear();
                continue;
            }
            NonAtomicValueType data;
            bool hasData = extractChangeData(*current, data);
            if (!hasData) {
                continue;
            }
            if (current->_type == ChangeBase::APPEND) {
                wset_inserted[data] = current->_weight;
            } else if (current->_type == ChangeBase::REMOVE) {
                wset_inserted.erase(data);
            } else if ((current->_type >= ChangeBase::INCREASEWEIGHT) && (current->_type <= ChangeBase::SETWEIGHT)) {
                auto existing = wset_inserted.find(data);
                if (existing != wset_inserted.end()) {
                    existing->second = this->applyWeightChange(existing->second, *current);
                    if ((existing->second == 0) && this->getInternalCollectionType().removeIfZero()) {
                        wset_inserted.erase(existing);
                    }
                } else if (this->getInternalCollectionType().createIfNonExistant()) {
                    int32_t weight = this->applyWeightChange(0, *current);
                    if (weight != 0 || !this->getInternalCollectionType().removeIfZero()) {
                        wset_inserted.insert(std::make_pair(data, weight));
                    }
                }
            }
        }
        std::vector<MultiValueType> new_values;
        new_values.reserve(wset_inserted.size());
        if constexpr (std::is_same_v<ValueType, NonAtomicValueType>) {
            wset_inserted.for_each([&new_values](const auto& e){ new_values.emplace_back(multivalue::ValueBuilder<MultiValueType>::build(e.first, e.second)); });
        } else {
            wset_inserted.for_each([&new_values](const auto& e){ new_values.emplace_back(multivalue::ValueBuilder<MultiValueType>::build(ValueType(e.first), e.second)); });
        }

        this->checkSetMaxValueCount(new_values.size());
        docValues.emplace_back(doc, std::move(new_values));
    }
}

template <typename B, typename M>
void
MultiValueAttribute<B, M>::populate_address_space_usage(AddressSpaceUsage& usage) const
{
    B::populate_address_space_usage(usage);
    usage.set(AddressSpaceComponents::multi_value, _mvMapping.getAddressSpaceUsage());
}

template <typename B, typename M>
bool
MultiValueAttribute<B, M>::addDoc(DocId & doc)
{
    bool incGen = this->_mvMapping.isFull();
    this->_mvMapping.addDoc(doc);
    this->incNumDocs();
    this->updateUncommittedDocIdLimit(doc);
    incGen |= onAddDoc(doc);
    if (incGen) {
        this->incGeneration();
    } else
        this->reclaim_unused_memory();
    return true;
}

template <typename B, typename M>
void
MultiValueAttribute<B, M>::onAddDocs(DocId  lidLimit) {
    this->_mvMapping.reserve(lidLimit);
}


template <typename B, typename M>
uint32_t
MultiValueAttribute<B, M>::getValueCount(DocId doc) const
{
    if (doc >= this->getNumDocs()) {
        return 0;
    }
    MultiValueArrayRef values(this->_mvMapping.get(doc));
    return values.size();
}


template <typename B, typename M>
uint64_t
MultiValueAttribute<B, M>::getTotalValueCount() const
{
    return _mvMapping.getTotalValueCnt();
}


template <typename B, typename M>
void
MultiValueAttribute<B, M>::clearDocs(DocId lidLow, DocId lidLimit, bool)
{
    _mvMapping.clearDocs(lidLow, lidLimit, [this](uint32_t docId) { this->clearDoc(docId); });
}


template <typename B, typename M>
void
MultiValueAttribute<B, M>::onShrinkLidSpace()
{
    uint32_t committedDocIdLimit = this->getCommittedDocIdLimit();
    _mvMapping.shrink(committedDocIdLimit);
    this->setNumDocs(committedDocIdLimit);
}

template <typename B, typename M>
const attribute::IMultiValueAttribute*
MultiValueAttribute<B, M>::as_multi_value_attribute() const
{
    return this;
}

template <typename B, typename M>
const attribute::IArrayReadView<multivalue::ValueType_t<M>>*
MultiValueAttribute<B, M>::make_read_view(attribute::IMultiValueAttribute::ArrayTag<ValueType>, vespalib::Stash& stash) const
{
    if constexpr (std::is_same_v<MultiValueType, ValueType>) {
        return &stash.create<attribute::RawMultiValueReadView<MultiValueType>>(this->_mvMapping.make_read_view(this->getCommittedDocIdLimit()));
    } else {
        return &stash.create<attribute::CopyMultiValueReadView<ValueType, MultiValueType>>(this->_mvMapping.make_read_view(this->getCommittedDocIdLimit()));
    }
}

template <typename B, typename M>
const attribute::IWeightedSetReadView<multivalue::ValueType_t<M>>*
MultiValueAttribute<B, M>::make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<ValueType>, vespalib::Stash& stash) const
{
    if constexpr (std::is_same_v<MultiValueType, multivalue::WeightedValue<ValueType>>) {
        return &stash.create<attribute::RawMultiValueReadView<MultiValueType>>(this->_mvMapping.make_read_view(this->getCommittedDocIdLimit()));
    } else {
        return &stash.create<attribute::CopyMultiValueReadView<multivalue::WeightedValue<ValueType>, MultiValueType>>(this->_mvMapping.make_read_view(this->getCommittedDocIdLimit()));
    }
}

} // namespace search


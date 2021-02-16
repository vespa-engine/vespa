// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/multivalueattribute.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/memory_allocator.h>

namespace search {

namespace multivalueattribute {

constexpr size_t SMALL_MEMORY_PAGE_SIZE = 4 * 1024;
constexpr bool enable_free_lists = true;

}

template <typename B, typename M>
MultiValueAttribute<B, M>::
MultiValueAttribute(const vespalib::string &baseFileName,
                    const AttributeVector::Config &cfg)
    : B(baseFileName, cfg),
      _mvMapping(MultiValueMapping::optimizedConfigForHugePage(1023,
                                                               vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                               multivalueattribute::SMALL_MEMORY_PAGE_SIZE,
                                                               8 * 1024,
                                                               cfg.getGrowStrategy().getMultiValueAllocGrowFactor(),
                                                               multivalueattribute::enable_free_lists),
                 cfg.getGrowStrategy().to_generic_strategy())
{
}

template <typename B, typename M>
MultiValueAttribute<B, M>::~MultiValueAttribute() = default;

template <typename B, typename M>
int32_t MultiValueAttribute<B, M>::getWeight(DocId doc, uint32_t idx) const
{
    MultiValueArrayRef values(this->_mvMapping.get(doc));
    return ((idx < values.size()) ? values[idx].weight() : 1);
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
    for (ChangeVectorIterator current(this->_changes.begin()), end(this->_changes.end()); (current != end); ) {
        DocId doc = current->_doc;
        // find last clear doc
        ChangeVectorIterator last_clear_doc = end;
        for (ChangeVectorIterator iter = current; (iter != end) && (iter->_doc == doc); ++iter) {
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
        vespalib::hash_map<ValueType, size_t, typename HashFn<ValueType>::type> tombstones;

        // iterate through all changes for this document
        for (; (current != end) && (current->_doc == doc); ++current) {
            if (current->_type == ChangeBase::CLEARDOC) {
                new_values.clear();
                tombstones.clear();
                continue;
            }
            ValueType data;
            bool hasData = extractChangeData(*current, data);
            if (!hasData) {
                continue;
            }
            if (current->_type == ChangeBase::APPEND) {
                new_values.emplace_back(data, current->_weight);
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
            for (size_t i = 0; i < new_values.size(); ++i) {
                auto iter = tombstones.find(new_values[i].value());
                if (iter == tombstones.end() || (iter->second <= i)) {
                    culled.emplace_back(new_values[i]);
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
    for (ChangeVectorIterator current(this->_changes.begin()), end(this->_changes.end()); (current != end); ) {
        const DocId doc = current->_doc;
        // find last clear doc
        ChangeVectorIterator last_clear_doc = end;
        size_t max_elems_inserted = 0;
        for (ChangeVectorIterator iter = current; (iter != end) && (iter->_doc == doc); ++iter) {
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
        vespalib::hash_map<ValueType, int32_t, typename HashFn<ValueType>::type> wset_inserted;
        wset_inserted.resize((old_values.size() + max_elems_inserted) * 2);
        for (const auto& e : old_values) {
            wset_inserted[e.value()] = e.weight();
        }
        // iterate through all changes for this document
        for (; (current != end) && (current->_doc == doc); ++current) {
            if (current->_type == ChangeBase::CLEARDOC) {
                wset_inserted.clear();
                continue;
            }
            ValueType data;
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
        wset_inserted.for_each([&new_values](const auto& e){ new_values.emplace_back(e.first, e.second); });

        this->checkSetMaxValueCount(new_values.size());
        docValues.emplace_back(doc, std::move(new_values));
    }
}

template <typename B, typename M>
vespalib::AddressSpace
MultiValueAttribute<B, M>::getMultiValueAddressSpaceUsage() const
{
    return _mvMapping.getAddressSpaceUsage();
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
        this->removeAllOldGenerations();
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
MultiValueAttribute<B, M>::clearDocs(DocId lidLow, DocId lidLimit)
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


} // namespace search


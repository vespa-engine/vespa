// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/multivalueattribute.h>

namespace search {

namespace multivalueattribute {

constexpr size_t SMALL_MEMORY_PAGE_SIZE = 4 * 1024;

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
                                                               cfg.getGrowStrategy().getMultiValueAllocGrowFactor()),
                 cfg.getGrowStrategy())
{
}

template <typename B, typename M>
MultiValueAttribute<B, M>::~MultiValueAttribute()
{
}

template <typename B, typename M>
int32_t MultiValueAttribute<B, M>::getWeight(DocId doc, uint32_t idx) const
{
    MultiValueArrayRef values(this->_mvMapping.get(doc));
    return ((idx < values.size()) ? values[idx].weight() : 1);
}


template <typename B, typename M>
void
MultiValueAttribute<B, M>::applyAttributeChanges(DocumentValues & docValues)
{
    // compute new values for each document with changes
    for (ChangeVectorIterator current(this->_changes.begin()), end(this->_changes.end()); (current != end); ) {
        DocId doc = current->_doc;

        MultiValueArrayRef oldValues(_mvMapping.get(doc));
        ValueVector newValues(oldValues.cbegin(), oldValues.cend());

        // find last clear doc
        ChangeVectorIterator lastClearDoc = end;
        for (ChangeVectorIterator iter = current; (iter != end) && (iter->_doc == doc); ++iter) {
            if (iter->_type == ChangeBase::CLEARDOC) {
                lastClearDoc = iter;
            }
        }

        // use last clear doc if found
        if (lastClearDoc != end) {
            current = lastClearDoc;
        }

        // iterate through all changes for this document
        for (; (current != end) && (current->_doc == doc); ++current) {

            if (current->_type == ChangeBase::CLEARDOC) {
                newValues.clear();
                continue;
            }

            ValueType data;
            bool hasData = extractChangeData(*current, data);

            if (current->_type == ChangeBase::APPEND) {
                if (hasData) {
                    if (this->hasArrayType()) {
                        newValues.push_back(MultiValueType(data, current->_weight));
                    } else if (this->hasWeightedSetType()) {
                        ValueVectorIterator witer;
                        for (witer = newValues.begin(); witer != newValues.end(); ++witer) {
                            if (witer->value() == data) {
                                break;
                            }
                        }
                        if (witer != newValues.end()) {
                            witer->setWeight(current->_weight);
                        } else {
                            newValues.push_back(MultiValueType(data, current->_weight));
                        }
                    }
                }
            } else if (current->_type == ChangeBase::REMOVE) {
                if (hasData) {
                    for (ValueVectorIterator witer = newValues.begin(); witer != newValues.end(); ) {
                        if (witer->value() == data) {
                            witer = newValues.erase(witer);
                        } else {
                            ++witer;
                        }
                    }
                }
            } else if ((current->_type >= ChangeBase::INCREASEWEIGHT) && (current->_type <= ChangeBase::DIVWEIGHT)) {
                if (this->hasWeightedSetType() && hasData) {
                    ValueVectorIterator witer;
                    for (witer = newValues.begin(); witer != newValues.end(); ++witer) {
                        if (witer->value() == data) {
                            break;
                        }
                    }
                    if (witer != newValues.end()) {
                        witer->setWeight(this->applyWeightChange(witer->weight(), *current));
                        if (witer->weight() == 0 && this->getInternalCollectionType().removeIfZero()) {
                            newValues.erase(witer);
                        }
                    } else if (this->getInternalCollectionType().createIfNonExistant()) {
                        int32_t weight = this->applyWeightChange(0, *current);
                        if (weight != 0 || !this->getInternalCollectionType().removeIfZero()) {
                            newValues.push_back(MultiValueType(data, weight));
                        }
                    }
                }
            }
        }
        this->checkSetMaxValueCount(newValues.size());

        docValues.push_back(std::make_pair(doc, ValueVector()));
        docValues.back().second.swap(newValues);
    }
}


template <typename B, typename M>
AddressSpace
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
    _mvMapping.clearDocs(lidLow, lidLimit, [=](uint32_t docId) { this->clearDoc(docId); });
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


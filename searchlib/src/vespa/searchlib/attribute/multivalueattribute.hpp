// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/multivalueattribute.h>

namespace search {

template <typename B, typename M>
MultiValueAttribute<B, M>::
MultiValueAttribute(const vespalib::string &baseFileName,
                    const AttributeVector::Config &cfg)
    : B(baseFileName, cfg),
      _mvMapping(this->getCommittedDocIdLimitRef(), cfg.getGrowStrategy())
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
    Histogram capacityNeeded = _mvMapping.getEmptyHistogram();

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

        // update histogram
        uint32_t maxValues = MultiValueMapping::maxValues();
        if (newValues.size() < maxValues) {
            capacityNeeded[newValues.size()] += 1;
        } else {
            capacityNeeded[maxValues] += 1;
        }

        this->checkSetMaxValueCount(newValues.size());

        docValues.push_back(std::make_pair(doc, ValueVector()));
        docValues.back().second.swap(newValues);
    }

    if (!_mvMapping.enoughCapacity(capacityNeeded)) {
        this->removeAllOldGenerations();
        _mvMapping.performCompaction(capacityNeeded);
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
MultiValueAttribute<B, M>::getTotalValueCount(void) const
{
    return _mvMapping.getTotalValueCnt();
}


template <typename B, typename M>
void
MultiValueAttribute<B, M>::clearDocs(DocId lidLow, DocId lidLimit)
{
    _mvMapping.clearDocs(lidLow, lidLimit, *this);
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


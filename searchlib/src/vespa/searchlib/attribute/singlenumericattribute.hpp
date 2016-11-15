// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/attribute/singlenumericattribute.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include "singlenumericattributesaver.h"

namespace search {

template <typename B>
SingleValueNumericAttribute<B>::
SingleValueNumericAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & c) :
    B(baseFileName, c),
    _data(c.getGrowStrategy().getDocsInitialCapacity(),
          c.getGrowStrategy().getDocsGrowPercent(),
          c.getGrowStrategy().getDocsGrowDelta(),
          getGenerationHolder())
{
}


template <typename B>
SingleValueNumericAttribute<B>::~SingleValueNumericAttribute(void)
{
    getGenerationHolder().clearHoldLists();
}


template <typename B>
void
SingleValueNumericAttribute<B>::onCommit()
{
    this->checkSetMaxValueCount(1);

    {
        // apply updates
        typename B::ValueModifier valueGuard(this->getValueModifier());
        for (const auto & change : this->_changes) {
            if (change._type == ChangeBase::UPDATE) {
                std::atomic_thread_fence(std::memory_order_release);
                _data[change._doc] = change._data;
            } else if (change._type >= ChangeBase::ADD && change._type <= ChangeBase::DIV) {
                std::atomic_thread_fence(std::memory_order_release);
                _data[change._doc] = this->applyArithmetic(_data[change._doc], change);
            } else if (change._type == ChangeBase::CLEARDOC) {
                std::atomic_thread_fence(std::memory_order_release);
                _data[change._doc] = this->_defaultValue._data;
            }
        }
    }

    std::atomic_thread_fence(std::memory_order_release);
    this->removeAllOldGenerations();

    this->_changes.clear();
}

template <typename B>
void
SingleValueNumericAttribute<B>::onUpdateStat()
{
    MemoryUsage usage = _data.getMemoryUsage();
    usage.incAllocatedBytesOnHold(getGenerationHolder().getHeldBytes());
    this->updateStatistics(_data.size(), _data.size(),
                           usage.allocatedBytes(), usage.usedBytes(), usage.deadBytes(), usage.allocatedBytesOnHold());
}

template <typename B>
void
SingleValueNumericAttribute<B>::removeOldGenerations(generation_t firstUsed)
{
    getGenerationHolder().trimHoldLists(firstUsed);
}

template <typename B>
void
SingleValueNumericAttribute<B>::onGenerationChange(generation_t generation)
{
    getGenerationHolder().transferHoldLists(generation - 1);
}

template <typename B>
bool
SingleValueNumericAttribute<B>::onLoadEnumerated(typename B::ReaderBase &
                                                 attrReader)
{
    uint64_t numValues = attrReader.getEnumCount();
    uint32_t numDocs = numValues;

    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);

    FileUtil::LoadedBuffer::UP udatBuffer(this->loadUDAT());
    assert((udatBuffer->size() % sizeof(T)) == 0);
    vespalib::ConstArrayRef<T> map(reinterpret_cast<const T *>(udatBuffer->buffer()),
                                   udatBuffer->size() / sizeof(T));
    attribute::NoSaveLoadedEnum saver;
    _data.fillMapped(getGenerationHolder(),
                     attrReader,
                     numValues,
                     map,
                     saver,
                     numDocs);
    return true;
}


template <typename B>
bool
SingleValueNumericAttribute<B>::onLoad()
{
    typename B::template PrimitiveReader<T> attrReader(*this);
    bool ok(attrReader.getHasLoadData());

    if (!ok)
        return false;

    this->setCreateSerialNum(attrReader.getCreateSerialNum());

    if (attrReader.getEnumerated())
        return onLoadEnumerated(attrReader);
    
    const size_t sz(attrReader.getDataCount());
    getGenerationHolder().clearHoldLists();
    _data.reset();
    _data.unsafe_reserve(sz);
    for (uint32_t i = 0; i < sz; ++i) {
        _data.push_back(attrReader.getNextData());
    }

    B::setNumDocs(sz);
    B::setCommittedDocIdLimit(sz);

    return true;
}

template <typename B>
AttributeVector::SearchContext::UP
SingleValueNumericAttribute<B>::getSearch(QueryTermSimple::UP qTerm,
                                          const AttributeVector::SearchContext::Params & params) const
{
    (void) params;
    QueryTermSimple::RangeResult<T> res = qTerm->getRange<T>();
    if (res.isEqual()) {
        return AttributeVector::SearchContext::UP(new SingleSearchContext< NumericAttribute::Equal<T> >(std::move(qTerm), *this));
    } else {
        return AttributeVector::SearchContext::UP(new SingleSearchContext< NumericAttribute::Range<T> >(std::move(qTerm), *this));
    }
}


template <typename B>
void
SingleValueNumericAttribute<B>::clearDocs(DocId lidLow, DocId lidLimit)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= this->getNumDocs());
    for (DocId lid = lidLow; lid < lidLimit; ++lid) {
        if (!attribute::isUndefined(_data[lid])) {
            this->clearDoc(lid);
        }
    }
}

template <typename B>
void
SingleValueNumericAttribute<B>::onShrinkLidSpace()
{
    uint32_t committedDocIdLimit = this->getCommittedDocIdLimit();
    assert(_data.size() >= committedDocIdLimit);
    _data.shrink(committedDocIdLimit);
    this->setNumDocs(committedDocIdLimit);
}

template <typename B>
std::unique_ptr<AttributeSaver>
SingleValueNumericAttribute<B>::onInitSave()
{
    const uint32_t numDocs(this->getCommittedDocIdLimit());
    assert(numDocs <= _data.size());
    return std::make_unique<SingleValueNumericAttributeSaver>
        (this->createSaveTargetConfig(), &_data[0], numDocs * sizeof(T));
}


}


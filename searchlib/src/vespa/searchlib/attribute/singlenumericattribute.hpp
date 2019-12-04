// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "singlenumericattribute.h"
#include "attributevector.hpp"
#include "singlenumericattributesaver.h"
#include "load_utils.h"
#include "primitivereader.h"
#include "attributeiterators.hpp"
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search {

template <typename B>
SingleValueNumericAttribute<B>::
SingleValueNumericAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & c) :
    B(baseFileName, c),
    _data(c.getGrowStrategy().getDocsInitialCapacity(),
          c.getGrowStrategy().getDocsGrowPercent(),
          c.getGrowStrategy().getDocsGrowDelta(),
          getGenerationHolder())
{ }

template <typename B>
SingleValueNumericAttribute<B>::~SingleValueNumericAttribute()
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
    vespalib::MemoryUsage usage = _data.getMemoryUsage();
    usage.mergeGenerationHeldBytes(getGenerationHolder().getHeldBytes());
    usage.merge(this->getChangeVectorMemoryUsage());
    this->updateStatistics(_data.size(), _data.size(),
                           usage.allocatedBytes(), usage.usedBytes(), usage.deadBytes(), usage.allocatedBytesOnHold());
}

template <typename B>
void
SingleValueNumericAttribute<B>::onAddDocs(DocId lidLimit) {
    _data.reserve(lidLimit);
}

template <typename B>
bool
SingleValueNumericAttribute<B>::addDoc(DocId & doc) {
    bool incGen = _data.isFull();
    _data.push_back(attribute::getUndefined<T>());
    std::atomic_thread_fence(std::memory_order_release);
    B::incNumDocs();
    doc = B::getNumDocs() - 1;
    this->updateUncommittedDocIdLimit(doc);
    if (incGen) {
        this->incGeneration();
    } else
        this->removeAllOldGenerations();
    return true;
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
SingleValueNumericAttribute<B>::onLoadEnumerated(ReaderBase &attrReader)
{
    uint32_t numDocs = attrReader.getEnumCount();

    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);
    _data.unsafe_reserve(numDocs);

    fileutil::LoadedBuffer::UP udatBuffer(this->loadUDAT());
    assert((udatBuffer->size() % sizeof(T)) == 0);
    vespalib::ConstArrayRef<T> map(reinterpret_cast<const T *>(udatBuffer->buffer()),
                                   udatBuffer->size() / sizeof(T));
    attribute::loadFromEnumeratedSingleValue(_data, getGenerationHolder(), attrReader,
                                             map, attribute::NoSaveLoadedEnum());
    return true;
}


template <typename B>
bool
SingleValueNumericAttribute<B>::onLoad()
{
    PrimitiveReader<T> attrReader(*this);
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
                                          const attribute::SearchContextParams & params) const
{
    (void) params;
    QueryTermSimple::RangeResult<T> res = qTerm->getRange<T>();
    if (res.isEqual()) {
        return std::make_unique<SingleSearchContext<NumericAttribute::Equal<T>>>(std::move(qTerm), *this);
    } else {
        return std::make_unique<SingleSearchContext<NumericAttribute::Range<T>>>(std::move(qTerm), *this);
    }
}


template <typename B>
void
SingleValueNumericAttribute<B>::clearDocs(DocId lidLow, DocId lidLimit)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= this->getNumDocs());
    uint32_t count = 0;
    constexpr uint32_t commit_interval = 1000;
    for (DocId lid = lidLow; lid < lidLimit; ++lid) {
        if (!attribute::isUndefined(_data[lid])) {
            this->clearDoc(lid);
        }
        if ((++count % commit_interval) == 0) {
            this->commit();
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
SingleValueNumericAttribute<B>::onInitSave(vespalib::stringref fileName)
{
    const uint32_t numDocs(this->getCommittedDocIdLimit());
    assert(numDocs <= _data.size());
    return std::make_unique<SingleValueNumericAttributeSaver>
        (this->createAttributeHeader(fileName), &_data[0], numDocs * sizeof(T));
}

template <typename B>
template <typename M>
bool SingleValueNumericAttribute<B>::SingleSearchContext<M>::valid() const { return M::isValid(); }

template <typename B>
template <typename M>
SingleValueNumericAttribute<B>::SingleSearchContext<M>::SingleSearchContext(QueryTermSimple::UP qTerm,
                                                                            const NumericAttribute & toBeSearched) :
    M(*qTerm, true),
    AttributeVector::SearchContext(toBeSearched),
    _data(&static_cast<const SingleValueNumericAttribute<B> &>(toBeSearched)._data[0])
{ }


template <typename B>
template <typename M>
Int64Range
SingleValueNumericAttribute<B>::SingleSearchContext<M>::getAsIntegerTerm() const {
    return M::getRange();
}

template <typename B>
template <typename M>
std::unique_ptr<queryeval::SearchIterator>
SingleValueNumericAttribute<B>::SingleSearchContext<M>::
createFilterIterator(fef::TermFieldMatchData * matchData, bool strict)
{
    if (!valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (getIsFilter()) {
        return strict
                 ? std::make_unique<FilterAttributeIteratorStrict<SingleSearchContext<M>>>(*this, matchData)
                 : std::make_unique<FilterAttributeIteratorT<SingleSearchContext<M>>>(*this, matchData);
    }
    return strict
             ? std::make_unique<AttributeIteratorStrict<SingleSearchContext<M>>>(*this, matchData)
             : std::make_unique<AttributeIteratorT<SingleSearchContext<M>>>(*this, matchData);
}
}


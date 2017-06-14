// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singlenumericenumattribute.h"
#include <vespa/searchlib/common/sort.h>
#include "singleenumattribute.hpp"
#include "loadednumericvalue.h"
#include "primitivereader.h"
#include "attributeiterators.hpp"
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/query/queryterm.h>
#include <vespa/searchlib/util/fileutil.hpp>

namespace search {

template <typename B>
void
SingleValueNumericEnumAttribute<B>::considerUpdateAttributeChange(const Change & c)
{
    _currDocValues[c._doc] = c._data.get();
}

template <typename B>
void
SingleValueNumericEnumAttribute<B>::considerArithmeticAttributeChange(const Change & c, UniqueSet & newUniques)
{
    T oldValue;
    typename std::map<DocId, T>::const_iterator iter = _currDocValues.find(c._doc);
    if (iter != _currDocValues.end()) {
        oldValue = iter->second;
    } else {
        oldValue = get(c._doc);
    }

    T newValue = this->applyArithmetic(oldValue, c);

    EnumIndex idx;
    if (!this->_enumStore.findIndex(newValue, idx)) {
        newUniques.insert(newValue);
    }

    _currDocValues[c._doc] = newValue;
}

template <typename B>
void
SingleValueNumericEnumAttribute<B>::applyArithmeticValueChange(const Change & c, EnumStoreBase::IndexVector & unused)
{
    EnumIndex oldIdx = this->_enumIndices[c._doc];
    EnumIndex newIdx;
    T newValue = this->applyArithmetic(get(c._doc), c);
    this->_enumStore.findIndex(newValue, newIdx);

    this->updateEnumRefCounts(c, newIdx, oldIdx, unused);
}

template <typename B>
SingleValueNumericEnumAttribute<B>::
SingleValueNumericEnumAttribute(const vespalib::string & baseFileName,
                                const AttributeVector::Config & c)
    : SingleValueEnumAttribute<B>(baseFileName, c),
      _currDocValues()
{
}

template <typename B>
SingleValueNumericEnumAttribute<B>::~SingleValueNumericEnumAttribute() {}

template <typename B>
void
SingleValueNumericEnumAttribute<B>::onCommit()
{
    SingleValueEnumAttribute<B>::onCommit();
    _currDocValues.clear();
}

template <typename B>
bool
SingleValueNumericEnumAttribute<B>::onLoadEnumerated(ReaderBase &attrReader)
{
    fileutil::LoadedBuffer::UP udatBuffer(this->loadUDAT());

    uint64_t numValues = attrReader.getEnumCount();
    uint32_t numDocs = numValues;

    EnumIndexVector eidxs;
    this->fillEnum0(udatBuffer->buffer(), udatBuffer->size(), eidxs);
    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);
    LoadedEnumAttributeVector loaded;
    EnumVector enumHist;
    if (this->hasPostings()) {
        loaded.reserve(numValues);
        this->fillEnumIdx(attrReader, eidxs, loaded);
    } else {
        EnumVector(eidxs.size(), 0).swap(enumHist);
        this->fillEnumIdx(attrReader, eidxs, enumHist);
    }
    EnumIndexVector().swap(eidxs);
    if (this->hasPostings()) {
        if (numDocs > 0) {
            this->onAddDoc(numDocs - 1);
        }
        attribute::sortLoadedByEnum(loaded);
        this->fillPostingsFixupEnum(loaded);
    } else {
        this->fixupEnumRefCounts(enumHist);
    }
    return true;
}


template <typename B>
bool
SingleValueNumericEnumAttribute<B>::onLoad()
{
    PrimitiveReader<T> attrReader(*this);
    bool ok(attrReader.getHasLoadData());
    
    if (!ok)
        return false;

    this->setCreateSerialNum(attrReader.getCreateSerialNum());

    if (attrReader.getEnumerated())
        return onLoadEnumerated(attrReader);

    const uint32_t numDocs(attrReader.getDataCount());
    SequentialReadModifyWriteVector<LoadedNumericValueT> loaded(numDocs);

    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);
    if (numDocs > 0) {
        this->onAddDoc(numDocs - 1);
    }

    for (uint32_t docIdx = 0; docIdx < numDocs; ++docIdx) {
        loaded[docIdx]._docId = docIdx;
        loaded[docIdx]._idx = 0;
        loaded[docIdx].setValue(attrReader.getNextData());
    }

    attribute::sortLoadedByValue(loaded);
    this->fillPostings(loaded);
    loaded.rewind();
    this->fillEnum(loaded);
    attribute::sortLoadedByDocId(loaded);
    loaded.rewind();
    this->fillValues(loaded);
    
    return true;
}


template <typename B>
AttributeVector::SearchContext::UP
SingleValueNumericEnumAttribute<B>::getSearch(QueryTermSimple::UP qTerm,
                                              const attribute::SearchContextParams & params) const
{
    (void) params;
    QueryTermSimple::RangeResult<T> res = qTerm->getRange<T>();
    if (res.isEqual()) {
        return AttributeVector::SearchContext::UP (new SingleSearchContext(std::move(qTerm), *this));
    } else {
        return AttributeVector::SearchContext::UP (new SingleSearchContext(std::move(qTerm), *this));
    }
}

template <typename B>
bool SingleValueNumericEnumAttribute<B>::SingleSearchContext::valid() const { return this->isValid(); }

template <typename B>
SingleValueNumericEnumAttribute<B>::SingleSearchContext::SingleSearchContext(QueryTermSimpleUP qTerm, const NumericAttribute & toBeSearched) :
    NumericAttribute::Range<T>(*qTerm, true),
    AttributeVector::SearchContext(toBeSearched),
    _toBeSearched(static_cast<const SingleValueNumericEnumAttribute<B> &>(toBeSearched))
{ }

template <typename B>
Int64Range SingleValueNumericEnumAttribute<B>::SingleSearchContext::getAsIntegerTerm() const {
    return this->getRange();
}

template <typename B>
std::unique_ptr<queryeval::SearchIterator>
SingleValueNumericEnumAttribute<B>::SingleSearchContext::createFilterIterator(fef::TermFieldMatchData * matchData,
                                                                              bool strict)
{
    if (!valid()) {
        return queryeval::SearchIterator::UP(new queryeval::EmptySearch());
    }
    if (getIsFilter()) {
        return queryeval::SearchIterator::UP
                (strict
                 ? new FilterAttributeIteratorStrict<SingleSearchContext>(*this, matchData)
                 : new FilterAttributeIteratorT<SingleSearchContext>(*this, matchData));
    }
    return queryeval::SearchIterator::UP
            (strict
             ? new AttributeIteratorStrict<SingleSearchContext>(*this, matchData)
             : new AttributeIteratorT<SingleSearchContext>(*this, matchData));
}

}


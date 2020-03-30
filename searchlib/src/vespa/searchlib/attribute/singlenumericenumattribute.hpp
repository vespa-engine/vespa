// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributeiterators.hpp"
#include "load_utils.h"
#include "loadednumericvalue.h"
#include "primitivereader.h"
#include "singleenumattribute.hpp"
#include "singlenumericenumattribute.h"
#include <vespa/searchlib/common/sort.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
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
    auto iter = _currDocValues.find(c._doc);
    if (iter != _currDocValues.end()) {
        oldValue = iter->second;
    } else {
        oldValue = get(c._doc);
    }

    T newValue = this->applyArithmetic(oldValue, c);

    EnumIndex idx;
    if (!this->_enumStore.find_index(newValue, idx)) {
        newUniques.insert(newValue);
    }

    _currDocValues[c._doc] = newValue;
}

template <typename B>
void
SingleValueNumericEnumAttribute<B>::applyArithmeticValueChange(const Change& c, EnumStoreBatchUpdater& updater)
{
    EnumIndex oldIdx = this->_enumIndices[c._doc];
    EnumIndex newIdx;
    T newValue = this->applyArithmetic(get(c._doc), c);
    this->_enumStore.find_index(newValue, newIdx);

    this->updateEnumRefCounts(c, newIdx, oldIdx, updater);
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
SingleValueNumericEnumAttribute<B>::~SingleValueNumericEnumAttribute() = default;

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
    auto udatBuffer = attribute::LoadUtils::loadUDAT(*this);

    uint64_t numValues = attrReader.getEnumCount();
    uint32_t numDocs = numValues;

    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);
    if (this->hasPostings()) {
        auto loader = this->getEnumStore().make_enumerated_postings_loader();
        loader.load_unique_values(udatBuffer->buffer(), udatBuffer->size());
        this->load_enumerated_data(attrReader, loader, numValues);
        if (numDocs > 0) {
            this->onAddDoc(numDocs - 1);
        }
        this->load_posting_lists_and_update_enum_store(loader);
    } else {
        auto loader = this->getEnumStore().make_enumerated_loader();
        loader.load_unique_values(udatBuffer->buffer(), udatBuffer->size());
        this->load_enumerated_data(attrReader, loader);
    }
    return true;
}


template <typename B>
bool
SingleValueNumericEnumAttribute<B>::onLoad()
{
    PrimitiveReader<T> attrReader(*this);
    bool ok(attrReader.getHasLoadData());
    
    if (!ok) {
        return false;
    }

    this->setCreateSerialNum(attrReader.getCreateSerialNum());

    if (attrReader.getEnumerated()) {
        return onLoadEnumerated(attrReader);
    }

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
    this->load_posting_lists(loaded);
    loaded.rewind();
    this->load_enum_store(loaded);
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
bool
SingleValueNumericEnumAttribute<B>::SingleSearchContext::valid() const
{
    return this->isValid();
}

template <typename B>
SingleValueNumericEnumAttribute<B>::SingleSearchContext::SingleSearchContext(QueryTermSimpleUP qTerm, const NumericAttribute & toBeSearched) :
    NumericAttribute::Range<T>(*qTerm, true),
    AttributeVector::SearchContext(toBeSearched),
    _toBeSearched(static_cast<const SingleValueNumericEnumAttribute<B> &>(toBeSearched))
{ }

template <typename B>
Int64Range
SingleValueNumericEnumAttribute<B>::SingleSearchContext::getAsIntegerTerm() const
{
    return this->getRange();
}

template <typename B>
std::unique_ptr<queryeval::SearchIterator>
SingleValueNumericEnumAttribute<B>::SingleSearchContext::createFilterIterator(fef::TermFieldMatchData * matchData,
                                                                              bool strict)
{
    if (!valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (getIsFilter()) {
        return strict
               ? std::make_unique<FilterAttributeIteratorStrict<SingleSearchContext>>(*this, matchData)
               : std::make_unique<FilterAttributeIteratorT<SingleSearchContext>>(*this, matchData);
    }
    return strict
           ? std::make_unique<AttributeIteratorStrict<SingleSearchContext>>(*this, matchData)
           : std::make_unique<AttributeIteratorT<SingleSearchContext>>(*this, matchData);
}

}


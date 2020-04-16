// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributeiterators.hpp"
#include "load_utils.h"
#include "loadednumericvalue.h"
#include "multinumericenumattribute.h"
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/util/fileutil.hpp>

namespace search {

using fileutil::LoadedBuffer;

template <typename B, typename M>
MultiValueNumericEnumAttribute<B, M>::
MultiValueNumericEnumAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg)
    : MultiValueEnumAttribute<B, M>(baseFileName, cfg)
{ }

template <typename B, typename M>
void
MultiValueNumericEnumAttribute<B, M>::loadAllAtOnce(AttributeReader & attrReader, size_t numDocs, size_t numValues)
{
    LoadedVectorR loaded(numValues);

    bool hasWeight(attrReader.hasWeight());
    for (uint32_t docIdx(0), valueIdx(0); docIdx < numDocs; ++docIdx) {
        const uint32_t currValueCount = attrReader.getNextValueCount();
        for (uint32_t subIdx = 0; subIdx < currValueCount; ++subIdx) {
            loaded[valueIdx]._docId = docIdx;
            loaded[valueIdx]._idx = subIdx;
            loaded[valueIdx].setValue(attrReader.getNextData());
            loaded[valueIdx].setWeight(hasWeight ? attrReader.getNextWeight() : 1);
            valueIdx++;
        }
    }

    attribute::sortLoadedByValue(loaded);
    this->load_posting_lists(loaded);
    loaded.rewind();
    this->load_enum_store(loaded);
    attribute::sortLoadedByDocId(loaded);

    loaded.rewind();
    this->fillValues(loaded);
}

template <typename B, typename M>
bool
MultiValueNumericEnumAttribute<B, M>::onLoadEnumerated(ReaderBase &attrReader)
{
    auto udatBuffer = attribute::LoadUtils::loadUDAT(*this);

    uint32_t numDocs = attrReader.getNumIdx() - 1;
    uint64_t numValues = attrReader.getNumValues();
    uint64_t enumCount = attrReader.getEnumCount();
    assert(numValues == enumCount);
    (void) enumCount;

    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);
    this->_mvMapping.reserve(numDocs);

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


template <typename B, typename M>
bool
MultiValueNumericEnumAttribute<B, M>::onLoad()
{
    AttributeReader attrReader(*this);
    bool ok(attrReader.getHasLoadData());

    if (!ok) {
        return false;
    }

    this->setCreateSerialNum(attrReader.getCreateSerialNum());

    if (attrReader.getEnumerated()) {
        return onLoadEnumerated(attrReader);
    }
    
    size_t numDocs = attrReader.getNumIdx() - 1;
    uint32_t numValues = attrReader.getNumValues();

    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);
    if (numDocs > 0) {
        this->onAddDoc(numDocs - 1);
    }
    this->_mvMapping.reserve(numDocs);
    loadAllAtOnce(attrReader, numDocs, numValues);

    return true;
}

template <typename B, typename M>
AttributeVector::SearchContext::UP
MultiValueNumericEnumAttribute<B, M>::getSearch(QueryTermSimple::UP qTerm,
                                                const attribute::SearchContextParams & params) const
{
    (void) params;
    QueryTermSimple::RangeResult<T> res = qTerm->getRange<T>();
    if (this->hasArrayType()) {
        if (res.isEqual()) {
            return std::make_unique<ArraySearchContext>(std::move(qTerm), *this);
        } else {
            return std::make_unique<ArraySearchContext>(std::move(qTerm), *this);
        }
    } else {
        if (res.isEqual()) {
            return std::make_unique<SetSearchContext>(std::move(qTerm), *this);
        } else {
            return std::make_unique<SetSearchContext>(std::move(qTerm), *this);
        }
    }
}

template <typename B, typename M>
MultiValueNumericEnumAttribute<B, M>::SetSearchContext::SetSearchContext(QueryTermSimpleUP qTerm, const NumericAttribute & toBeSearched) :
    NumericAttribute::Range<T>(*qTerm),
    SearchContext(toBeSearched),
    _toBeSearched(static_cast<const MultiValueNumericEnumAttribute<B, M> &>(toBeSearched))
{ }

template <typename B, typename M>
Int64Range
MultiValueNumericEnumAttribute<B, M>::SetSearchContext::getAsIntegerTerm() const
{
    return this->getRange();
}

template <typename B, typename M>
std::unique_ptr<queryeval::SearchIterator>
MultiValueNumericEnumAttribute<B, M>::SetSearchContext::createFilterIterator(fef::TermFieldMatchData * matchData, bool strict)
{
    if (!valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (getIsFilter()) {
        return strict
               ? std::make_unique<FilterAttributeIteratorStrict<SetSearchContext>>(*this, matchData)
               : std::make_unique<FilterAttributeIteratorT<SetSearchContext>>(*this, matchData);
    }
    return strict
           ? std::make_unique<AttributeIteratorStrict<SetSearchContext>>(*this, matchData)
           : std::make_unique<AttributeIteratorT<SetSearchContext>>(*this, matchData);
}

template <typename B, typename M>
std::unique_ptr<queryeval::SearchIterator>
MultiValueNumericEnumAttribute<B, M>::ArraySearchContext::createFilterIterator(fef::TermFieldMatchData * matchData, bool strict)
{
    if (!valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (getIsFilter()) {
        return strict
               ? std::make_unique<FilterAttributeIteratorStrict<ArraySearchContext>>(*this, matchData)
               : std::make_unique<FilterAttributeIteratorT<ArraySearchContext>>(*this, matchData);
    }
    return strict
           ? std::make_unique<AttributeIteratorStrict<ArraySearchContext>>(*this, matchData)
           : std::make_unique<AttributeIteratorT<ArraySearchContext>>(*this, matchData);
}

template <typename B, typename M>
MultiValueNumericEnumAttribute<B, M>::ArraySearchContext::ArraySearchContext(QueryTermSimpleUP qTerm, const NumericAttribute & toBeSearched) :
    NumericAttribute::Range<T>(*qTerm),
    SearchContext(toBeSearched),
    _toBeSearched(static_cast<const MultiValueNumericEnumAttribute<B, M> &>(toBeSearched))
{ }

template <typename B, typename M>
Int64Range
MultiValueNumericEnumAttribute<B, M>::ArraySearchContext::getAsIntegerTerm() const
{
    return this->getRange();
}

} // namespace search


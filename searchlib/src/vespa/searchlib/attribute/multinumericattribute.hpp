// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multinumericattribute.h"
#include "multivalueattribute.hpp"
#include "attributevector.hpp"
#include "attributeiterators.hpp"
#include "multinumericattributesaver.h"
#include "load_utils.h"
#include "primitivereader.h"
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/util/fileutil.h>

namespace search {

using fileutil::LoadedBuffer;

template <typename B, typename M>
typename MultiValueNumericAttribute<B, M>::T
MultiValueNumericAttribute<B, M>::getFromEnum(EnumHandle e) const
{
    (void) e;
    return 0;
}

template <typename B, typename M>
bool MultiValueNumericAttribute<B, M>::findEnum(T value, EnumHandle & e) const
{
    (void) value; (void) e;
    return false;
}

template <typename B, typename M>
MultiValueNumericAttribute<B, M>::
MultiValueNumericAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & c) :
    MultiValueAttribute<B, M>(baseFileName, c)
{
}

template <typename B, typename M>
uint32_t MultiValueNumericAttribute<B, M>::getValueCount(DocId doc) const
{
    if (doc >= B::getNumDocs()) {
        return 0;
    }
    MultiValueArrayRef values(this->_mvMapping.get(doc));
    return values.size();
}

template <typename B, typename M>
void
MultiValueNumericAttribute<B, M>::onCommit()
{
    DocumentValues docValues;
    this->applyAttributeChanges(docValues);
    {
        typename B::ValueModifier valueGuard(this->getValueModifier());
        for (const auto & value : docValues) {
            clearOldValues(value.first);
            setNewValues(value.first, value.second);
        }
    }

    std::atomic_thread_fence(std::memory_order_release);
    this->removeAllOldGenerations();

    this->_changes.clear();
    if (this->_mvMapping.considerCompact(this->getConfig().getCompactionStrategy())) {
        this->incGeneration();
        this->updateStat(true);
    }
}

template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::onUpdateStat()
{
    vespalib::MemoryUsage usage = this->_mvMapping.updateStat();
    usage.merge(this->getChangeVectorMemoryUsage());
    this->updateStatistics(this->_mvMapping.getTotalValueCnt(), this->_mvMapping.getTotalValueCnt(), usage.allocatedBytes(),
                           usage.usedBytes(), usage.deadBytes(), usage.allocatedBytesOnHold());
}


template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::clearOldValues(DocId doc)
{
    (void) doc;
}

template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::setNewValues(DocId doc, const std::vector<WType> & values)
{
    this->_mvMapping.set(doc, values);
}

template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::removeOldGenerations(generation_t firstUsed)
{
    this->_mvMapping.trimHoldLists(firstUsed);
}


template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::onGenerationChange(generation_t generation)
{
    this->_mvMapping.transferHoldLists(generation - 1);
}

template <typename B, typename M>
bool
MultiValueNumericAttribute<B, M>::onLoadEnumerated(ReaderBase & attrReader)
{
    uint32_t numDocs = attrReader.getNumIdx() - 1;
    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);
    this->_mvMapping.reserve(numDocs+1);

    auto udatBuffer = attribute::LoadUtils::loadUDAT(*this);
    assert((udatBuffer->size() % sizeof(T)) == 0);
    vespalib::ConstArrayRef<T> map(reinterpret_cast<const T *>(udatBuffer->buffer()), udatBuffer->size() / sizeof(T));
    uint32_t maxvc = attribute::loadFromEnumeratedMultiValue(this->_mvMapping, attrReader, map, attribute::NoSaveLoadedEnum());
    this->checkSetMaxValueCount(maxvc);
    
    return true;
}

template <typename B, typename M>
bool
MultiValueNumericAttribute<B, M>::onLoad()
{
    PrimitiveReader<MValueType> attrReader(*this);
    bool ok(attrReader.getHasLoadData());

    if (!ok)
        return false;

    this->setCreateSerialNum(attrReader.getCreateSerialNum());

    if (attrReader.getEnumerated())
        return onLoadEnumerated(attrReader);
    
    bool hasWeight(attrReader.hasWeight());
    size_t numDocs = attrReader.getNumIdx() - 1;

    this->_mvMapping.prepareLoadFromMultiValue();
    // set values
    std::vector<MultiValueType> values;
    B::setNumDocs(numDocs);
    B::setCommittedDocIdLimit(numDocs);
    this->_mvMapping.reserve(numDocs+1);
    for (DocId doc = 0; doc < numDocs; ++doc) {
        const uint32_t valueCount(attrReader.getNextValueCount());
        for (uint32_t i(0); i < valueCount; i++) {
            MValueType currData = attrReader.getNextData();
            values.emplace_back(currData, hasWeight ? attrReader.getNextWeight() : 1);
        }
        this->checkSetMaxValueCount(valueCount);
        setNewValues(doc, values);
        values.clear();
    }
    this->_mvMapping.doneLoadFromMultiValue();
    return true;
}

template <typename B, typename M>
AttributeVector::SearchContext::UP
MultiValueNumericAttribute<B, M>::getSearch(QueryTermSimple::UP qTerm,
                                            const attribute::SearchContextParams & params) const
{
    (void) params;
    if (this->hasArrayType()) {
        return std::make_unique<ArraySearchContext>(std::move(qTerm), *this);
    } else {
        return std::make_unique<SetSearchContext>(std::move(qTerm), *this);
    }
}


template <typename B, typename M>
std::unique_ptr<AttributeSaver>
MultiValueNumericAttribute<B, M>::onInitSave(vespalib::stringref fileName)
{
    vespalib::GenerationHandler::Guard guard(this->getGenerationHandler().takeGuard());
    return std::make_unique<MultiValueNumericAttributeSaver<MultiValueType>>
        (std::move(guard), this->createAttributeHeader(fileName), this->_mvMapping);
}

template <typename B, typename M>
bool MultiValueNumericAttribute<B, M>::SetSearchContext::valid() const { return this->isValid(); }

template <typename B, typename M>
MultiValueNumericAttribute<B, M>::SetSearchContext::SetSearchContext(QueryTermSimple::UP qTerm, const NumericAttribute & toBeSearched) :
    NumericAttribute::Range<T>(*qTerm),
    AttributeVector::SearchContext(toBeSearched),
    _toBeSearched(static_cast<const MultiValueNumericAttribute<B, M> &>(toBeSearched))
{ }

template <typename B, typename M>
Int64Range MultiValueNumericAttribute<B, M>::SetSearchContext::getAsIntegerTerm() const {
    return this->getRange();
}

template <typename B, typename M>
std::unique_ptr<queryeval::SearchIterator>
MultiValueNumericAttribute<B, M>::SetSearchContext::createFilterIterator(fef::TermFieldMatchData * matchData, bool strict)
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
bool MultiValueNumericAttribute<B, M>::ArraySearchContext::valid() const { return this->isValid(); }

template <typename B, typename M>
MultiValueNumericAttribute<B, M>::ArraySearchContext::ArraySearchContext(QueryTermSimple::UP qTerm, const NumericAttribute & toBeSearched) :
    NumericAttribute::Range<T>(*qTerm),
    AttributeVector::SearchContext(toBeSearched),
    _toBeSearched(static_cast<const MultiValueNumericAttribute<B, M> &>(toBeSearched))
{ }

template <typename B, typename M>
Int64Range MultiValueNumericAttribute<B, M>::ArraySearchContext::getAsIntegerTerm() const {
    return this->getRange();
}

template <typename B, typename M>
std::unique_ptr<queryeval::SearchIterator>
MultiValueNumericAttribute<B, M>::ArraySearchContext::createFilterIterator(fef::TermFieldMatchData * matchData, bool strict)
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

} // namespace search

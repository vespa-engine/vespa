// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multinumericenumattribute.h"
#include "enum_store_loaders.h"
#include "load_utils.h"
#include "loadednumericvalue.h"
#include "enumerated_multi_value_read_view.h"
#include "multi_numeric_enum_search_context.h"
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/util/fileutil.hpp>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/stash.h>

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
        loader.build_enum_value_remapping();
        this->load_enumerated_data(attrReader, loader, numValues);
        if (numDocs > 0) {
            this->onAddDoc(numDocs - 1);
        }
        this->load_posting_lists_and_update_enum_store(loader);
    } else {
        auto loader = this->getEnumStore().make_enumerated_loader();
        loader.load_unique_values(udatBuffer->buffer(), udatBuffer->size());
        loader.build_enum_value_remapping();
        this->load_enumerated_data(attrReader, loader);
    }
    return true;
}


template <typename B, typename M>
bool
MultiValueNumericEnumAttribute<B, M>::onLoad(vespalib::Executor *)
{
    AttributeReader attrReader(*this);
    bool ok(attrReader.getHasLoadData());

    if (!ok) {
        return false;
    }

    this->_enumStore.clear_default_value_ref();
    this->commit();
    this->incGeneration();

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
const attribute::IArrayReadView<typename B::BaseClass::BaseType>*
MultiValueNumericEnumAttribute<B, M>::make_read_view(attribute::IMultiValueAttribute::ArrayTag<typename B::BaseClass::BaseType>, vespalib::Stash& stash) const
{
    return &stash.create<attribute::EnumeratedMultiValueReadView<T, M>>(this->_mvMapping.make_read_view(this->getCommittedDocIdLimit()), this->_enumStore);
}

template <typename B, typename M>
const attribute::IWeightedSetReadView<typename B::BaseClass::BaseType>*
MultiValueNumericEnumAttribute<B, M>::make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<typename B::BaseClass::BaseType>, vespalib::Stash& stash) const
{
    return &stash.create<attribute::EnumeratedMultiValueReadView<multivalue::WeightedValue<T>, M>>(this->_mvMapping.make_read_view(this->getCommittedDocIdLimit()), this->_enumStore);
}

template <typename B, typename M>
std::unique_ptr<attribute::SearchContext>
MultiValueNumericEnumAttribute<B, M>::getSearch(QueryTermSimple::UP qTerm,
                                                const attribute::SearchContextParams & params) const
{
    (void) params;
    auto doc_id_limit = this->getCommittedDocIdLimit();
    return std::make_unique<attribute::MultiNumericEnumSearchContext<T, M>>(std::move(qTerm), *this, this->_mvMapping.make_read_view(doc_id_limit), this->_enumStore);
}

} // namespace search


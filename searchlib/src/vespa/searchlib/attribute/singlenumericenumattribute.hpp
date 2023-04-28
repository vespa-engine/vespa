// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singlenumericenumattribute.h"
#include "load_utils.h"
#include "loadednumericvalue.h"
#include "primitivereader.h"
#include "singleenumattribute.hpp"
#include "single_numeric_enum_search_context.h"
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/util/fileutil.hpp>

namespace search {

template <typename B>
void
SingleValueNumericEnumAttribute<B>::considerUpdateAttributeChange(DocId doc, const Change & c)
{
    _currDocValues[doc] = c._data.get();
}

template <typename B>
void
SingleValueNumericEnumAttribute<B>::considerArithmeticAttributeChange(const Change & c, EnumStoreBatchUpdater & inserter)
{
    T oldValue;
    auto iter = _currDocValues.find(c._doc);
    if (iter != _currDocValues.end()) {
        oldValue = iter->second;
    } else {
        oldValue = get(c._doc);
    }

    T newValue = this->template applyArithmetic<T, typename Change::DataType>(oldValue, c._data.getArithOperand(), c._type);

    EnumIndex idx;
    if (!this->_enumStore.find_index(newValue, idx)) {
        c.set_entry_ref(inserter.insert(newValue).ref());
    } else {
        c.set_entry_ref(idx.ref());
    }

    _currDocValues[c._doc] = newValue;
}

template <typename B>
void
SingleValueNumericEnumAttribute<B>::applyArithmeticValueChange(const Change& c, EnumStoreBatchUpdater& updater)
{
    EnumIndex oldIdx = this->_enumIndices[c._doc].load_relaxed();
    EnumIndex newIdx;
    T newValue = this->template applyArithmetic<T, typename Change::DataType>(get(c._doc), c._data.getArithOperand(), c._type);
    this->_enumStore.find_index(newValue, newIdx);

    this->updateEnumRefCounts(c._doc, newIdx, oldIdx, updater);
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


template <typename B>
bool
SingleValueNumericEnumAttribute<B>::onLoad(vespalib::Executor *)
{
    PrimitiveReader<T> attrReader(*this);
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
std::unique_ptr<attribute::SearchContext>
SingleValueNumericEnumAttribute<B>::getSearch(QueryTermSimple::UP qTerm,
                                              const attribute::SearchContextParams & params) const
{
    (void) params;
    auto docid_limit = this->getCommittedDocIdLimit();
    return std::make_unique<attribute::SingleNumericEnumSearchContext<T>>(std::move(qTerm), *this, this->_enumIndices.make_read_view(docid_limit), this->_enumStore);
}

}


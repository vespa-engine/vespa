// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attributevector.hpp"
#include "load_utils.h"
#include "numeric_matcher.h"
#include "numeric_range_matcher.h"
#include "primitivereader.h"
#include "singlenumericattribute.h"
#include "singlenumericattributesaver.h"
#include "single_numeric_search_context.h"
#include "valuemodifier.h"
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchcommon/attribute/config.h>

namespace search {

template <typename B>
SingleValueNumericAttribute<B>::
SingleValueNumericAttribute(const vespalib::string & baseFileName)
    : SingleValueNumericAttribute(baseFileName, attribute::Config(attribute::BasicType::fromType(T()),
                                                                  attribute::CollectionType::SINGLE))
{ }

template <typename B>
SingleValueNumericAttribute<B>::
SingleValueNumericAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & c)
    : B(baseFileName, c),
      _data(c.getGrowStrategy(), getGenerationHolder(), this->get_initial_alloc())
{ }

template <typename B>
SingleValueNumericAttribute<B>::~SingleValueNumericAttribute()
{
    getGenerationHolder().reclaim_all();
}

template <typename B>
void
SingleValueNumericAttribute<B>::onCommit()
{
    this->checkSetMaxValueCount(1);

    {
        // apply updates
        typename B::ValueModifier valueGuard(this->getValueModifier());
        for (const auto & change : this->_changes.getInsertOrder()) {
            if (change._type == ChangeBase::UPDATE) {
                vespalib::atomic::store_ref_relaxed(_data[change._doc], change._data);
            } else if (change._type >= ChangeBase::ADD && change._type <= ChangeBase::DIV) {
                vespalib::atomic::store_ref_relaxed(_data[change._doc], this->template applyArithmetic<T, typename B::Change::DataType>(_data[change._doc], change._data.getArithOperand(), change._type));
            } else if (change._type == ChangeBase::CLEARDOC) {
                vespalib::atomic::store_ref_relaxed(_data[change._doc], this->_defaultValue._data);
            }
        }
    }

    this->reclaim_unused_memory();

    this->_changes.clear();
}

template <typename B>
void
SingleValueNumericAttribute<B>::onUpdateStat()
{
    vespalib::MemoryUsage usage = _data.getMemoryUsage();
    usage.mergeGenerationHeldBytes(getGenerationHolder().get_held_bytes());
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
    _data.push_back(B::defaultValue());
    std::atomic_thread_fence(std::memory_order_release);
    B::incNumDocs();
    doc = B::getNumDocs() - 1;
    this->updateUncommittedDocIdLimit(doc);
    if (incGen) {
        this->incGeneration();
    } else
        this->reclaim_unused_memory();
    return true;
}

template <typename B>
void
SingleValueNumericAttribute<B>::reclaim_memory(generation_t oldest_used_gen)
{
    getGenerationHolder().reclaim(oldest_used_gen);
}

template <typename B>
void
SingleValueNumericAttribute<B>::before_inc_generation(generation_t current_gen)
{
    getGenerationHolder().assign_generation(current_gen);
}

template <typename B>
bool
SingleValueNumericAttribute<B>::onLoadEnumerated(ReaderBase &attrReader)
{
    uint32_t numDocs = attrReader.getEnumCount();

    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);
    _data.unsafe_reserve(numDocs);

    auto udatBuffer = attribute::LoadUtils::loadUDAT(*this);
    assert((udatBuffer->size() % sizeof(T)) == 0);
    vespalib::ConstArrayRef<T> map(reinterpret_cast<const T *>(udatBuffer->buffer()),
                                   udatBuffer->size() / sizeof(T));
    attribute::loadFromEnumeratedSingleValue(_data, getGenerationHolder(), attrReader,
                                             map, vespalib::ConstArrayRef<uint32_t>(), attribute::NoSaveLoadedEnum());
    return true;
}


template <typename B>
bool
SingleValueNumericAttribute<B>::onLoad(vespalib::Executor *)
{
    PrimitiveReader<T> attrReader(*this);
    bool ok(attrReader.getHasLoadData());

    if (!ok) {
        return false;
    }

    this->setCreateSerialNum(attrReader.getCreateSerialNum());

    if (attrReader.getEnumerated())
        return onLoadEnumerated(attrReader);
    
    const size_t sz(attrReader.getDataCount());
    getGenerationHolder().reclaim_all();
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
std::unique_ptr<attribute::SearchContext>
SingleValueNumericAttribute<B>::getSearch(QueryTermSimple::UP qTerm,
                                          const attribute::SearchContextParams & params) const
{
    (void) params;
    QueryTermSimple::RangeResult<T> res = qTerm->getRange<T>();
    auto data = _data.make_read_view(this->getCommittedDocIdLimit());
    if (res.isEqual()) {
        return std::make_unique<attribute::SingleNumericSearchContext<T, attribute::NumericMatcher<T>>>(std::move(qTerm), *this, data);
    } else {
        return std::make_unique<attribute::SingleNumericSearchContext<T, attribute::NumericRangeMatcher<T>>>(std::move(qTerm), *this, data);
    }
}


template <typename B>
void
SingleValueNumericAttribute<B>::clearDocs(DocId lidLow, DocId lidLimit, bool in_shrink_lid_space)
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
            if (in_shrink_lid_space) {
                this->clear_uncommitted_doc_id_limit();
            }
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

}


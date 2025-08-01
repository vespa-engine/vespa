// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multinumericattribute.h"
#include "attributevector.hpp"
#include "load_utils.h"
#include "multi_numeric_search_context.h"
#include "multinumericattributesaver.h"
#include "numeric_sort_blob_writer.h"
#include "primitivereader.h"
#include "string_to_number.h"
#include "valuemodifier.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/searchlib/query/query_term_simple.h>
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
MultiValueNumericAttribute(const std::string & baseFileName, const AttributeVector::Config & c) :
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
    this->reclaim_unused_memory();

    this->_changes.clear();
    if (this->_mvMapping.consider_compact(this->getConfig().getCompactionStrategy())) {
        this->incGeneration();
        this->updateStat(true);
    }
}

template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::onUpdateStat()
{
    auto& compaction_strategy = this->getConfig().getCompactionStrategy();
    vespalib::MemoryUsage usage = this->_mvMapping.update_stat(compaction_strategy);
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
void MultiValueNumericAttribute<B, M>::reclaim_memory(generation_t oldest_used_gen)
{
    this->_mvMapping.reclaim_memory(oldest_used_gen);
}


template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::before_inc_generation(generation_t current_gen)
{
    this->_mvMapping.assign_generation(current_gen);
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
    this->set_size_on_disk(attrReader.size_on_disk() + udatBuffer->size_on_disk());
    this->set_last_flush_duration(attrReader.flush_duration());
    std::span<const T> map(reinterpret_cast<const T *>(udatBuffer->buffer()), udatBuffer->size() / sizeof(T));
    uint32_t maxvc = attribute::loadFromEnumeratedMultiValue(this->_mvMapping, attrReader, map, std::span<const uint32_t>(), attribute::NoSaveLoadedEnum());
    this->checkSetMaxValueCount(maxvc);

    return true;
}

template <typename B, typename M>
bool
MultiValueNumericAttribute<B, M>::onLoad(vespalib::Executor *)
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
    this->set_size_on_disk(attrReader.size_on_disk());
    this->set_last_flush_duration(attrReader.flush_duration());
    this->_mvMapping.reserve(numDocs+1);
    for (DocId doc = 0; doc < numDocs; ++doc) {
        const uint32_t valueCount(attrReader.getNextValueCount());
        for (uint32_t i(0); i < valueCount; i++) {
            MValueType currData = attrReader.getNextData();
            values.emplace_back(multivalue::ValueBuilder<M>::build(currData, hasWeight ? attrReader.getNextWeight() : 1));
        }
        this->checkSetMaxValueCount(valueCount);
        setNewValues(doc, values);
        values.clear();
    }
    this->_mvMapping.doneLoadFromMultiValue();
    return true;
}

template <typename B, typename M>
std::unique_ptr<attribute::SearchContext>
MultiValueNumericAttribute<B, M>::getSearch(QueryTermSimple::UP qTerm,
                                            const attribute::SearchContextParams & params) const
{
    (void) params;
    return std::make_unique<attribute::MultiNumericSearchContext<T, M>>(std::move(qTerm), *this, this->_mvMapping.make_read_view(this->getCommittedDocIdLimit()));
}


template <typename B, typename M>
std::unique_ptr<AttributeSaver>
MultiValueNumericAttribute<B, M>::onInitSave(std::string_view fileName)
{
    vespalib::GenerationHandler::Guard guard(this->getGenerationHandler().takeGuard());
    return std::make_unique<MultiValueNumericAttributeSaver<MultiValueType>>
        (std::move(guard), this->createAttributeHeader(fileName), this->_mvMapping);
}

template <typename B, typename M>
bool
MultiValueNumericAttribute<B, M>::is_sortable() const noexcept
{
    return true;
}

template <typename MultiValueMappingT, typename T, bool ascending>
class MultiNumericSortBlobWriter : public attribute::ISortBlobWriter {
private:
    const MultiValueMappingT& _mv_mapping;
    attribute::NumericSortBlobWriter<T, ascending> _writer;
public:
    MultiNumericSortBlobWriter(const MultiValueMappingT &mv_mapping, common::sortspec::MissingPolicy policy,
                               T missing_value)
        : _mv_mapping(mv_mapping),
          _writer(policy, missing_value, true)
    {
    }
    long write(uint32_t docid, void* buf, long available) override {
        _writer.reset();
        auto indices = _mv_mapping.get(docid);
        for (auto& v : indices) {
            _writer.candidate(multivalue::get_value(v));
        }
        return _writer.write(buf, available);
    }
};

template <typename B, typename M>
std::unique_ptr<attribute::ISortBlobWriter>
MultiValueNumericAttribute<B, M>::make_sort_blob_writer(bool ascending, const common::BlobConverter*,
                                                        common::sortspec::MissingPolicy policy,
                                                        std::string_view missing_value) const
{
    T missing_num = string_to_number<T>(missing_value);
    if (ascending) {
        return std::make_unique<MultiNumericSortBlobWriter<MultiValueMapping, T, true>>(this->_mvMapping, policy, missing_num);
    } else {
        return std::make_unique<MultiNumericSortBlobWriter<MultiValueMapping, T, false>>(this->_mvMapping, policy, missing_num);
    }
}

} // namespace search

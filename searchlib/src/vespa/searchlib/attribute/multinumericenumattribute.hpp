// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multinumericenumattribute.h"
#include "enum_store_loaders.h"
#include "enumerated_multi_value_read_view.h"
#include "load_utils.h"
#include "loadednumericvalue.h"
#include "multi_numeric_enum_search_context.h"
#include "numeric_sort_blob_writer.h"
#include "string_to_number.h"
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/util/fileutil.hpp>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/stash.h>

namespace search {

using fileutil::LoadedBuffer;

template <typename B, typename M>
MultiValueNumericEnumAttribute<B, M>::
MultiValueNumericEnumAttribute(const std::string & baseFileName, const AttributeVector::Config & cfg)
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
    this->set_size_on_disk(attrReader.size_on_disk() + udatBuffer->size_on_disk());
    this->set_last_flush_duration(attrReader.flush_duration());
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
    this->set_size_on_disk(attrReader.size_on_disk());
    this->set_last_flush_duration(attrReader.flush_duration());
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

template <typename B, typename M>
bool
MultiValueNumericEnumAttribute<B, M>::is_sortable() const noexcept
{
    return true;
}

template <typename MultiValueMappingT, typename EnumStoreT, typename T, bool ascending>
class MultiNumericEnumSortBlobWriter : public attribute::ISortBlobWriter {
private:
    const MultiValueMappingT& _mv_mapping;
    const EnumStoreT& _enum_store;
    attribute::NumericSortBlobWriter<T, ascending> _writer;
public:
    MultiNumericEnumSortBlobWriter(const MultiValueMappingT& mv_mapping, const EnumStoreT& enum_store,
                                   search::common::sortspec::MissingPolicy policy, T missing_value)
        : _mv_mapping(mv_mapping),
          _enum_store(enum_store),
          _writer(policy, missing_value, true)
    {}
    long write(uint32_t docid, void* buf, long available) override {
        _writer.reset();
        auto indices = _mv_mapping.get(docid);
        for (auto& v : indices) {
            _writer.candidate(_enum_store.get_value(multivalue::get_value_ref(v).load_acquire()));
        }
        return _writer.write(buf, available);
    }
};

template <typename B, typename M>
std::unique_ptr<attribute::ISortBlobWriter>
MultiValueNumericEnumAttribute<B, M>::make_sort_blob_writer(bool ascending, const common::BlobConverter*,
                                                            search::common::sortspec::MissingPolicy policy,
                                                            std::string_view missing_value) const
{
    T missing_num = string_to_number<T>(missing_value);
    if (ascending) {
        return std::make_unique<MultiNumericEnumSortBlobWriter<MultiValueMapping, EnumStore, T, true>>(this->_mvMapping,
            this->_enumStore, policy, missing_num);
    } else {
        return std::make_unique<MultiNumericEnumSortBlobWriter<MultiValueMapping, EnumStore, T, false>>(this->_mvMapping,
            this->_enumStore, policy, missing_num);
    }
}

} // namespace search


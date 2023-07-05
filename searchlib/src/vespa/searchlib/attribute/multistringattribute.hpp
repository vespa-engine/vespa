// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multistringattribute.h"
#include "enumattribute.hpp"
#include "enumerated_multi_value_read_view.h"
#include "multi_string_enum_hint_search_context.h"
#include "string_sort_blob_writer.h"
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/util/regexp.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search {

//-----------------------------------------------------------------------------
// MultiValueStringAttributeT public
//-----------------------------------------------------------------------------
template <typename B, typename M>
MultiValueStringAttributeT<B, M>::
MultiValueStringAttributeT(const vespalib::string &name,
                           const AttributeVector::Config &c)
    : MultiValueEnumAttribute<B, M>(name, c)
{ }

template <typename B, typename M>
MultiValueStringAttributeT<B, M>::MultiValueStringAttributeT(const vespalib::string &name)
    : MultiValueStringAttributeT<B, M>(name, AttributeVector::Config(AttributeVector::BasicType::STRING,  attribute::CollectionType::ARRAY))
{ }

template <typename B, typename M>
MultiValueStringAttributeT<B, M>::~MultiValueStringAttributeT() = default;


template <typename B, typename M>
void
MultiValueStringAttributeT<B, M>::freezeEnumDictionary()
{
    this->getEnumStore().freeze_dictionary();
}

template <typename B, typename M>
std::unique_ptr<attribute::SearchContext>
MultiValueStringAttributeT<B, M>::getSearch(QueryTermSimpleUP qTerm,
                                            const attribute::SearchContextParams &) const
{
    bool cased = this->get_match_is_cased();
    auto doc_id_limit = this->getCommittedDocIdLimit();
    return std::make_unique<attribute::MultiStringEnumHintSearchContext<M>>(std::move(qTerm), cased, *this, this->_mvMapping.make_read_view(doc_id_limit), this->_enumStore, doc_id_limit, this->getStatus().getNumValues());
}

template <typename B, typename M>
const attribute::IArrayReadView<const char*>*
MultiValueStringAttributeT<B, M>::make_read_view(attribute::IMultiValueAttribute::ArrayTag<const char*>, vespalib::Stash& stash) const
{
    return &stash.create<attribute::EnumeratedMultiValueReadView<const char*, M>>(this->_mvMapping.make_read_view(this->getCommittedDocIdLimit()), this->_enumStore);
}

template <typename B, typename M>
const attribute::IWeightedSetReadView<const char*>*
MultiValueStringAttributeT<B, M>::make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<const char*>, vespalib::Stash& stash) const
{
    return &stash.create<attribute::EnumeratedMultiValueReadView<multivalue::WeightedValue<const char*>, M>>(this->_mvMapping.make_read_view(this->getCommittedDocIdLimit()), this->_enumStore);
}

template <typename B, typename M>
long
MultiValueStringAttributeT<B, M>::on_serialize_for_sort(DocId doc, void * serTo, long available, const common::BlobConverter * bc, bool asc) const
{
    attribute::StringSortBlobWriter writer(serTo, available, bc, asc);
    auto indices = this->_mvMapping.get(doc);
    for (auto& v : indices) {
        if (!writer.candidate(this->_enumStore.get_value(multivalue::get_value_ref(v).load_acquire()))) {
            return -1;
        }
    }
    return writer.write();
}

template <typename B, typename M>
long
MultiValueStringAttributeT<B, M>::onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const
{
    return on_serialize_for_sort(doc, serTo, available, bc, true);
}

template <typename B, typename M>
long
MultiValueStringAttributeT<B, M>::onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const
{
    return on_serialize_for_sort(doc, serTo, available, bc, false);
}

} // namespace search


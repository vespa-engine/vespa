// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singlestringattribute.h"
#include "singleenumattribute.hpp"
#include "attributevector.hpp"
#include "single_string_enum_hint_search_context.h"
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/util/regexp.h>
#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search {

//-----------------------------------------------------------------------------
// SingleValueStringAttributeT public
//-----------------------------------------------------------------------------
template <typename B>
SingleValueStringAttributeT<B>::
SingleValueStringAttributeT(const vespalib::string &name,
                            const AttributeVector::Config & c)
    : SingleValueEnumAttribute<B>(name, c)
{ }

template <typename B>
SingleValueStringAttributeT<B>::SingleValueStringAttributeT(const vespalib::string &name)
    : SingleValueStringAttributeT<B>(name, AttributeVector::Config(AttributeVector::BasicType::STRING))
{ }

template <typename B>
SingleValueStringAttributeT<B>::~SingleValueStringAttributeT() = default;

template <typename B>
void
SingleValueStringAttributeT<B>::freezeEnumDictionary()
{
    this->getEnumStore().freeze_dictionary();
}

template <typename B>
std::unique_ptr<attribute::SearchContext>
SingleValueStringAttributeT<B>::getSearch(QueryTermSimpleUP qTerm,
                                          const attribute::SearchContextParams &) const
{
    bool cased = this->get_match_is_cased();
    auto docid_limit = this->getCommittedDocIdLimit();
    return std::make_unique<attribute::SingleStringEnumHintSearchContext>(std::move(qTerm), cased, *this, this->_enumIndices.make_read_view(docid_limit), this->_enumStore, this->getStatus().getNumValues());
}

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singlestringattribute.h"
#include "stringattribute.h"
#include "singleenumattribute.hpp"
#include "attributevector.hpp"
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>
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
    return std::make_unique<StringTemplSearchContext>(std::move(qTerm), *this);
}

template <typename B>
SingleValueStringAttributeT<B>::StringTemplSearchContext::StringTemplSearchContext(QueryTermSimple::UP qTerm, const AttrType & toBeSearched) :
    StringSingleImplSearchContext(std::move(qTerm), toBeSearched),
    EnumHintSearchContext(toBeSearched.getEnumStore().get_dictionary(),
                          toBeSearched.getCommittedDocIdLimit(),
                          toBeSearched.getStatus().getNumValues())
{
    this->setup_enum_hint_sc(toBeSearched.getEnumStore(), *this);
}

}

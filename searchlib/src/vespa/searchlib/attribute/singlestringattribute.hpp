// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singlestringattribute.h"
#include "stringattribute.h"
#include "singleenumattribute.hpp"
#include "attributevector.hpp"
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/util/bufferwriter.h>
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
AttributeVector::SearchContext::UP
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
    const EnumStore &enumStore(toBeSearched.getEnumStore());

    this->_plsc = static_cast<attribute::IPostingListSearchContext *>(this);
    if (this->valid()) {
        if (this->isPrefix()) {
            auto comp = enumStore.make_folded_comparator(queryTerm()->getTerm(), true);
            lookupRange(comp, comp);
        } else if (this->isRegex()) {
            vespalib::string prefix(vespalib::RegexpUtil::get_prefix(this->queryTerm()->getTerm()));
            auto comp = enumStore.make_folded_comparator(prefix.c_str(), true);
            lookupRange(comp, comp);
        } else {
            auto comp = enumStore.make_folded_comparator(queryTerm()->getTerm());
            lookupTerm(comp);
        }
    }
}

}

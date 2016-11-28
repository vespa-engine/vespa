// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/singlestringattribute.h>
#include <vespa/searchlib/attribute/stringattribute.h>
#include <vespa/searchlib/attribute/singleenumattribute.hpp>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>
#include <set>
#include <string>

namespace search {

//-----------------------------------------------------------------------------
// SingleValueStringAttributeT public
//-----------------------------------------------------------------------------
template <typename B>
SingleValueStringAttributeT<B>::
SingleValueStringAttributeT(const vespalib::string &name,
                            const AttributeVector::Config & c)
    : SingleValueEnumAttribute<B>(name, c)
{
}

template <typename B>
SingleValueStringAttributeT<B>::~SingleValueStringAttributeT()
{
}

template <typename B>
void
SingleValueStringAttributeT<B>::freezeEnumDictionary(void)
{
    this->getEnumStore().freezeTree();
}


template <typename B>
AttributeVector::SearchContext::UP
SingleValueStringAttributeT<B>::getSearch(QueryTermSimple::UP qTerm,
                                          const AttributeVector::SearchContext::Params & params) const
{
    (void) params;
    return std::unique_ptr<search::AttributeVector::SearchContext>
        (new StringTemplSearchContext(std::move(qTerm), *this));
}

template <typename B>
SingleValueStringAttributeT<B>::StringTemplSearchContext::StringTemplSearchContext(QueryTermSimple::UP qTerm, const AttrType & toBeSearched) :
    StringSingleImplSearchContext(std::move(qTerm), toBeSearched),
    EnumHintSearchContext(toBeSearched.getEnumStore().getEnumStoreDict(),
                          toBeSearched.getCommittedDocIdLimit(),
                          toBeSearched.getStatus().getNumValues())
{
    const EnumStore &enumStore(toBeSearched.getEnumStore());

    this->_plsc = static_cast<attribute::IPostingListSearchContext *>(this);
    if (this->valid()) {
        if (this->isPrefix()) {
            FoldedComparatorType comp(enumStore, queryTerm().getTerm(), true);
            lookupRange(comp, comp);
        } else if (this->isRegex()) {
            vespalib::string prefix(vespalib::Regexp::get_prefix(this->queryTerm().getTerm()));
            FoldedComparatorType comp(enumStore, prefix.c_str(), true);
            lookupRange(comp, comp);
        } else {
            FoldedComparatorType comp(enumStore, queryTerm().getTerm());
            lookupTerm(comp);
        }
    }
}


} // namespace search


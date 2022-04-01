// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "single_enum_search_context.h"
#include "attributeiterators.hpp"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

template <typename T, typename Matcher>
SingleEnumSearchContext<T, Matcher>::SingleEnumSearchContext(Matcher&& matcher, const AttributeVector& toBeSearched, const vespalib::datastore::AtomicEntryRef* enum_indices, const EnumStoreT<T>& enum_store)
    : Matcher(std::move(matcher)),
      SearchContext(toBeSearched),
      _enum_indices(enum_indices),
      _enum_store(enum_store)
{
}

template <typename T, typename Matcher>
bool
SingleEnumSearchContext<T, Matcher>::valid() const
{
    return this->isValid();
}

template <typename T, typename Matcher>
std::unique_ptr<queryeval::SearchIterator>
SingleEnumSearchContext<T, Matcher>::createFilterIterator(fef::TermFieldMatchData* matchData, bool strict)
{
    if (!valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (getIsFilter()) {
        return strict
            ? std::make_unique<FilterAttributeIteratorStrict<SingleEnumSearchContext>>(*this, matchData)
            : std::make_unique<FilterAttributeIteratorT<SingleEnumSearchContext>>(*this, matchData);
    }
    return strict
        ? std::make_unique<AttributeIteratorStrict<SingleEnumSearchContext>>(*this, matchData)
        : std::make_unique<AttributeIteratorT<SingleEnumSearchContext>>(*this, matchData);
}

}

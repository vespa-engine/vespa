// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumhintsearchcontext.h"
#include "enumstore.h"
#include "string_search_context.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <vespa/vespalib/util/regexp.h>

namespace search::attribute {

template <typename Matcher>
StringSearchContextT<Matcher>::StringSearchContextT(const AttributeVector& to_be_searched, Matcher&& matcher)
    : SearchContext(to_be_searched), Matcher(std::move(matcher)) {
}

template <typename Matcher>
StringSearchContextT<Matcher>::StringSearchContextT(StringSearchContextT&&) noexcept = default;

template <typename Matcher>
StringSearchContextT<Matcher>::~StringSearchContextT() = default;

template <typename Matcher>
const QueryTermUCS4* StringSearchContextT<Matcher>::queryTerm() const {
    return this->get_query_term_ptr();
}

template <typename Matcher>
bool StringSearchContextT<Matcher>::valid() const {
    return Matcher::isValid();
}

template <typename Matcher>
void StringSearchContextT<Matcher>::set_and_setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store,
                                                               EnumHintSearchContext&         enum_hint_sc) {
    _plsc = &enum_hint_sc;
    this->setup_enum_hint_sc(enum_store, enum_hint_sc);
}

} // namespace search::attribute

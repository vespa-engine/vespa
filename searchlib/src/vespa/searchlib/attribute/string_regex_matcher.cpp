// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_regex_matcher.h"

#include "enumstore.h"
#include "i_dictionary_lookup.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/vespalib/util/regexp.h>

namespace search::attribute {

StringRegexMatcher::StringRegexMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased)
    : _query_term(static_cast<QueryTermUCS4*>(query_term.release())), _helper(*_query_term, cased) {
}

StringRegexMatcher::StringRegexMatcher(StringRegexMatcher&&) noexcept = default;

StringRegexMatcher::~StringRegexMatcher() = default;

bool StringRegexMatcher::lookup_in_dictionary(const EnumStoreT<const char*>& enum_store,
                                              IDictionaryLookup&             dict) const {
    if (is_valid()) {
        std::string prefix(vespalib::RegexpUtil::get_prefix(get_query_term_ptr()->getTerm()));
        auto        comp = enum_store.prefix_lookup_comparator(prefix.c_str());
        dict.lookupRange(comp, comp);
        return true;
    }

    return false;
}

bool StringRegexMatcher::use_dictionary_entry(const EnumStoreT<const char*>&  enum_store,
                                              EnumPostingTree::ConstIterator& it) const {
    if (_helper.get_regex().valid() &&
        _helper.get_regex().partial_match(enum_store.get_value(it.getKey().load_acquire())))
    {
        return true;
    }
    ++it;
    return false;
}

bool StringRegexMatcher::use_posting_lists_when_non_strict(const queryeval::ExecuteInfo& /*info*/,
                                                           uint32_t /*docid_limit*/) const {
    return false;
}

} // namespace search::attribute

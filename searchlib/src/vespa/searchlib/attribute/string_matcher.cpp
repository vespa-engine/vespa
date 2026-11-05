// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_matcher.h"

#include "enumhintsearchcontext.h"
#include "enumstore.h"
#include "i_dictionary_lookup.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <vespa/vespalib/util/regexp.h>

namespace search::attribute {

StringMatcher::StringMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased)
    : _query_term(static_cast<QueryTermUCS4*>(query_term.release())), _helper(*_query_term, cased) {
}

StringMatcher::StringMatcher(StringMatcher&&) noexcept = default;

StringMatcher::~StringMatcher() = default;

bool StringMatcher::lookup_in_dictionary(const EnumStoreT<const char*>& enum_store, IDictionaryLookup& dict) const {
    if (is_valid()) {
        if (_helper.is_prefix()) {
            auto comp = enum_store.prefix_lookup_comparator(get_query_term_ptr()->getTerm());
            dict.lookupRange(comp, comp);
        } else {
            auto comp = enum_store.string_lookup_comparator(get_query_term_ptr()->getTerm());
            dict.lookupTerm(comp);
        }
        return true;
    }

    return false;
}

bool StringMatcher::use_dictionary_entry(const EnumStoreT<const char*>&  enum_store,
                                         EnumPostingTree::ConstIterator& it) const {
    if (_helper.is_cased()) {
        if (match(enum_store.get_value(it.getKey().load_acquire()))) {
            return true;
        }
        ++it;
        return false;
    }
    return true;
}

bool StringMatcher::use_posting_lists_when_non_strict(const queryeval::ExecuteInfo& /*info*/,
                                                      uint32_t /*docid_limit*/) const {
    return false;
}

} // namespace search::attribute

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_search_helper.h"

#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>
#include <vespa/vespalib/util/hdr_abort.h>

namespace search {
class QueryTermSimple;
}

namespace search::attribute {

/*
 * Class used to determine if an attribute vector string value is a match for
 * the query string range.
 */
class StringRangeMatcher {
private:
    std::unique_ptr<QueryTermSimple> _query_term;

    std::string _low;
    std::string _high;

public:
    StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased,
                       vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm);
    StringRangeMatcher(StringRangeMatcher&&) noexcept;
    ~StringRangeMatcher();

protected:
    bool isValid() const;
    bool match(const char* src) const;
    bool isPrefix() const { HDR_ABORT("should not be reached"); }
    bool isRegex() const { HDR_ABORT("should not be reached"); }
    bool isCased() const { HDR_ABORT("should not be reached"); }
    bool isFuzzy() const { HDR_ABORT("should not be reached"); }
    const vespalib::Regex& getRegex() const { HDR_ABORT("should not be reached"); }
    const vespalib::FuzzyMatcher& getFuzzyMatcher() const { HDR_ABORT("should not be reached"); }
    const QueryTermUCS4* get_query_term_ptr() const noexcept { HDR_ABORT("should not be reached"); }

    template <typename DictionaryConstIteratorType>
    bool is_fuzzy_match(const char* /*word*/, DictionaryConstIteratorType& /*itr*/,
                        const DfaStringComparator::DataStoreType& /*data_store*/) const {
        HDR_ABORT("should not be reached");
    }
};

} // namespace search::attribute

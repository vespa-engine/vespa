// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <iosfwd>

namespace search {

enum class Normalizing : uint8_t {
    NONE,
    LOWERCASE,
    LOWERCASE_AND_FOLD
};

enum class TermType : uint8_t {
    WORD = 0,
    PREFIXTERM = 1,
    SUBSTRINGTERM = 2,
    EXACTSTRINGTERM = 3,
    SUFFIXTERM = 4,
    REGEXP = 5,
    GEO_LOCATION = 6,
    FUZZYTERM = 7,
    NEAREST_NEIGHBOR = 8
};

std::ostream &operator<<(std::ostream &, Normalizing);

/**
 * Resolves what kind of normalization that is needed for the query terms in context
 * of the fields searched. It also provides a utility method for doing the normalization.
 */
class QueryNormalization {
public:
    using Normalizing = search::Normalizing;
    virtual ~QueryNormalization() = default;
    virtual bool is_text_matching(std::string_view index) const noexcept = 0;
    virtual Normalizing normalizing_mode(std::string_view index) const noexcept = 0;
    static vespalib::string optional_fold(std::string_view s, TermType type, Normalizing normalizing);
};

}

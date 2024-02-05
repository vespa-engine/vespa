// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <iosfwd>

namespace search {

enum class Normalizing {
    NONE,
    LOWERCASE,
    LOWERCASE_AND_FOLD
};

std::ostream &operator<<(std::ostream &, Normalizing);

class QueryNormalization {
public:
    using Normalizing = search::Normalizing;
    virtual ~QueryNormalization() = default;
    virtual bool is_text_matching(vespalib::stringref index) const noexcept = 0;
    virtual Normalizing normalizing_mode(vespalib::stringref index) const noexcept = 0;
};

}

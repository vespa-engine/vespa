// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryterm.h"
#include <vespa/vespalib/regex/regex.h>

namespace search::streaming {

/**
 * Query term that matches fields using a regular expression, with case sensitivity
 * controlled by the provided Normalizing mode.
 */
class RegexpTerm : public QueryTerm {
    vespalib::Regex _regexp;
public:
    RegexpTerm(std::unique_ptr<QueryNodeResultBase> result_base, string_view term,
               const string& index, Type type, Normalizing normalizing);
    ~RegexpTerm() override;

    RegexpTerm* as_regexp_term() noexcept override { return this; }

    [[nodiscard]] const vespalib::Regex& regexp() const noexcept { return _regexp; }
};

}

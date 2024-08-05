// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "regexp_term.h"

namespace search::streaming {

using vespalib::Regex;

namespace {

constexpr Regex::Options normalize_mode_to_regex_opts(Normalizing norm) noexcept {
    return ((norm == Normalizing::NONE)
            ? Regex::Options::None
            : Regex::Options::IgnoreCase);
}

}

RegexpTerm::RegexpTerm(std::unique_ptr<QueryNodeResultBase> result_base, string_view term,
                       const string& index, Type type, Normalizing normalizing)
    : QueryTerm(std::move(result_base), term, index, type, normalizing),
      _regexp(Regex::from_pattern({term.data(), term.size()}, normalize_mode_to_regex_opts(normalizing)))
{
}

RegexpTerm::~RegexpTerm() = default;

}

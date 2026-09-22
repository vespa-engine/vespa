// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/string_range_spec.h>

#include <optional>

namespace search::query {

/**
 * The term of a StringRangeTerm query node, i.e., a lexical range over strings.
 */
using StringRange = std::optional<StringRangeSpec>;

} // namespace search::query

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/string_range_spec.h>

#include <memory>

namespace search::query {

/**
 * The term of a StringRangeTerm query node, i.e. a lexical range over strings.
 */
class StringRange {
    std::unique_ptr<StringRangeSpec> _spec;

public:
    StringRange() noexcept : _spec() {}
    explicit StringRange(std::unique_ptr<StringRangeSpec> spec) noexcept : _spec(std::move(spec)) {}
    StringRange(const StringRange& other);
    StringRange(StringRange&& other) noexcept = default;
    StringRange& operator=(const StringRange& other);
    StringRange& operator=(StringRange&& other) noexcept = default;
    ~StringRange();

    const StringRangeSpec* getSpec() const noexcept { return _spec.get(); }
};

bool operator==(const StringRange& r1, const StringRange& r2);

} // namespace search::query

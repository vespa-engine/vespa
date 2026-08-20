// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search {
struct StringRangeSpec;
}

namespace search::attribute {

/**
 * Helper class for StringRangeMatcher that implements the actual matching logic.
 * Separate class from StringRangeMatcher for unit testing.
 * It handles different search settings like prefix, regex and cased/uncased.
 */
class StringRangeSearchHelper {
private:
    const StringRangeSpec* _range_spec;
    bool                   _cased;

public:
    StringRangeSearchHelper(const StringRangeSpec* range_spec, bool cased);
    ~StringRangeSearchHelper();

    [[nodiscard]] bool is_valid() const noexcept { return _range_spec != nullptr; }
    [[nodiscard]] const StringRangeSpec* get_string_range_spec() const { return _range_spec; }
    [[nodiscard]] bool is_match(const char* src) const;

private:
    template <bool fold>
    [[nodiscard]] bool is_match_internal(const char* src) const;
};

} // namespace search::attribute

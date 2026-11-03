// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <string_view>

namespace search {
struct StringRangeSpec;
}

namespace search::attribute {

namespace detail {

template <typename T>
concept FoldableString = std::same_as<const char*, T> || std::same_as<std::string_view, T>;

}

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
    template <detail::FoldableString T>
    [[nodiscard]] bool is_match(T src) const;

private:
    template <detail::FoldableString T> static T string_as(const std::string& str);
    template <bool fold, detail::FoldableString T>
    [[nodiscard]] bool is_match_internal(T src) const;
};

template <> inline const char* StringRangeSearchHelper::string_as(const std::string& str) {
    return str.c_str();
}
template <> inline std::string_view StringRangeSearchHelper::string_as(const std::string& str) {
    return str;
}

} // namespace search::attribute

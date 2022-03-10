#pragma once

#include <string_view>
#include <utility>
#include <vector>
#include <optional>

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

class Fuzzy {
private:
    static constexpr uint8_t DefaultPrefixSize = 0u;
    static constexpr uint8_t DefaultEditDistance = 2u;

    std::vector<uint32_t> _folded_term_codepoints;

    uint8_t _prefix_size;  // prefix of a term that is considered frozen, i.e non-fuzzy
    uint8_t _edit_distance; // max edit distance


public:
    Fuzzy():
        _folded_term_codepoints(),
        _prefix_size(DefaultPrefixSize),
        _edit_distance(DefaultEditDistance)
    {}

    Fuzzy(std::vector<uint32_t> codepoints):
        _folded_term_codepoints(std::move(codepoints)),
        _prefix_size(DefaultPrefixSize),
        _edit_distance(DefaultEditDistance)
    {}

    Fuzzy(std::vector<uint32_t> codepoints, uint8_t prefix_size, uint8_t edit_distance):
        _folded_term_codepoints(std::move(codepoints)),
        _prefix_size(prefix_size),
        _edit_distance(edit_distance)
    {}

    [[nodiscard]] bool isMatch(std::string_view src) const;

    [[nodiscard]] vespalib::string getPrefix() const;

    ///

    static Fuzzy from_term(std::string_view term);

    static std::optional<uint32_t> levenstein_distance(const std::vector<uint32_t>& source, const std::vector<uint32_t>& target, uint32_t threshold);

    static std::optional<uint32_t> levenstein_distance(std::string_view source, std::string_view target, uint32_t threshold);

    static std::vector<uint32_t> folded_codepoints(const char* src, size_t srcSize);

    static std::vector<uint32_t> folded_codepoints(std::string_view src);

};

}

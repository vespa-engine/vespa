// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "levenshtein_dfa.h"
#include "unicode_utils.h"
#include <vector>

namespace vespalib::fuzzy {

template <typename Traits>
class ImplicitLevenshteinDfa final : public LevenshteinDfa::Impl {
    const std::vector<uint32_t> _u32_str_buf; // TODO std::u32string
    std::string                 _target_as_utf8;
    std::vector<uint32_t>       _target_utf8_char_offsets;
    const bool                  _is_cased;
public:
    using MatchResult = LevenshteinDfa::MatchResult;

    ImplicitLevenshteinDfa(std::vector<uint32_t> str, bool is_cased)
        : _u32_str_buf(std::move(str)),
          _target_as_utf8(),
          _target_utf8_char_offsets(),
          _is_cased(is_cased)
    {
        precompute_utf8_target_with_offsets();
    }

    ~ImplicitLevenshteinDfa() override = default;

    [[nodiscard]] MatchResult match(std::string_view u8str) const override;

    [[nodiscard]] MatchResult match(std::string_view u8str, std::string& successor_out) const override;

    [[nodiscard]] MatchResult match(std::string_view u8str, std::vector<uint32_t>& successor_out) const override;

    [[nodiscard]] size_t memory_usage() const noexcept override {
        return _u32_str_buf.size() * sizeof(uint32_t);
    }

    void dump_as_graphviz(std::ostream& os) const override;
private:
    void precompute_utf8_target_with_offsets();
};

}

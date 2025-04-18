// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace juniper::separators {

// Separators used in strings passed to juniper.

// UTF-8 encoded separarators
extern std::string interlinear_annotation_anchor_string;
extern std::string interlinear_annotation_separator_string;
extern std::string interlinear_annotation_terminator_string;
extern std::string group_separator_string;
extern std::string record_separator_string;
extern std::string unit_separator_string;

// UTF-32 separators
constexpr char32_t interlinear_annotation_anchor = U'\xfff9';
constexpr char32_t interlinear_annotation_separator = U'\xfffa';
constexpr char32_t interlinear_annotation_terminator = U'\xfffb';

// The GS character used to separate paragraphs
constexpr char8_t group_separator = u8'\x1d';

// The RS character
constexpr char8_t record_separator = u8'\x1e';

// The US character used to separate words in CJK texts
constexpr char8_t unit_separator = u8'\x1f';

} // namespace juniper::separators

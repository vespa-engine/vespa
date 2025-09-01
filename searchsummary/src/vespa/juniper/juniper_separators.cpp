// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniper_separators.h"
#include <vespa/vespalib/util/casts.h>

namespace juniper::separators {

using namespace vespalib;

std::string interlinear_annotation_anchor_string(u8"\uFFF9"_C);     // U+FFF9
std::string interlinear_annotation_separator_string(u8"\uFFFA"_C);  // U+FFFA
std::string interlinear_annotation_terminator_string(u8"\uFFFB"_C); // U+FFFB
std::string group_separator_string("\x1d");
std::string record_separator_string("\x1e");
std::string unit_separator_string("\x1f");

} // namespace juniper::separators

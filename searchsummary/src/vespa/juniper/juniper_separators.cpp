// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniper_separators.h"

namespace juniper::separators {

vespalib::string interlinear_annotation_anchor_string("\xef\xbf\xb9"); // U+FFF9
vespalib::string interlinear_annotation_separator_string("\xef\xbf\xba"); // U+FFFA
vespalib::string interlinear_annotation_terminator_string("\xef\xbf\xbb"); // U+FFFB
vespalib::string group_separator_string("\x1d");
vespalib::string record_separator_string("\x1e");
vespalib::string unit_separator_string("\x1f");

}

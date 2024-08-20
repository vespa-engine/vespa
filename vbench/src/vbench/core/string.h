// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>

namespace vbench {

using string = std::string;

extern string strfmt(const char *fmt, ...) __attribute__ ((format (printf,1,2)));

extern size_t splitstr(const string &str, const string &sep, std::vector<string> &dst);

} // namespace vbench


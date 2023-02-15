// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#define USE_VESPA_STRING 1

#if USE_VESPA_STRING
#include <vespa/vespalib/stllike/string.h>
#else
#include <string>
#endif

#include <vector>

namespace vbench {

// define which string class to use
#if USE_VESPA_STRING
using string = vespalib::string;
#else
using string = std::string;
#endif

extern string strfmt(const char *fmt, ...) __attribute__ ((format (printf,1,2)));

extern size_t splitstr(const string &str, const string &sep, std::vector<string> &dst);

} // namespace vbench


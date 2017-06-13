// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string.h"

#if USE_VESPA_STRING
#include <vespa/vespalib/util/vstringfmt.h>
#else
#include <vespa/vespalib/util/stringfmt.h>
#endif

namespace vbench {

string strfmt(const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
#if USE_VESPA_STRING
    string ret = vespalib::make_vespa_string_va(fmt, ap);
#else
    string ret = vespalib::make_string_va(fmt, ap);
#endif
    va_end(ap);
    return ret;
}

size_t splitstr(const string &str, const string &sep,
                std::vector<string> &dst)
{
    dst.clear();
    string tmp;
    for (size_t i = 0; i < str.size(); ++i) {
        if (sep.find(str[i]) != string::npos) {
            if (!tmp.empty()) {
                dst.push_back(tmp);
                tmp.clear();
            }
        } else {
            tmp.push_back(str[i]);
        }
    }
    if (!tmp.empty()) {
        dst.push_back(tmp);
    }
    return dst.size();
}

} // namespace vbench

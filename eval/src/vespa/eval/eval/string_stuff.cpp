// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_stuff.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib::eval {

vespalib::string as_quoted_string(const vespalib::string &str) {
    vespalib::string res;
    res.push_back('"');
    for (char c: str) {
        switch (c) {
        case '\\':
            res.append("\\\\");
            break;
        case '"':
            res.append("\\\"");
            break;
        case '\t':
            res.append("\\t");
            break;
        case '\n':
            res.append("\\n");
            break;
        case '\r':
            res.append("\\r");
            break;
        case '\f':
            res.append("\\f");
            break;
        default:
            if (static_cast<unsigned char>(c) >= 32 &&
                static_cast<unsigned char>(c) <= 126)
            {
                res.push_back(c);
            } else {
                const char *lookup = "0123456789abcdef";
                res.append("\\x");
                res.push_back(lookup[(c >> 4) & 0xf]);
                res.push_back(lookup[c & 0xf]);
            }
        }
    }
    res.push_back('"');
    return res;
}

bool is_number(const vespalib::string &str) {
    for (char c: str) {
        if (!isdigit(c)) {
            return false;
        }
    }
    return true;
}

size_t as_number(const vespalib::string &str) {
    return atoi(str.c_str());
}

vespalib::string as_string(const TensorSpec::Address &address) {
    CommaTracker label_list;
    vespalib::string str = "{";
    for (const auto &label: address) {
        label_list.maybe_add_comma(str);
        if (label.second.is_mapped()) {
            str += make_string("%s:%s", label.first.c_str(), as_quoted_string(label.second.name).c_str());
        } else {
            str += make_string("%s:%zu", label.first.c_str(), label.second.index);
        }
    }
    str += "}";
    return str;
}

}

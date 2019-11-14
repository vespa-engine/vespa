// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_stuff.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib::eval {

vespalib::string as_string(const TensorSpec::Address &address) {
    CommaTracker label_list;
    vespalib::string str = "{";
    for (const auto &label: address) {
        label_list.maybe_add_comma(str);
        if (label.second.is_mapped()) {
            str += make_string("%s:%s", label.first.c_str(), label.second.name.c_str());
        } else {
            str += make_string("%s:%zu", label.first.c_str(), label.second.index);
        }
    }
    str += "}";
    return str;
}

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "normalize_class_name.h"

namespace vespalib {

namespace {

void
normalize_class_name_helper(vespalib::string& class_name, const vespalib::string& old, const vespalib::string& replacement)
{
    for (;;) {
        auto pos = class_name.find(old);
        if (pos == vespalib::string::npos) {
            break;
        }
        class_name.replace(pos, old.size(), replacement);
    }
}

}

vespalib::string
normalize_class_name(vespalib::string class_name)
{
    normalize_class_name_helper(class_name, "long long", "long");
    normalize_class_name_helper(class_name, ">>", "> >");
    return class_name;
}

}

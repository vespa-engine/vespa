// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "normalize_class_name.h"

namespace vespalib {

namespace {

void
normalize_class_name_helper(std::string& class_name, const std::string& old, const std::string& replacement)
{
    for (;;) {
        auto pos = class_name.find(old);
        if (pos == std::string::npos) {
            break;
        }
        class_name.replace(pos, old.size(), replacement);
    }
}

}

std::string
normalize_class_name(std::string class_name)
{
    normalize_class_name_helper(class_name, "long long", "long");
    normalize_class_name_helper(class_name, ">>", "> >");
    return class_name;
}

}

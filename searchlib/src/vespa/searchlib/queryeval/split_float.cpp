// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "split_float.h"
#include <cctype>

namespace search::queryeval {

SplitFloat::SplitFloat(const vespalib::string &input)
{
    bool seenText = false;
    for (size_t i = 0; i < input.size(); ++i) {
        unsigned char c = input[i];
        if (isalnum(c)) {
            if (!seenText) {
                _parts.push_back(vespalib::string());
            }
            _parts.back().push_back(c);
            seenText = true;
        } else {
            seenText = false;
        }
    }
}

}


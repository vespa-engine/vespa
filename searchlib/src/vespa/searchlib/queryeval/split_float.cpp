// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "split_float.h"
#include <cctype>

namespace search::queryeval {

SplitFloat::SplitFloat(std::string_view input)
{
    bool seenText = false;
    for (unsigned char c : input) {
        if (isalnum(c)) {
            if (!seenText) {
                _parts.emplace_back();
            }
            _parts.back().push_back(c);
            seenText = true;
        } else {
            seenText = false;
        }
    }
}

}


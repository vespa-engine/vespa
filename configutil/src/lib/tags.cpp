// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tags.h"
#include <cctype>
#include <string>

namespace configdefinitions {

std::string upcase(const std::string &orig)
{
    std::string upper(orig);
    for (size_t i = 0; i < orig.size(); ++i) {
        int l = (unsigned char)orig[i];
        upper[i] = (unsigned char)std::toupper(l);
    }
    return upper;
}

bool tagsContain(const std::string &tags, const std::string &tag)
{
    std::string allupper = upcase(tags);
    std::string tagupper = upcase(tag);

    for (;;) {
        size_t pos = allupper.rfind(' ');
        if (pos == std::string::npos) {
            break;
        }
        if (allupper.substr(pos+1) == tagupper) {
            return true;
        }
        allupper.resize(pos);
    }
    return (allupper == tagupper);
}

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tags.h"
#include <vespa/vespalib/stllike/string.h>

namespace configdefinitions {

vespalib::string upcase(const vespalib::string &orig)
{
    vespalib::string upper(orig);
    for (size_t i = 0; i < orig.size(); ++i) {
        int l = (unsigned char)orig[i];
        upper[i] = (unsigned char)toupper(l);
    }
    return upper;
}

bool tagsContain(const vespalib::string &tags, const vespalib::string &tag)
{
    vespalib::string allupper = upcase(tags);
    vespalib::string tagupper = upcase(tag);

    for (;;) {
        size_t pos = allupper.rfind(' ');
        if (pos == vespalib::string::npos) {
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

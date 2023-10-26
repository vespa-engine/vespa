// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
    class GenericHeader;
}

namespace search::common {

class FileHeaderContext
{
public:
    FileHeaderContext();
    virtual ~FileHeaderContext();

    virtual void addTags(vespalib::GenericHeader &header, const vespalib::string &name) const = 0;

    static void addCreateAndFreezeTime(vespalib::GenericHeader &header);
    static void setFreezeTime(vespalib::GenericHeader &header);
};

}

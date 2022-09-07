// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/time.h>

namespace search {

class FileKit
{
public:
    static bool createStamp(const vespalib::string &name);
    static bool hasStamp(const vespalib::string &name);
    static bool removeStamp(const vespalib::string &name);

    /**
     * Returns the modification time of the given file/directory,
     * or time stamp 0 if stating of file/directory fails.
     **/
    static vespalib::system_time getModificationTime(const vespalib::string &name);
};

}

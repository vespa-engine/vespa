// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/time.h>
#include <string>

namespace search {

class FileKit
{
public:
    /**
     * Returns the modification time of the given file/directory,
     * or time stamp 0 if stating of file/directory fails.
     **/
    static vespalib::system_time getModificationTime(const std::string &name);
};

}

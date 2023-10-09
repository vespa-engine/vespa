// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filekit.h"
#include <vespa/vespalib/util/error.h>
#include <vespa/fastos/file.h>

#include <vespa/log/log.h>
LOG_SETUP(".filekit");

namespace search {

vespalib::system_time
FileKit::getModificationTime(const vespalib::string &name)
{
    FastOS_StatInfo statInfo;
    if (FastOS_File::Stat(name.c_str(), &statInfo)) {
        return statInfo._modifiedTime;
    }
    return vespalib::system_time();
}


}

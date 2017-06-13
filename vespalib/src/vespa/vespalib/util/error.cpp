// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/error.h>

namespace vespalib {

vespalib::string
getErrorString(const int osError)
{
    char errorBuf[128];
    return strerror_r(osError, errorBuf, sizeof(errorBuf));
}

vespalib::string
getLastErrorString()
{
    return getErrorString(errno);
}

}

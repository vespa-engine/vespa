// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/error.h>
#include <system_error>

namespace vespalib {

std::string
getErrorString(const int osError)
{
    std::error_code ec(osError, std::system_category());
    return ec.message();
}

std::string
getLastErrorString()
{
    return getErrorString(errno);
}

}

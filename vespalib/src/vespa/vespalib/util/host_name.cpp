// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "host_name.h"
#include <vespa/vespalib/util/exceptions.h>
#include <unistd.h>

namespace vespalib {

namespace {

vespalib::string make_host_name() {
    constexpr size_t max_size = 1024;
    char hostname[max_size + 1];
    memset(hostname, 0, sizeof(hostname));
    if (gethostname(hostname, max_size) != 0) {
        throw FatalException("gethostname failed");
    }
    return hostname;
}

} // namespace vespalib::<unnamed>

const vespalib::string HostName::_host_name = make_host_name();

} // namespace vespalib

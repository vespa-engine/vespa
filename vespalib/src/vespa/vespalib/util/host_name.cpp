// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "host_name.h"
#include <vespa/defaults.h>

namespace vespalib {

namespace {

vespalib::string make_host_name() {
    return vespa::Defaults::vespaHostname();
}

} // namespace vespalib::<unnamed>

const vespalib::string HostName::_host_name = make_host_name();

} // namespace vespalib
